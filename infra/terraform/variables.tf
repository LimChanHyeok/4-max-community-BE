variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "aws_account_id" {
  type    = string
  default = "929368845727"
}

variable "name_prefix" {
  type    = string
  default = "community-search"
}

variable "availability_zone" {
  type    = string
  default = "ap-northeast-2a"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "subnet_cidr" {
  type    = string
  default = "10.20.1.0/24"
}

variable "ubuntu_ami_id" {
  description = "Canonical Ubuntu Server 24.04 amd64 AMI ID, fixed for reproducibility."
  type        = string
}

variable "http_allowed_cidrs" {
  description = "Nginx HTTP와 HTTPS 접속을 허용할 IPv4 CIDR 목록."
  type        = set(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition = (
      length(var.http_allowed_cidrs) > 0 &&
      alltrue([for cidr in var.http_allowed_cidrs : can(cidrnetmask(cidr))])
    )
    error_message = "유효한 IPv4 CIDR을 하나 이상 지정하세요."
  }
}

variable "backend_instance_type" {
  type    = string
  default = "t3a.small"
}

variable "mysql_instance_type" {
  type    = string
  default = "t3a.medium"
}

variable "monitoring_instance_type" {
  type    = string
  default = "t3a.micro"
}

variable "root_volume_size" {
  type    = number
  default = 20
}

variable "mysql_data_volume_size" {
  type    = number
  default = 20
}

variable "image_bucket_name" {
  type    = string
  default = "community-board-images-prod"
}

variable "parameter_prefix" {
  description = "Parameter Store prefix for search runtime secrets."
  type        = string
  default     = "/community/search"
}
