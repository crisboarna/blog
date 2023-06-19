import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as ecr from 'aws-cdk-lib/aws-ecr';

interface FargateStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
  ssmVpcEndpointSG: ec2.ISecurityGroup;
  ssmMessagesVpcEndpointSG: ec2.ISecurityGroup;
  ec2MessagesVpcEndpointSG: ec2.ISecurityGroup;
  logsVpcEndpointSG: ec2.ISecurityGroup;
  ecrVpcEndpointSG: ec2.ISecurityGroup;
  ecrDockerVpcEndpoint: ec2.ISecurityGroup;
  bastionEcrRepo: ecr.Repository;
}

export class FargateStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: FargateStackProps) {
    super(scope, id, props);

    const {
        vpc,
        logsVpcEndpointSG,
        ec2MessagesVpcEndpointSG,
        ssmMessagesVpcEndpointSG,
        ssmVpcEndpointSG,
        ecrVpcEndpointSG,
        ecrDockerVpcEndpoint,
        bastionEcrRepo
    } = props;

    // create log group for ECS exec audit logs
    const ecsExecAuditLogGroup = new logs.LogGroup(
      this,
      'EcsExecAuditLogGroup',
      {
          logGroupName: '/ecs/ecs-exec/audit',
          removalPolicy: cdk.RemovalPolicy.DESTROY,
          retention: logs.RetentionDays.ONE_WEEK,
      }
    );

    // define role for session manager to be used by ECS task
    const sessionManagerRole = new iam.Role(
        this,
        'SessionManagerRole',
        {
          assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com')
    });

    // grant session manager role access to audit log group
    ecsExecAuditLogGroup.grantWrite(sessionManagerRole);

    // add managed policy to session manager role
    sessionManagerRole.addManagedPolicy(
        iam.ManagedPolicy
            .fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore')
    );

    // define task definition for bastion
    const bastionTaskDefinition =
        new ecs.FargateTaskDefinition(
            this,
            'BastionFargateTaskDefinition',
            {
              family: 'Bastion',
              cpu: 256,
              memoryLimitMiB: 512,
              taskRole: sessionManagerRole,
              executionRole: sessionManagerRole,
            }
    );

    // define container for bastion
    const bastionContainer =
        bastionTaskDefinition.addContainer('BastionFargateContainer', {
      image: ecs.ContainerImage.fromEcrRepository(bastionEcrRepo),
      // ensure container stays alive
      command: ['sleep', 'infinity'],
      healthCheck: {
        // ECS will kill the container if it fails to start within 30 seconds
        command: ['CMD-SHELL', 'exit', '0'],
      },
      // needed for ECS Exec to work
      linuxParameters: new ecs.LinuxParameters(this, `LinuxParams`, {
          initProcessEnabled: true,
      }),
    });

    // define security group for bastion
    const bastionSecurityGroup =
        new ec2.SecurityGroup(
            this,
            'SecurityGroupBastion',
            {
                vpc: vpc,
                allowAllOutbound: false
            }
        );

    [
        // needed for ECS Exec to communicate with SSM
        ssmVpcEndpointSG,
        ssmMessagesVpcEndpointSG,
        ec2MessagesVpcEndpointSG,
        // needed for audit logs to reach CloudWatch
        logsVpcEndpointSG,
        // needed for ECS Exec to pull image from ECR
        ecrVpcEndpointSG,
        ecrDockerVpcEndpoint
    ].forEach(
        (sg) => {
            bastionSecurityGroup.connections.allowFrom(
                sg,
                ec2.Port.tcp(443),
                'Allow SSM access from Bastion'
            );
            bastionSecurityGroup.connections.allowTo(
                sg,
                ec2.Port.tcp(443),
                'Allow SSM access to Bastion'
            );
        }
    );

    // define cluster for bastion and configure ecs exec auditing
    const cluster = new ecs.Cluster(
        this,
        'BastionCluster',
        {
          vpc,
          clusterName: 'BastionCluster',
          executeCommandConfiguration: {
              logging: ecs.ExecuteCommandLogging.OVERRIDE,
              logConfiguration: {
                  cloudWatchEncryptionEnabled: false,
                  cloudWatchLogGroup: ecsExecAuditLogGroup,
              }
          }
        }
    );

    // define service for bastion referencing task definition,
    // security group, cluster and private subnets.note we
    // are not assigning a public IP and enabling execute command
    const bastionService = new ecs.FargateService(
        this,
        'BastionService',
        {
          cluster,
          serviceName: 'BastionService',
          taskDefinition: bastionTaskDefinition,
          securityGroups: [bastionSecurityGroup],
          // ensure the bastion is deployed to private subnets
          vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
          // ensure execute command is enabled
          enableExecuteCommand: true,
          desiredCount: 1,
        }
      );

    const ecsExecCommand = `export REGION=<region> && aws ecs execute-command \\
    --region $REGION \\
    --cluster BastionCluster \\
    --task $(aws ecs list-tasks --region $REGION --cluster ${cluster.clusterArn} --service-name ${bastionService.serviceName} --query 'taskArns[0]' --output text --no-cli-pager) \\
    --command "/bin/bash" \\
    --interactive`

    new cdk.CfnOutput(this, 'ECSExecCommand', { value: ecsExecCommand });
  }
}