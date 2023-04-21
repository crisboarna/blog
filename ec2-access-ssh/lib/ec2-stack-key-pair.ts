import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {InstanceClass, InstanceSize, InstanceType} from "aws-cdk-lib/aws-ec2";
import {readFileSync} from 'fs';
import {join} from 'path';

interface Ec2StackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
}

export class Ec2StackKeyPair extends cdk.Stack {
  constructor(scope: Construct, id: string, props: Ec2StackProps) {
    super(scope, id, props);

    const { vpc } = props;
    const keyName = 'ec2-access-ssh-keypair';

    new ec2.CfnKeyPair(this, 'Ec2-KeyPair', {
        keyName,
        publicKeyMaterial: readFileSync(
            join(__dirname, `keys/${keyName}.pub`)
        ).toString(),
    });

    const securityGroup = new ec2.SecurityGroup(this, 'SG-EC2-KeyPair', {
      vpc: vpc,
      allowAllOutbound: true,
      description: 'SG for EC2 Port 22 SSH access',
      securityGroupName: 'SG-EC2-KeyPair'
    });

    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(22), 'SSH access');

    const ec2Instance = new ec2.Instance(this, 'EC2KeyPair', {
      vpc,
      keyName,
      securityGroup,
      instanceType: InstanceType.of(InstanceClass.T3, InstanceSize.MICRO),
      machineImage: new ec2.AmazonLinuxImage({
        generation: ec2.AmazonLinuxGeneration.AMAZON_LINUX_2,
      }),
    });

    const sshCommand = `ssh -i lib/keys/${keyName} ec2-user@${ec2Instance.instancePublicDnsName}`;
    new cdk.CfnOutput(this, 'SshCommand', { value: sshCommand});
  }
}
