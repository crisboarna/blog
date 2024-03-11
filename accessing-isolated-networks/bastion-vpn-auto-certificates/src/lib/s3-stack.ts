import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import {Construct} from "constructs";

interface BaseStackProps extends cdk.StackProps {
  projectName: string;
  stackEnv: string;
  s3BucketName: string;
}

export class S3Stack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: BaseStackProps) {
    super(scope, id, props);

    const { s3BucketName, projectName, stackEnv } = props;

    new s3.Bucket(this, `${projectName}-S3-${stackEnv}`, {
      bucketName: s3BucketName,
      autoDeleteObjects: true,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });
  }
}