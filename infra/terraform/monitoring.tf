resource "aws_security_group" "monitoring" {
  name        = "${var.name_prefix}-monitoring"
  description = "Private monitoring intake from search servers"
  vpc_id      = aws_vpc.search.id
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_intake" {
  for_each = {
    backend_metrics = { source = aws_security_group.backend.id, port = 9090 }
    backend_logs    = { source = aws_security_group.backend.id, port = 3100 }
    mysql_metrics   = { source = aws_security_group.mysql.id, port = 9090 }
    mysql_logs      = { source = aws_security_group.mysql.id, port = 3100 }
  }

  security_group_id            = aws_security_group.monitoring.id
  referenced_security_group_id = each.value.source
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
}

resource "aws_vpc_security_group_egress_rule" "monitoring_outbound" {
  security_group_id = aws_security_group.monitoring.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_iam_role_policy" "monitoring_parameter_store" {
  name = "${var.name_prefix}-monitoring-deny-parameters"
  role = aws_iam_role.server["monitoring"].id

  # SSM 관리형 정책에 포함된 Parameter Store 읽기 권한은 모니터링 서버에 필요하지 않다.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Deny"
      Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"]
      Resource = "*"
    }]
  })
}

resource "aws_instance" "monitoring" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.monitoring_instance_type
  subnet_id                   = aws_subnet.public.id
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.monitoring.id]
  iam_instance_profile        = aws_iam_instance_profile.server["monitoring"].name

  user_data = file("${path.module}/scripts/bootstrap.sh")

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size
    encrypted   = true
  }

  metadata_options {
    http_tokens = "required"
  }

  depends_on = [
    aws_route.internet,
    aws_route_table_association.public,
    aws_iam_role_policy_attachment.ssm,
    aws_iam_role_policy.monitoring_parameter_store
  ]

  tags = { Name = "${var.name_prefix}-monitoring" }
}

output "monitoring" {
  value = {
    instance_id = aws_instance.monitoring.id
    private_ip  = aws_instance.monitoring.private_ip
    grafana_forward = format(
      "aws ssm start-session --region %s --target %s --document-name AWS-StartPortForwardingSession --parameters '%s'",
      var.aws_region,
      aws_instance.monitoring.id,
      jsonencode({ portNumber = ["3000"], localPortNumber = ["13000"] }),
    )
  }
}
