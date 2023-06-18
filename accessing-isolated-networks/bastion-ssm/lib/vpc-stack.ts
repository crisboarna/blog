import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';

export class VpcStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly ssmVpcEndpointSG: ec2.ISecurityGroup;
  public readonly ssmMessagesVpcEndpointSG: ec2.ISecurityGroup;
  public readonly ec2MessagesVpcEndpointSG: ec2.ISecurityGroup;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    this.vpc = new ec2.Vpc(this, 'VPC', {
      ipAddresses: ec2.IpAddresses.cidr('10.0.0.0/16'),
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        {
          name: 'Subnet-Private',
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
          cidrMask: 24,
        },
      ],
    });

    // create required VPC endpoints for SSM agent
    const ssmVpcEndpoint = new ec2.InterfaceVpcEndpoint(
        this,
        'SSMVpcEndpoint',
        {
          service: ec2.InterfaceVpcEndpointAwsService.SSM,
          vpc: this.vpc,
          privateDnsEnabled: true,
          subnets: {
            subnetType: ec2.SubnetType.PRIVATE_ISOLATED
          },
        }
    );

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

    this.ssmVpcEndpointSG = ssmVpcEndpoint
        .connections.securityGroups[0];
    this.ssmMessagesVpcEndpointSG = ssmMessagesVpcEndpoint
        .connections.securityGroups[0];
    this.ec2MessagesVpcEndpointSG = ec2MessagesVpcEndpoint
        .connections.securityGroups[0];
  }
}
