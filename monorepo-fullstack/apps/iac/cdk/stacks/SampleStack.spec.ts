import * as cdk from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { SampleStack } from './SampleStack';

test('it works', () => {
  const app = new cdk.App();
  const stack = new SampleStack(app, 'iac');

  const template = Template.fromStack(stack);

  template.hasResource('AWS::S3::Bucket', {
    DeletionPolicy: 'Delete',
  });

  template.hasResourceProperties('AWS::Lambda::Function', {
    Runtime: 'nodejs18.x',
    Handler: 'index.handler',
  });
});
