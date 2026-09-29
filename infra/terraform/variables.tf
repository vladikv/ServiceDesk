variable "aws_region" {
  description = "AWS region where the learning environment will be created."
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type. Check current AWS pricing and account eligibility before deployment."
  type        = string
  default     = "t3.micro"
}

variable "project_name" {
  description = "Name prefix used for AWS resources."
  type        = string
  default     = "servicedesk"
}
