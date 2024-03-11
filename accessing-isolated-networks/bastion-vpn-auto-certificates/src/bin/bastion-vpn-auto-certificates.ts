#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { VpcStack } from '../lib/vpc-stack';
import { S3Stack } from '../lib/s3-stack';
import { VpnStack } from '../lib/vpn-stack';

const app = new cdk.App();
const projectName = app.node.tryGetContext('projectName');
const stackEnv = app.node.tryGetContext('stackEnv');
const vpcCidr = app.node.tryGetContext('vpcCidr');
const s3BucketName = app.node.tryGetContext('s3BucketName');
const deployVpn = app.node.tryGetContext('deployVpn') || true;

const env: cdk.Environment = {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION
};

const vpc = new VpcStack(app, `${projectName}-VPC-${stackEnv}`, {
    projectName,
    stackEnv,
    env,
    vpcCidr
});

const s3 = new S3Stack(app, `${projectName}-S3-${stackEnv}`, {
    projectName,
    stackEnv,
    env,
    s3BucketName: `${s3BucketName}-${stackEnv.toLowerCase()}`
});

if(deployVpn) {
    const vpn = new VpnStack(app, `${projectName}-VpnStack-${stackEnv}`, {
        projectName,
        stackEnv,
        env,
        vpcCidr,
        s3BucketName: `${s3BucketName}-${stackEnv.toLowerCase()}`
    });

    vpn.addDependency(s3);
    vpn.addDependency(vpc);
}
