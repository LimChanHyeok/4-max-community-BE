locals {
  parameter_names = {
    backend = {
      db_password = "${var.parameter_prefix}/db/password"
      jwt_secret  = "${var.parameter_prefix}/backend/jwt-secret"
    }
    mysql = {
      db_password   = "${var.parameter_prefix}/db/password"
      root_password = "${var.parameter_prefix}/mysql/root-password"
    }
  }
}

resource "aws_iam_role_policy" "parameter_store" {
  for_each = local.parameter_names

  name = "${var.name_prefix}-${each.key}-parameters"
  role = aws_iam_role.server[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = [
          for name in values(each.value) :
          "arn:aws:ssm:${var.aws_region}:${var.aws_account_id}:parameter${name}"
        ]
      },
      {
        Sid    = "RestrictParameterReadsGrantedBySSMCore"
        Effect = "Deny"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath",
          "ssm:GetParameterHistory"
        ]
        NotResource = [
          for name in values(each.value) :
          "arn:aws:ssm:${var.aws_region}:${var.aws_account_id}:parameter${name}"
        ]
      }
    ]
  })
}

output "parameter_names" {
  value = local.parameter_names
}
