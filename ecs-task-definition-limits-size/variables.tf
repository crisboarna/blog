variable "project_name" {
  description = "The name of the project"
  type        = string
}

variable "env" {
  description = "The environment (e.g., 'DEV', 'QA', 'PRD')"
  type        = string
}

variable "region" {
  description = "The region"
  type        = string
  default     = "eu-west-1"
}