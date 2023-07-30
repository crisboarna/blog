#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { VpcStack } from '../lib/vpc-stack';
import { VpnStack } from '../lib/vpn-stack';

const app = new cdk.App();

const serverCert = app.node.tryGetContext('serverCert');
const clientCert = app.node.tryGetContext('clientCert');

const vpc = new VpcStack(app, 'VpcStack');
const vpn = new VpnStack(app, 'VpnStack', {
    vpc: vpc.vpc,
    vpnCert: {
        serverCert,
        clientCert
    }
});