# Engineering Mindscape 
## ECS Task Definition Limitations Workaround

## How to run
1. Clone the repository
2. Run the following command
```bash
terraform init && terraform apply -auto-approve
```
3. Once the resources are created, you can verify the ECS task definition limitations workaround works and the hystrix.properties file is correctly placed in the file system
by running the following command
```bash 
aws logs tail /aws/ecs/EngineeringMindscape-Task-Limits-DEV/example
```
4. To clean up the resources, run the following command
```bash
terraform destroy -auto-approve
```