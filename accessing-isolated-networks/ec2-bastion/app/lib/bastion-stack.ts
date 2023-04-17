import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {Construct} from "constructs";

export class BastionStack extends cdk.Stack {
    constructor(scope: Construct, id: string, props: { vpc: ec2.Vpc }, stackProps?: cdk.StackProps) {
        super(scope, id, stackProps);

        const { vpc } = props;

        const securityGroup = new ec2.SecurityGroup(this, 'SG-Bastion', {
            vpc: vpc,
            allowAllOutbound: true,
            description: 'SG for Bastion Port 22 SSH access',
            securityGroupName: 'SG-Bastion'
        });

        securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(22), 'SSH access');

        const bastionHost = new ec2.BastionHostLinux(this, 'BastionHost', {
            vpc,
            securityGroup,
            subnetSelection: { subnetType: ec2.SubnetType.PUBLIC },
        });

        const profile = this.node.tryGetContext('profile');

        const createSshKeyCommand = 'ssh-keygen -t rsa -f my_rsa_key';
        const pushSshKeyCommand = `aws ec2-instance-connect send-ssh-public-key --region ${cdk.Aws.REGION} --instance-id ${bastionHost.instanceId} --availability-zone ${bastionHost.instanceAvailabilityZone} --instance-os-user ec2-user --ssh-public-key file://my_rsa_key.pub ${profile ? `--profile ${profile}` : ''}`;
        const sshCommand = `ssh -o "IdentitiesOnly=yes" -i my_rsa_key ec2-user@${bastionHost.instancePublicDnsName}`;

        new cdk.CfnOutput(this, 'CreateSshKeyCommand', { value: createSshKeyCommand });
        new cdk.CfnOutput(this, 'PushSshKeyCommand', { value: pushSshKeyCommand });
        new cdk.CfnOutput(this, 'SshCommand', { value: sshCommand});
    }
}