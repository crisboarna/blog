output "ecs_cluster_name" {
  value = module.ecs_cluster.name
}

output "ecs_service_name" {
  value = module.ecs_service.name
}

output "ecs_log_group" {
  value = module.ecs_service.container_definitions.example.cloudwatch_log_group_name
}