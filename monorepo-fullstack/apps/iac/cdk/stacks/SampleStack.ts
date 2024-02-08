// this is the imported symbol from the shared library
import {PROJECT_NAME} from '@engineeringmindscape/shared';
import { CfnOutput, RemovalPolicy, Stack, StackProps } from 'aws-cdk-lib';
import { ApiGatewayToLambda } from '@aws-solutions-constructs/aws-apigateway-lambda';
import { Construct } from 'constructs';
import * as api from 'aws-cdk-lib/aws-apigateway';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { BucketDeployment, Source } from 'aws-cdk-lib/aws-s3-deployment';

export class SampleStack extends Stack {
  constructor(construct: Construct, id: string, props?: StackProps) {
    super(construct, id, props);

    // AWS provided L3 constructs to deploy API Gateway -> Lambda configuration
    new ApiGatewayToLambda(this, `${PROJECT_NAME}-API-GW-Lambda`, {
      lambdaFunctionProps: {
        functionName: `${PROJECT_NAME}-API-GW-Lambda`,
        runtime: lambda.Runtime.NODEJS_20_X,
        handler: 'main.handler',
        code: lambda.Code.fromAsset(`${__dirname}/../../../../dist/apps/api`),
      },
      apiGatewayProps: {
        restApiName: `${PROJECT_NAME}-API-GW`,
        defaultCorsPreflightOptions: {
          allowOrigins: api.Cors.ALL_ORIGINS,
          allowMethods: api.Cors.ALL_METHODS
        },
        defaultMethodOptions: {
          authorizationType: 'NONE'
        }
      },
    });

    const s3Bucket = new s3.Bucket(this, `${PROJECT_NAME}-S3-Bucket`, {
      removalPolicy: RemovalPolicy.DESTROY,
      bucketName: `${PROJECT_NAME.toLowerCase()}-monorepo-demo`,
      publicReadAccess: true,
      autoDeleteObjects: true,
      websiteIndexDocument: 'index.html',
      websiteErrorDocument: 'index.html',
      // required due to https://aws.amazon.com/about-aws/whats-new/2022/12/amazon-s3-automatically-enable-block-public-access-disable-access-control-lists-buckets-april-2023/
      objectOwnership: s3.ObjectOwnership.OBJECT_WRITER,
      blockPublicAccess: new s3.BlockPublicAccess({
        blockPublicAcls: false,
        ignorePublicAcls: false,
        blockPublicPolicy: false,
        restrictPublicBuckets: false,
      }),
    });

    // deploy web app to s3 bucket
    new BucketDeployment(this, `${PROJECT_NAME}-S3-Deployment`, {
      sources: [Source.asset(`${__dirname}/../../../../dist/apps/web`)],
      destinationBucket: s3Bucket,
      metadata: { ForceRedeployment: Date.now().toString() }
    });

    // output the S3 bucket URL
    new CfnOutput(this, `${PROJECT_NAME}-S3-Url-Output`, {
      value: s3Bucket.bucketWebsiteUrl,
    });
  }
}
