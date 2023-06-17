import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {Construct} from "constructs";

interface BastionStackProps extends cdk.StackProps {
    vpc: ec2.Vpc;
}

export class BastionStack extends cdk.Stack {
    constructor(scope: Construct, id: string, props: BastionStackProps) {
        super(scope, id, props);

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
        // Run following commands on stack creation completion to land with terminal inside of bastion host
        const createSshKeyCommand = 'ssh-keygen -t rsa -f bastion_rsa_key';
        const pushSshKeyCommand = `aws ec2-instance-connect send-ssh-public-key --availability-zone ${bastionHost.instanceAvailabilityZone} --instance-id ${bastionHost.instanceId} --instance-os-user ec2-user --region ${cdk.Aws.REGION} --ssh-public-key file://bastion_rsa_key.pub ${profile ? `--profile ${profile}` : ''}`;
        const sshCommand = `ssh -o "IdentitiesOnly=yes" -i bastion_rsa_key ec2-user@${bastionHost.instancePublicDnsName}`;

        new cdk.CfnOutput(this, 'CreateSshKeyCommand', { value: createSshKeyCommand });
        new cdk.CfnOutput(this, 'PushSshKeyCommand', { value: pushSshKeyCommand });
        new cdk.CfnOutput(this, 'SshCommand', { value: sshCommand});
    }
}