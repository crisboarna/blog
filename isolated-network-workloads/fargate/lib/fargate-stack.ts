import * as cdk from 'aws-cdk-lib';
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecr from "aws-cdk-lib/aws-ecr";
import * as logs from "aws-cdk-lib/aws-logs";
import * as iam from "aws-cdk-lib/aws-iam";
import * as ecs from "aws-cdk-lib/aws-ecs";
import { Construct } from 'constructs';

interface FargateStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
  logsVpcEndpointSG: ec2.ISecurityGroup;
  ecrVpcEndpointSG: ec2.ISecurityGroup;
  ecrDockerVpcEndpoint: ec2.ISecurityGroup;
  workloadEcrRepo: ecr.Repository;
}

export class FargateStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: FargateStackProps) {
    super(scope, id, props);
    const {
      vpc,
      logsVpcEndpointSG,
      ecrVpcEndpointSG,
      ecrDockerVpcEndpoint,
      workloadEcrRepo
    } = props;

    // create log group for ECS exec audit logs
    const logGroup = new logs.LogGroup(
        this,
        'LogGroup',
        {
          logGroupName: '/ecs/fargate/service',
          removalPolicy: cdk.RemovalPolicy.DESTROY,
          retention: logs.RetentionDays.ONE_WEEK,
        }
    );

    // define role for session manager to be used by ECS task
    const ecsRole = new iam.Role(
        this,
        'ECSRole',
        {
          assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com')
        });

    // grant session manager role access to log group
    logGroup.grantWrite(ecsRole);

    // define task definition for workload
    const workloadTaskDefinition =
        new ecs.FargateTaskDefinition(
            this,
            'WorkloadTaskDefinition',
            {
              family: 'Workload',
              cpu: 256,
              memoryLimitMiB: 512,
              taskRole: ecsRole,
              executionRole: ecsRole,
            }
        );

    // define container for workload
    const workloadContainer =
        workloadTaskDefinition.addContainer('WorkloadContainer', {
          image: ecs.ContainerImage.fromEcrRepository(workloadEcrRepo),
          // ensure container stays alive
          command: ["/bin/sh", "-c", "while true; do echo 'Hello'; sleep 10; done"],
          healthCheck: {
            // ECS will kill the container if it fails to start within 30 seconds
            command: ['CMD-SHELL', 'exit', '0'],
          },
          logging: ecs.LogDrivers.awsLogs({logGroup: logGroup, streamPrefix: 'ingress'}),
        });

    // define security group for workload
    const workloadSecurityGroup =
        new ec2.SecurityGroup(
            this,
            'SecurityGroupWorkload',
            {
              vpc: vpc,
              allowAllOutbound: true
            }
        );

    [
      // needed for logs to reach CloudWatch
      logsVpcEndpointSG,
      // needed for workload to pull image from ECR
      ecrVpcEndpointSG,
      ecrDockerVpcEndpoint
    ].forEach(
        (sg) => {
          workloadSecurityGroup.connections.allowFrom(
              sg,
              ec2.Port.tcp(443),
              'Allow SSM access from Workload'
          );
          workloadSecurityGroup.connections.allowTo(
              sg,
              ec2.Port.tcp(443),
              'Allow SSM access to Workload'
          );
        }
    );

    // define cluster for workload
    const cluster = new ecs.Cluster(
        this,
        'WorkloadCluster',
        {
          vpc,
          clusterName: 'WorkloadCluster',
        }
    );

    // define service for workload referencing task definition,
    // security group, cluster and private subnets.note we
    // are not assigning a public IP
    const workloadService = new ecs.FargateService(
        this,
        'WorkloadService',
        {
          cluster,
          serviceName: 'WorkloadService',
          taskDefinition: workloadTaskDefinition,
          securityGroups: [workloadSecurityGroup],
          // ensure the workload is deployed to private subnets
          vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
          desiredCount: 1,
        }
    );
  }
}
