#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { VpcStack } from '../lib/vpc-stack';
import { FargateStack } from '../lib/fargate-stack';

const fargate = new cdk.App();
const vpc = new VpcStack(fargate, 'VpcStack');
const bastion = new FargateStack(
    fargate,
    'BastionFargateStack',
    {
        vpc: vpc.vpc,
        logsVpcEndpointSG: vpc.logsVpcEndpointSG,
        ec2MessagesVpcEndpointSG: vpc.ec2MessagesVpcEndpointSG,
        ssmMessagesVpcEndpointSG: vpc.ssmMessagesVpcEndpointSG,
        ssmVpcEndpointSG: vpc.ssmVpcEndpointSG,
        ecrVpcEndpointSG: vpc.ecrVpcEndpoint,
        ecrDockerVpcEndpoint: vpc.ecrDockerVpcEndpoint,
        bastionEcrRepo: vpc.bastionEcrRepo
    }
);