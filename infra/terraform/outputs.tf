output "backend" {
  value = {
    instance_id = aws_instance.backend.id
    public_ip   = aws_instance.backend.public_ip
    private_ip  = aws_instance.backend.private_ip
    api_url     = "http://${aws_instance.backend.public_ip}"
  }
}

output "mysql" {
  value = {
    instance_id    = aws_instance.mysql.id
    private_ip     = aws_instance.mysql.private_ip
    data_volume    = aws_ebs_volume.mysql_data.id
    data_directory = "/srv/mysql"
  }
}

output "ecr_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "ssm_commands" {
  value = {
    backend = "aws ssm start-session --region ${var.aws_region} --target ${aws_instance.backend.id}"
    mysql   = "aws ssm start-session --region ${var.aws_region} --target ${aws_instance.mysql.id}"
  }
}
