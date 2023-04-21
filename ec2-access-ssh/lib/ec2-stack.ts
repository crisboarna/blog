import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {InstanceClass, InstanceSize, InstanceType} from "aws-cdk-lib/aws-ec2";

interface Ec2StackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
}
export class Ec2Stack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: Ec2StackProps) {
    super(scope, id, props);

    const { vpc } = props;

    const securityGroup = new ec2.SecurityGroup(this, 'SG-EC2', {
      vpc: vpc,
      allowAllOutbound: true,
      description: 'SG for EC2 Port 22 SSH access',
      securityGroupName: 'SG-EC2'
    });

    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(22), 'SSH access');

    new ec2.Instance(this, 'Instance2', {
      vpc,
      securityGroup,
      instanceType: InstanceType.of(InstanceClass.T3, InstanceSize.MICRO),
      machineImage: new ec2.AmazonLinuxImage({
        generation: ec2.AmazonLinuxGeneration.AMAZON_LINUX_2,
      }),
    });
  }
}
