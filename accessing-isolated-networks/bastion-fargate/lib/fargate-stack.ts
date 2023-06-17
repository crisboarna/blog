import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as iam from 'aws-cdk-lib/aws-iam';

interface FargateStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
}

export class FargateStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: FargateStackProps) {
    super(scope, id, props);

    const {vpc } = props;

    // define role for session manager to be used by ECS task
    const sessionManagerRole = new iam.Role(this, 'SessionManagerRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

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
    });

    // define container for bastion
    const bastionContainer =
        bastionTaskDefinition.addContainer('BastionFargateContainer', {
      image: ecs.ContainerImage.fromRegistry('amazonlinux'),
      memoryLimitMiB: 512,
      containerName: 'bastion',
      command: ['sleep', 'infinity'],
      healthCheck: {
        command: ['CMD-SHELL', 'exit', '0'],
      },
      essential: true,
    });

    // add port mapping for bastion to allow outbound/inbound HTTPS
    bastionContainer.addPortMappings({
      containerPort: 443,
      protocol: ecs.Protocol.TCP,
    });

    // define security group for bastion
    const bastionSecurityGroup =
        new ec2.SecurityGroup(
            this,
            'SecurityGroupBastion',
            { vpc: vpc }
        );

    bastionSecurityGroup.addIngressRule(
        ec2.Peer.ipv4(vpc.vpcCidrBlock),
        ec2.Port.tcp(443),
        'Allow inbound HTTPS');

    // define cluster for bastion
    const cluster = new ecs.Cluster(
        this,
        'BastionCluster',
        {
          vpc,
          clusterName: 'BastionCluster',
          containerInsights: true,
          enableFargateCapacityProviders: true,
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
          vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
          assignPublicIp: false,
          enableExecuteCommand: true,
          desiredCount: 1,
        }
      );

    const ecsExecCommand = `REGION=<region> aws ecs execute-command \\
    --region $REGION \\
    --cluster BastionCluster \\
    --task $(aws ecs list-tasks --region $REGION --cluster ${cluster.clusterArn} --service-name ${bastionService.serviceName} --query 'taskArns[0]' --output text --no-cli-pager) \\
    --container bastion \\
    --command "/bin/bash" \\
    --interactive`
    new cdk.CfnOutput(this, 'ECSExecCommand', { value: ecsExecCommand });
  }
}