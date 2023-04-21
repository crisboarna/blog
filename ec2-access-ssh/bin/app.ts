#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { VpcStack } from '../lib/vpc-stack';
import { Ec2Stack } from '../lib/ec2-stack';

const app = new cdk.App();
const vpc = new VpcStack(app, 'VpcStack');
const ec2 = new Ec2Stack(app, 'Ec2Stack', { vpc: vpc.vpc });