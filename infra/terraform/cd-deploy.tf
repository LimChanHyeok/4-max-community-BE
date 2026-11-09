locals {
  search_cd_repositories = {
    backend = {
      subject       = "repo:LimChanHyeok/4-max-community-BE:ref:refs/heads/search-enhancement"
      role_name     = "${var.name_prefix}-cd-deploy"
      document_name = "${var.name_prefix}-deploy"
    }
    frontend = {
      subject       = "repo:100-hours-a-week/4-max-community-FE:ref:refs/heads/main"
      role_name     = "${var.name_prefix}-frontend-deploy"
      document_name = "${var.name_prefix}-frontend-deploy"
    }
  }
}

data "aws_iam_policy_document" "search_cd_assume" {
  for_each = local.search_cd_repositories

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:sub"
      values   = [each.value.subject]
    }
  }
}

resource "aws_iam_role" "search_cd_deploy" {
  for_each = local.search_cd_repositories

  name               = each.value.role_name
  assume_role_policy = data.aws_iam_policy_document.search_cd_assume[each.key].json
}

resource "aws_iam_role_policy" "search_cd_deploy" {
  for_each = local.search_cd_repositories

  name = "deploy-search-${each.key}"
  role = aws_iam_role.search_cd_deploy[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(each.key == "backend" ? [
      {
        Effect   = "Allow"
        Action   = ["ecr:DescribeImages"]
        Resource = aws_ecr_repository.backend.arn
      },
      ] : [], [
      {
        Effect = "Allow"
        Action = ["ssm:SendCommand"]
        Resource = [
          aws_ssm_document.search_deploy[each.key].arn,
          aws_instance.backend.arn,
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "ec2:DescribeInstances",
          "ssm:DescribeInstanceInformation",
          "ssm:GetCommandInvocation",
        ]
        Resource = "*"
      },
    ])
  })
}

resource "aws_ssm_document" "search_deploy" {
  for_each = local.search_cd_repositories

  name          = each.value.document_name
  document_type = "Command"

  content = jsonencode({
    schemaVersion = "2.2"
    description   = "Deploy the selected ${each.key} version to the search environment."
    parameters = each.key == "backend" ? {
      ImageDigest = {
        type              = "String"
        interpolationType = "ENV_VAR"
        allowedPattern    = "^sha256:[0-9a-f]{64}$"
      }
      } : {
      FrontendSha = {
        type              = "String"
        interpolationType = "ENV_VAR"
        allowedPattern    = "^[0-9a-f]{40}$"
      }
    }
    mainSteps = [{
      action = "aws:runShellScript"
      name   = "deploy"
      inputs = {
        timeoutSeconds = "900"
        runCommand = [
          "#!/bin/bash",
          "export AWS_REGION='${var.aws_region}'",
          "export SEARCH_IMAGE_REPOSITORY='${aws_ecr_repository.backend.repository_url}'",
          "export SEARCH_PUBLIC_IP='${aws_eip.backend.public_ip}'",
          file("${path.module}/../../deploy/search/apply-release.sh"),
        ]
      }
    }]
  })
}
