data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "server" {
  for_each = toset(["backend", "mysql"])

  name               = "${var.name_prefix}-${each.key}-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_instance_profile" "server" {
  for_each = aws_iam_role.server

  name = each.value.name
  role = each.value.name
}

resource "aws_iam_role_policy_attachment" "ssm" {
  for_each = aws_iam_role.server

  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "backend" {
  name = "${var.name_prefix}-backend-runtime"
  role = aws_iam_role.server["backend"].id

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
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage"
        ]
        Resource = aws_ecr_repository.backend.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetBucketLocation"]
        Resource = data.aws_s3_bucket.images.arn
      },
      {
        Effect = "Allow"
        Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = [
          "${data.aws_s3_bucket.images.arn}/posts/*",
          "${data.aws_s3_bucket.images.arn}/profiles/*"
        ]
      }
    ]
  })
}
