import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {Construct} from "constructs";

interface BastionStackProps extends cdk.StackProps {
    vpc: ec2.Vpc;
    ssmVpcEndpointSG: ec2.ISecurityGroup;
    ssmMessagesVpcEndpointSG: ec2.ISecurityGroup;
    ec2MessagesVpcEndpointSG: ec2.ISecurityGroup;
}
export class BastionStack extends cdk.Stack {
    constructor(scope: Construct, id: string, props: BastionStackProps) {
        super(scope, id, props);

        const {
            vpc,
            ssmVpcEndpointSG,
            ssmMessagesVpcEndpointSG,
            ec2MessagesVpcEndpointSG
        } = props;

        // create security group for Bastion host
        const securityGroup = new ec2.SecurityGroup(
            this,
            'SG-Bastion',
            {
                vpc: vpc,
                allowAllOutbound: false,
                description: 'SG for Bastion host',
                securityGroupName: 'SG-Bastion'
            }
        );

        // allow SSM agent to communicate with required SSM agent endpoints
        securityGroup.connections.allowTo(
            ssmVpcEndpointSG,
            ec2.Port.tcp(443),
            'Allow SSM access to Bastion host'
        );
        securityGroup.connections.allowFrom(
            ssmVpcEndpointSG,
            ec2.Port.tcp(443),
            'Allow SSM access from Bastion host'
        );

        securityGroup.connections.allowTo(
            ssmMessagesVpcEndpointSG,
            ec2.Port.tcp(443),
            'Allow SSM access to Bastion host'
        );
        securityGroup.connections.allowFrom(
            ssmMessagesVpcEndpointSG,
            ec2.Port.tcp(443),
            'Allow SSM access from Bastion host'
        );

        securityGroup.connections.allowTo(
            ec2MessagesVpcEndpointSG,
            ec2.Port.tcp(443),
            'Allow SSM access to Bastion host'
        );
        securityGroup.connections.allowFrom(
            ec2MessagesVpcEndpointSG,
            ec2.Port.tcp(443),
            'Allow SSM access from Bastion host'
        );

        // create the Bastion host
        const bastionHost = new ec2.Instance(
            this,
            'BastionHost',
            {
                vpc,
                securityGroup,
                instanceName: 'BastionHost',
                vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
                instanceType: ec2.InstanceType.of(
                    ec2.InstanceClass.T3,
                    ec2.InstanceSize.MICRO
                ),
                machineImage: new ec2.AmazonLinuxImage(),
            }
        );

        // add the SSM managed policy to the Bastion host
        bastionHost.role.addManagedPolicy({
            managedPolicyArn: 'arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore'
        });

        // output the command to connect to the Bastion host
        const connectCommand = `aws ssm start-session \\
        --target ${bastionHost.instanceId} \\
        --region ${this.region}`;

        new cdk.CfnOutput(this, 'ConnectCommand', { value: connectCommand});
    }
}