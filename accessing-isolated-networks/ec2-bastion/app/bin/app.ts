#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { VpcStack } from '../lib/vpc-stack';
import { BastionStack } from '../lib/bastion-stack';

const app = new cdk.App();
const vpc = new VpcStack(app, 'VpcStack');
const bastion = new BastionStack(app, 'BastionStack', { vpc: vpc.vpc });