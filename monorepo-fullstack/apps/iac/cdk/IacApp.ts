import * as cdk from 'aws-cdk-lib';
import { SampleStack } from './stacks/SampleStack';
// this is the imported symbol from the shared library
import {PROJECT_NAME} from '@engineeringmindscape/shared';

const app = new cdk.App();
new SampleStack(app, PROJECT_NAME, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION,
  },
});
