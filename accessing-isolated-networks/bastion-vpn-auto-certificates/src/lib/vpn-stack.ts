import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import { Construct } from 'constructs';
import IPCIDR from "ip-cidr";
import {S3, SSM} from "aws-sdk";
import { execSync } from 'child_process';
import {SSM_VPC_ID, SSM_VPC_VPN_CERT_CLIENT_ID, SSM_VPC_VPN_CERT_SERVER_ID} from "../config";
import * as path from 'path';

interface VpnStackProps extends cdk.StackProps {
  projectName: string;
  stackEnv: string;
  vpcCidr:string;
  s3BucketName: string;
}

export class VpnStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: VpnStackProps) {
    super(scope, id, props);

    const { vpcCidr, s3BucketName, projectName, stackEnv } = props;

    this.checkCertificatesExist(stackEnv, s3BucketName).then(exists => {
      if(!exists) {
        const {serverCertArn,clientCertArn } =
            this.generateCertificates(stackEnv, s3BucketName);
        // these need to be created via sdk as the CloudFormation resources
        // require them to be available before the stack is created
        // and they cannot be created and referenced in the same stack.
        // Alternative would be to create a custom resource to create the
        // SSM parameters, but that is more complex and requires a lambda
        // function or to use a different stack for the certificates.
        Promise.all([
          this.createSSMParameter(projectName, stackEnv, SSM_VPC_VPN_CERT_SERVER_ID, serverCertArn),
          this.createSSMParameter(projectName, stackEnv, SSM_VPC_VPN_CERT_CLIENT_ID, clientCertArn)
        ]);
      }
    });

    const vpc: ec2.IVpc = ec2.Vpc.fromLookup(
        this,
        `${projectName}-VPC-Import-${stackEnv}`,
        {
          isDefault: false,
          vpcName: `${projectName}-VPC-${stackEnv}`,
          vpcId: ssm.StringParameter.valueFromLookup(
            this,
            `/${projectName.toLowerCase()}${SSM_VPC_ID}${stackEnv.toLowerCase()}`
          ),
          region: this.region
        }
    );

    // get SSM Parameter values containing the server/client ACM ARN, that was created in
    // above if statement, if it is the first run
    const [serverCertArn,clientCertArn ] = [
        SSM_VPC_VPN_CERT_SERVER_ID,
        SSM_VPC_VPN_CERT_CLIENT_ID].map((paramName, idx) =>
          ssm.StringParameter.fromStringParameterName(
              this,
              `${projectName}-SSM-Param-${idx}-${stackEnv}`,
              `/${projectName.toLowerCase()}${paramName}${stackEnv.toLowerCase()}`)
        );

    // create a new security group for the client vpn endpoint
    const securityGroup = new ec2.SecurityGroup(
        this,
        `${projectName}-SG-Bastion-${stackEnv}`,
        {
          vpc: vpc,
          allowAllOutbound: true,
          description: 'SG for Bastion VPN access',
          securityGroupName: `${projectName}-SG-Bastion-${stackEnv}`
        }
    );

    // create ip cidr helper object to extract the AWS DNS server IP
    const ipCidr = new IPCIDR(vpcCidr);

    // create the new client vpn endpoint
    const clientVpnEndpoint =
        vpc.addClientVpnEndpoint(
            `${projectName}-Bastion-Client-VPN-${stackEnv}`,
            {
              // cidr range from which client will connect. must not overlap with that of the VPC
              cidr: "30.0.0.0/16",
              serverCertificateArn: serverCertArn.stringValue,
              clientCertificateArn: clientCertArn.stringValue,
              description: `Bastion access`,
              // split tunneling allows the client to access the internet directly instead of going through the VPN
              splitTunnel: true,
              // allow all users to connect to the VPN
              authorizeAllUsersToVpcCidr: true,
              // prioritize the AWS DNS server over the Google DNS server for DNS resolution in the VPN tunnel
              dnsServers: [ipCidr.toArray({ from: 2, limit: 1 })[0], "8.8.8.8"],
              // allow UDP traffic on port 1194 from the client to the VPN
              transportProtocol: ec2.TransportProtocol.UDP,
              securityGroups: [securityGroup],
              logging: true,
              // associate the client vpn endpoint with the private subnets of the VPC
              vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
            }
        );

    const cfClientVpnEndpoint =
        clientVpnEndpoint.node.defaultChild as ec2.CfnClientVpnEndpoint;

    // add a tag to the client vpn endpoint to change name of it to desired name
    cfClientVpnEndpoint.addPropertyOverride("TagSpecifications", [
      {
        ResourceType: "client-vpn-endpoint",
        Tags: [{ Key: "Name", Value: `Bastion-Client-VPN` }],
      },
    ]);
  }

  private checkCertificatesExist(stackEnv: string, bucketName: string): Promise<boolean> {
    return new Promise((resolve, reject) => {
        const s3 = new S3({ region: this.region });
        s3.headObject({
          Bucket: bucketName,
          Key: `${stackEnv.toLowerCase()}/certificates/ca.crt`,
        }).promise().then(() => {
            console.log('ca.crt already exists in the S3 bucket.');
            resolve(true)
        }).catch((e)=> {
            if (e.code === 'NotFound') {
                console.log('ca.crt does not exist in the S3 bucket.');
                resolve(false);
            }
            reject(e);
        });
    });
  }

  private generateCertificates(stackEnv: string, bucketName: string): {serverCertArn: string, clientCertArn: string} {
    const scriptPath = path.join(__dirname, '../utils/certificates.sh');
    const result = execSync(`${scriptPath} ${bucketName} ${stackEnv.toLowerCase()} ${this.region}`);
    const stdout = result.toString();

    const serverCertArn = stdout.split('SERVER_CERT: ')[1].split('\n')[0];
    const clientCertArn = stdout.split('CLIENT_CERT: ')[1].split('\n')[0];

    if (serverCertArn && clientCertArn) {
      console.log('Command successful. Output:');
      console.log(stdout);
      return {serverCertArn, clientCertArn};
    } else {
      console.log('Command failed. Output: ' + stdout);
      throw new Error('Could not generate certificates.');
    }
  }

  private createSSMParameter(projectName: string, stackEnv: string, name: string, value: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const parameterName = `/${projectName.toLowerCase()}${name}${stackEnv.toLowerCase()}`;
      const ssm = new SSM({region: this.region});

      try {
        const response = ssm.putParameter({
          Name: parameterName,
          Value: value,
          Type: 'String',
          Overwrite: true,
        }).promise().then(response => {
          console.log('Parameter version: ' + response.Version);
          resolve();
        });
      } catch (e: any) {
        console.error(e.message);
        process.exit(1);
      }
    });
  }
}
