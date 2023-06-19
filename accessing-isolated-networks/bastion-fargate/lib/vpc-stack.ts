import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Construct} from "constructs";

export class VpcStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly ssmVpcEndpointSG: ec2.ISecurityGroup;
  public readonly ssmMessagesVpcEndpointSG: ec2.ISecurityGroup;
  public readonly ec2MessagesVpcEndpointSG: ec2.ISecurityGroup;
  public readonly logsVpcEndpointSG: ec2.ISecurityGroup;
  public readonly ecrVpcEndpoint: ec2.ISecurityGroup;
  public readonly ecrDockerVpcEndpoint: ec2.ISecurityGroup;
  public readonly bastionEcrRepo: ecr.Repository;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    this.vpc = new ec2.Vpc(this, 'VPC', {
      ipAddresses: ec2.IpAddresses.cidr('10.0.0.0/16'),
      maxAzs: 2,
      natGateways: 0,
      enableDnsHostnames: true,
      enableDnsSupport: true,
      subnetConfiguration: [
        {
          name: 'Subnet-Private',
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
          cidrMask: 24,
        },
      ],
    });

    const ssmVpcEndpoint = new ec2.InterfaceVpcEndpoint(this, 'SSMVpcEndpoint', {
      service: ec2.InterfaceVpcEndpointAwsService.SSM,
      vpc: this.vpc,
      privateDnsEnabled: true,
      subnets: {
        subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
      },
    });

    const ssmMessagesVpcEndpoint = new ec2.InterfaceVpcEndpoint(
        this,
        'SSMMessagesVpcEndpoint',
        {
          service: ec2.InterfaceVpcEndpointAwsService.SSM_MESSAGES,
          vpc: this.vpc,
          privateDnsEnabled: true,
          subnets: {
            subnetType: ec2.SubnetType.PRIVATE_ISOLATED
          },
        }
    );

    const ec2MessagesVpcEndpoint = new ec2.InterfaceVpcEndpoint(
        this,
        'Ec2MessagesVpcEndpoint',
        {
          service: ec2.InterfaceVpcEndpointAwsService.EC2_MESSAGES,
          vpc: this.vpc,
          privateDnsEnabled: true,
          subnets: {
            subnetType: ec2.SubnetType.PRIVATE_ISOLATED
          },
        }
    );

    const logsVpcEndpoint = new ec2.InterfaceVpcEndpoint(
        this,
        'LogsVpcEndpoint',
        {
          service: ec2.InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS,
          vpc: this.vpc,
          privateDnsEnabled: true,
          subnets: {
            subnetType: ec2.SubnetType.PRIVATE_ISOLATED
          },
        }
    );

    // as we need to pull images from ECR, we need to interact with the ECR API
    const ecrVpcEndpoint = new ec2.InterfaceVpcEndpoint(
        this,
        'ECRVpcEndpoint',
        {
            service: ec2.InterfaceVpcEndpointAwsService.ECR,
            vpc: this.vpc,
            privateDnsEnabled: true,
            subnets: {
                subnetType: ec2.SubnetType.PRIVATE_ISOLATED
            },
        }
    );

    // as we need to pull images from ECR, we need to interact with the ECR API
    const ecrDockerVpcEndpoint = new ec2.InterfaceVpcEndpoint(
        this,
        'ECRDockerVpcEndpoint',
        {
            service: ec2.InterfaceVpcEndpointAwsService.ECR_DOCKER,
            vpc: this.vpc,
            privateDnsEnabled: true,
            subnets: {
                subnetType: ec2.SubnetType.PRIVATE_ISOLATED
            },
        }
    );

    // as ECR stores images in S3, we need to add a gateway endpoint for S3
    new ec2.GatewayVpcEndpoint(
        this,
        'S3VpcGateway',
        {
            service: ec2.GatewayVpcEndpointAwsService.S3,
            vpc: this.vpc,
            subnets: [{subnetType: ec2.SubnetType.PRIVATE_ISOLATED}]
        }
    );

    this.bastionEcrRepo = new ecr.Repository(this, 'ECRRepo', {
        repositoryName: 'bastion-fargate',
    });

    this.bastionEcrRepo.addToResourcePolicy(new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        principals: [new iam.AccountPrincipal(cdk.Aws.ACCOUNT_ID)],
        actions: [
            "ecr:BatchCheckLayerAvailability",
            "ecr:GetDownloadUrlForLayer",
            "ecr:BatchGetImage"
        ],
    }));

    this.ssmVpcEndpointSG = ssmVpcEndpoint
        .connections.securityGroups[0];
    this.ssmMessagesVpcEndpointSG = ssmMessagesVpcEndpoint
        .connections.securityGroups[0];
    this.ec2MessagesVpcEndpointSG = ec2MessagesVpcEndpoint
        .connections.securityGroups[0];
    this.logsVpcEndpointSG = logsVpcEndpoint
        .connections.securityGroups[0];
    this.ecrVpcEndpoint = ecrVpcEndpoint
        .connections.securityGroups[0];
    this.ecrDockerVpcEndpoint = ecrDockerVpcEndpoint
        .connections.securityGroups[0];
  }
}