locals {
  github_oidc_host = "token.actions.githubusercontent.com"
  search_ci_subjects = [
    "repo:LimChanHyeok/4-max-community-BE:ref:refs/heads/search-enhancement",
    # PR #72의 머지 전 EC2 검증에만 사용한다.
    "repo:LimChanHyeok/4-max-community-BE:ref:refs/heads/feature/69-search-environment",
  ]
}

data "aws_iam_openid_connect_provider" "github_actions" {
  url = "https://${local.github_oidc_host}"
}

data "aws_iam_policy_document" "search_ci_assume" {
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
      values   = local.search_ci_subjects
    }
  }
}

resource "aws_iam_role" "search_ci_ecr_push" {
  name               = "${var.name_prefix}-ci-ecr-push"
  assume_role_policy = data.aws_iam_policy_document.search_ci_assume.json
}

resource "aws_iam_role_policy" "search_ci_ecr_push" {
  name = "publish-search-backend"
  role = aws_iam_role.search_ci_ecr_push.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
        ]
        Resource = aws_ecr_repository.backend.arn
      },
    ]
  })
}
