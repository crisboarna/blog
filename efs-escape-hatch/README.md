# Welcome to your CDK Java project!

This is a blank project for CDK development with Java.

The `cdk.json` file tells the CDK Toolkit how to execute your app.

It is a [Maven](https://maven.apache.org/) based project, so you can open this project with any Maven compatible Java IDE to build and run tests.

## Useful commands

 * `mvn package`     compile and run tests
 * `cdk ls`          list all stacks in the app
 * `cdk synth`       emits the synthesized CloudFormation template
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk docs`        open CDK documentation

Individually trigger stacks to deploy:

 * `npx cdk deploy EngineerMindscape-VPC-DEMO`
 * `npx cdk deploy EngineerMindscape-EFS-DEMO`
 * `npx cdk deploy EngineerMindscape-ECS-DEMO`
 * `npx cdk deploy EngineerMindscape-ECS-EFS-Util-DEMO`

There are also shorter utility scripts to deploy the stacks individually:

 * `npm run deploy:vpc`
 * `npm run deploy:ecs`
 * `npm run deploy:efs`
 * `npm run deploy:efs-util`

And to connect to the ECS cluster EFS util task:

 * `npm run shell:efs:util`

## Teardown

 * `npx cdk destroy --all`
or
 * `npm run destroy`