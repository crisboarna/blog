import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import { IpAddresses } from 'aws-cdk-lib/aws-ec2';
import {Construct} from "constructs";
import {SSM_VPC_ID} from "../config";

interface BaseStackProps extends cdk.StackProps {
  projectName: string;
  stackEnv: string;
  vpcCidr: string;
}

export class VpcStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: BaseStackProps) {
    super(scope, id, props);

    const { vpcCidr, projectName, stackEnv } = props;

    const vpc = new ec2.Vpc(this, `${projectName}-VPC-${stackEnv}`, {
      vpcName: `${projectName}-VPC-${stackEnv}`,
      ipAddresses: IpAddresses.cidr(vpcCidr),
      maxAzs: 2,
      subnetConfiguration: [
        {
          name: 'Subnet-Isolated',
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
          cidrMask: 24,
        },
      ],
    });

    new ssm.StringParameter(this, `${projectName}-Param-VPC-ID-${stackEnv}`, {
      parameterName: `/${projectName.toLowerCase()}${SSM_VPC_ID}${stackEnv.toLowerCase()}`,
      stringValue: vpc.vpcId
    });
  }
}