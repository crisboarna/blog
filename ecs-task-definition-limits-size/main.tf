module "vpc" {
  source = "terraform-aws-modules/vpc/aws"
  version = "5.5.1"

  name                = "${var.project_name}-vpc-${var.env}"
  cidr                = "10.0.0.0/23"
  azs                 = ["eu-west-1a"]
  public_subnets      = ["10.0.0.0/23"]
}

resource "aws_ecr_repository" "repository" {
  name                 = "${lower(var.project_name)}-task-limits-${lower(var.env)}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true
  image_scanning_configuration {
    scan_on_push = true
  }
}

// lifecycle policy to expire untagged images so we can keep the ECR repository clean
// and be able to delete it without any issues using terraform destroy
resource "aws_ecr_lifecycle_policy" "lifecycle_policy" {
  repository = aws_ecr_repository.repository.name
  policy     = <<EOF
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Expire untagged images",
      "selection": {
        "tagStatus": "untagged",
        "countType": "imageCountMoreThan",
        "countNumber": 1
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
EOF
}

resource "null_resource" "build_and_push_image" {
  // build and push the image to the ECR repository during terraform apply to reduce example complexity
  provisioner "local-exec" {
    command = <<EOF
      $(aws ecr get-login-password --region ${var.region} | docker login --username AWS --password-stdin ${aws_ecr_repository.repository.repository_url})
      docker build --platform linux/amd64 -t ${aws_ecr_repository.repository.repository_url} .
      docker push ${aws_ecr_repository.repository.repository_url}
    EOF
  }
  // ensure the ECR repository is created before building and pushing the image
  depends_on = [aws_ecr_repository.repository]
}

module "ecs_cluster" {
  source  = "terraform-aws-modules/ecs/aws//modules/cluster"
  version = "5.8.0"

  cluster_name        = "${var.project_name}-ECS-${var.env}"
}

resource "null_resource" "zip_file" {
  provisioner "local-exec" {
    command = "zip hystrix.zip hystrix.properties"
  }
}

data "local_file" "zip_file" {
  depends_on = [null_resource.zip_file]
  filename = "${path.module}/hystrix.zip"
}

resource "aws_ssm_parameter" "ssm_parameter" {
  name        = "/${lower(var.project_name)}/example/zip/hystrix/properties/${lower(var.env)}"
  type        = "String"
  value       = data.local_file.zip_file.content_base64
  description = "${var.project_name} ECS Example Parameter"
  tier        = "Standard"
  overwrite   = true
  depends_on = [data.local_file.zip_file]
}

module "ecs_service" {
  source = "terraform-aws-modules/ecs/aws//modules/service"
  version = "5.8.0"

  name                               = "${var.project_name}-Task-Limits-${var.env}"
  family                             = "${var.project_name}-Task-Limits-${var.env}"
  cluster_arn                        = module.ecs_cluster.arn
  subnet_ids                         = module.vpc.public_subnets
  assign_public_ip                   = true
  cpu                                = 256
  memory                             = 512
  desired_count                      = 1
  tasks_iam_role_statements = [
    {
      effect    = "Allow",
      actions   = ["ssm:GetParameters"],
      resources = [
        aws_ssm_parameter.ssm_parameter.arn
      ],
    }
  ]
  container_definitions = {
    example = {
      image                     = "${aws_ecr_repository.repository.repository_url}:latest",
      essential                 = true
      readonly_root_filesystem  = false
      // run the entrypoint first to convert encoded environment variables to files.
      entrypoint                = ["/usr/local/bin/docker-entrypoint.sh"],
      // the command that will be executed once the entrypoint has been executed
      command                   = ["/bin/sh", "-c", "while true; do cat /tmp/hystrix.properties; sleep 1; done"]
      // environment variable used in entrypoint to decide where to place output files
      environment               = [
        {
          name                  = "CONFIG_DIR"
          value                 = "/tmp"
        }
      ]
      // encoded files to be placed as environment variables from ssm and secrets manager
      secrets                   = [
        {
          name                  = "ZIP_HYSTRIX_PROPERTIES"
          valueFrom             = aws_ssm_parameter.ssm_parameter.name
        }
      ]
      health_check              = {
        command                 = ["CMD-SHELL", "exit 0"]
        interval                = 30
        retries                 = 3
        timeout                 = 5
      }
    }
  }
  security_group_rules = {
    egress_all = {
      type        = "egress"
      from_port   = 0
      to_port     = 0
      protocol    = "-1"
      cidr_blocks = ["0.0.0.0/0"]
    }
  }
  // ensure the image is built and pushed before creating the service
  depends_on = [null_resource.build_and_push_image]
}