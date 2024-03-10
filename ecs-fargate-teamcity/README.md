# Building a TeamCity CI/CD Server on AWS ECS Fargate with On-Demand Fargate Agents

## Introduction

This repository exhibits the code and configuration for building a TeamCity CI/CD server on AWS ECS Fargate with
On-Demand Fargate Agents. The repository is a part of the blog
post [Building a TeamCity CI/CD Server on AWS ECS Fargate with On-Demand Fargate Agents](https://www.linkedin.com/pulse/building-teamcity-cicd-server-aws-ecs-fargate-on-demand-rajesh-kumar/).

## Requirements

- [AWS CLI](https://aws.amazon.com/cli/)
- [AWS CDK](https://docs.aws.amazon.com/cdk/latest/guide/getting_started.html)
- [Node.js](https://nodejs.org/en/download/)
- [NPM](https://www.npmjs.com/get-npm)
- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
- [Maven](https://maven.apache.org/download.cgi)

It is a [Maven](https://maven.apache.org/) based project, so you can open this project with any Maven compatible Java
IDE to build and run tests.

## Useful commands

* `mvn package`     compile and run tests
* `cdk ls`          list all stacks in the app
* `cdk synth`       emits the synthesized CloudFormation template
* `cdk deploy`      deploy this stack to your default AWS account/region
* `cdk diff`        compare deployed stack with current state
* `cdk docs`        open CDK documentation

To deploy the stacks individually you can run one of the following commands:

1. ECR - Elastic Container Registry

```bash
npx cdk deploy SmartLink-Repositories-CI
```

2. S3 - Simple Storage Service

```bash
npx cdk deploy SmartLink-S3-CI
```

3. CodeArtifact

```bash
npx cdk deploy SmartLink-S3-CI
```

4. VPC - Virtual Private Cloud

```bash
npx cdk deploy SmartLink-VPC-CI
```

5. EFS - Elastic File System

```bash
npx cdk deploy SmartLink-EFS-CI
```

6. ECS - Elastic Container Service

```bash
npx cdk deploy SmartLink-ECS-CI
```

7. ECS Services - TeamCity MySQL

```bash
npx cdk deploy SmartLink-ECS-Services-TeamCity-MySQL-CI
```

8. ECS Services - TeamCity Server

```bash
npx cdk deploy SmartLink-ECS-Services-TeamCity-Server-CI
```

9. ECS Services - TeamCity Agent

```bash
npx cdk deploy SmartLink-ECS-Services-TeamCity-Agent-CI
```