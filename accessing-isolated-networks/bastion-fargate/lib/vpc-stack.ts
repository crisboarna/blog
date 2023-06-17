import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { IpAddresses } from 'aws-cdk-lib/aws-ec2';
import {Construct} from "constructs";

export class VpcStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    this.vpc = new ec2.Vpc(this, 'VPC', {
      ipAddresses: IpAddresses.cidr('10.0.0.0/16'),
      maxAzs: 2,
      subnetConfiguration: [
        {
          name: 'Subnet-Private',
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
          cidrMask: 24,
        },
        {
          name: 'Subnet-Public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24,
        },
      ],
    });

    const ssmVpcEndpoint = new ec2.InterfaceVpcEndpoint(this, 'SSMVpcEndpoint', {
      service: ec2.InterfaceVpcEndpointAwsService.SSM,
      vpc: this.vpc,
      privateDnsEnabled: true,
      subnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
    });
  }
}