#!/bin/bash

set -e

# environment is first argument passed to script
ENV=$1

# service is second argument passed to script
SERVICE=$2

# container name is third argument passed to script
CONTAINER_NAME="${3:-$(echo $SERVICE | tr 'A-Z-' 'a-z_')}"

# region for the service
REGION=${4:-"eu-west-1"}

usage_string="Usage: ./ecs-exec.sh <env> <service> [<container_name>:lower(service)] [<region>:eu-west-1]"

# check if ENV is passed
if [ -z "$ENV" ]; then
  echo "environment not passed in."
  echo $usage_string
  exit 1
fi

# check if SERVICE is passed
if [ -z "$SERVICE" ]; then
  echo "service target is not passed in."
  echo $usage_string
  exit 1
fi

# Check if required commands are available
for cmd in aws; do
  if ! command -v "$cmd" > /dev/null; then
    echo "Error: $cmd is not installed." >&2
    exit 1
  fi
done

CLUSTER_NAME="EngineerMindscape-ECS-$ENV"
SERVICE_NAME="EngineerMindscape-$SERVICE-$ENV"

echo "Cluster Name: $CLUSTER_NAME"
echo "Service Name: $SERVICE_NAME"
echo "Container Name: $CONTAINER_NAME"

TASK_ARN=$(aws ecs list-tasks --region $REGION --cluster $CLUSTER_NAME --service-name $SERVICE_NAME --query 'taskArns[0]' --output text --no-cli-pager)
echo "Task ARN: $TASK_ARN"

aws ecs execute-command --region $REGION --cluster $CLUSTER_NAME --task $TASK_ARN --container $CONTAINER_NAME --command '/bin/sh' --interactive