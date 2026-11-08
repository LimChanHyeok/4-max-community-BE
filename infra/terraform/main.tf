data "aws_ami" "ubuntu" {
  owners = ["099720109477"]

  filter {
    name   = "image-id"
    values = [var.ubuntu_ami_id]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
}

data "aws_s3_bucket" "images" {
  bucket = var.image_bucket_name
}

resource "aws_vpc" "search" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = var.name_prefix }
}

resource "aws_internet_gateway" "search" {
  vpc_id = aws_vpc.search.id

  tags = { Name = var.name_prefix }
}

resource "aws_subnet" "public" {
  vpc_id            = aws_vpc.search.id
  cidr_block        = var.subnet_cidr
  availability_zone = var.availability_zone

  tags = { Name = "${var.name_prefix}-public" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.search.id

  tags = { Name = "${var.name_prefix}-public" }
}

resource "aws_route" "internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.search.id
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "backend" {
  name        = "${var.name_prefix}-backend"
  description = "Public HTTP access through Nginx"
  vpc_id      = aws_vpc.search.id
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  for_each = var.http_allowed_cidrs

  security_group_id = aws_security_group.backend.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  for_each = var.http_allowed_cidrs

  security_group_id = aws_security_group.backend.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_security_group" "mysql" {
  name        = "${var.name_prefix}-mysql"
  description = "MySQL access from the backend private network interface"
  vpc_id      = aws_vpc.search.id
}

resource "aws_vpc_security_group_ingress_rule" "mysql" {
  security_group_id            = aws_security_group.mysql.id
  referenced_security_group_id = aws_security_group.backend.id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}

resource "aws_vpc_security_group_egress_rule" "outbound" {
  for_each = {
    backend = aws_security_group.backend.id
    mysql   = aws_security_group.mysql.id
  }

  security_group_id = each.value
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_ebs_volume" "mysql_data" {
  availability_zone = var.availability_zone
  type              = "gp3"
  size              = var.mysql_data_volume_size
  encrypted         = true

  lifecycle {
    prevent_destroy = true
  }

  tags = { Name = "${var.name_prefix}-mysql-data" }
}

resource "aws_instance" "backend" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.backend_instance_type
  subnet_id                   = aws_subnet.public.id
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.backend.id]
  iam_instance_profile        = aws_iam_instance_profile.server["backend"].name

  user_data = join("\n", [
    file("${path.module}/scripts/bootstrap.sh"),
    templatefile("${path.module}/scripts/prepare-secrets.sh.tftpl", {
      settings_base64 = base64encode(jsonencode({
        role      = "backend"
        region    = var.aws_region
        directory = "/var/lib/community-search/backend"
        group_id  = 10001
        file_mode = "0640"
        files = {
          db-password = local.parameter_names.backend.db_password
          jwt-secret  = local.parameter_names.backend.jwt_secret
        }
      }))
    })
  ])
  user_data_replace_on_change = true

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size
    encrypted   = true
  }

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  depends_on = [
    aws_route.internet,
    aws_route_table_association.public,
    aws_iam_role_policy_attachment.ssm,
    aws_iam_role_policy.parameter_store
  ]

  tags = { Name = "${var.name_prefix}-backend" }
}

resource "aws_eip" "backend" {
  domain   = "vpc"
  instance = aws_instance.backend.id

  depends_on = [aws_internet_gateway.search]

  tags = { Name = "${var.name_prefix}-backend" }
}

resource "aws_instance" "mysql" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.mysql_instance_type
  subnet_id                   = aws_subnet.public.id
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.mysql.id]
  iam_instance_profile        = aws_iam_instance_profile.server["mysql"].name

  user_data = join("\n", [
    file("${path.module}/scripts/bootstrap.sh"),
    templatefile("${path.module}/scripts/mount-mysql.sh.tftpl", {
      volume_serial = replace(aws_ebs_volume.mysql_data.id, "-", "")
    }),
    templatefile("${path.module}/scripts/prepare-secrets.sh.tftpl", {
      settings_base64 = base64encode(jsonencode({
        role      = "mysql"
        region    = var.aws_region
        directory = "/var/lib/community-search/mysql"
        group_id  = 0
        file_mode = "0600"
        files = {
          db-password   = local.parameter_names.mysql.db_password
          root-password = local.parameter_names.mysql.root_password
        }
      }))
    })
  ])
  user_data_replace_on_change = true

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size
    encrypted   = true
  }

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  depends_on = [
    aws_route.internet,
    aws_route_table_association.public,
    aws_iam_role_policy_attachment.ssm,
    aws_iam_role_policy.parameter_store
  ]

  tags = { Name = "${var.name_prefix}-mysql" }
}

resource "aws_volume_attachment" "mysql_data" {
  device_name                    = "/dev/sdf"
  volume_id                      = aws_ebs_volume.mysql_data.id
  instance_id                    = aws_instance.mysql.id
  stop_instance_before_detaching = true
}

resource "aws_ecr_repository" "backend" {
  name                 = "${var.name_prefix}-backend"
  image_tag_mutability = "IMMUTABLE"
}
