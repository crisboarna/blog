#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { VpcStack } from '../lib/vpc-stack';
import { Ec2StackInstanceConnect } from '../lib/ec2-stack-instance-connect';
import {Ec2StackKeyPair} from "../lib/ec2-stack-key-pair";

const app = new cdk.App();
const vpc = new VpcStack(app, 'VpcStack');
const ec2InstanceConnect = new Ec2StackInstanceConnect(app, 'Ec2StackInstanceConnect', { vpc: vpc.vpc });
const ec2KeyPair = new Ec2StackKeyPair(app, 'Ec2StackKeyPair', { vpc: vpc.vpc });