import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {InstanceClass, InstanceSize, InstanceType} from "aws-cdk-lib/aws-ec2";


interface Ec2StackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
}
export class Ec2StackInstanceConnect extends cdk.Stack {
  constructor(scope: Construct, id: string, props: Ec2StackProps) {
    super(scope, id, props);

    const { vpc } = props;

    const securityGroup = new ec2.SecurityGroup(this, 'SG-EC2-Instance-Connect', {
      vpc: vpc,
      allowAllOutbound: true,
      description: 'SG for EC2 Port 22 SSH access',
      securityGroupName: 'SG-EC2-Instance-Connect'
    });

    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(22), 'SSH access');

    const ec2Instance = new ec2.Instance(this, 'EC2InstanceConnect', {
      vpc,
      securityGroup,
      instanceType: InstanceType.of(InstanceClass.T3, InstanceSize.MICRO),
      machineImage: new ec2.AmazonLinuxImage({
        generation: ec2.AmazonLinuxGeneration.AMAZON_LINUX_2,
      }),
    });

    const sendSSHKeyCommand = `aws ec2-instance-connect send-ssh-public-key --region <region> --availability-zone ${ec2Instance.instanceAvailabilityZone} --instance-id ${ec2Instance.instanceId} --instance-os-user ec2-user --ssh-public-key file://ephemeral_key.pub --no-cli-pager`;
    const sshCommand = `ssh -o "IdentitiesOnly=yes" -i ephemeral_key ec2-user@${ec2Instance.instancePublicDnsName}`;

    new cdk.CfnOutput(this, 'SendSSHKeyCommand', { value: sendSSHKeyCommand});
    new cdk.CfnOutput(this, 'SshCommand', { value: sshCommand});
  }
}
