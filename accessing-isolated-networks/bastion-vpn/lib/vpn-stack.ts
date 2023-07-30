import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';
import IPCIDR from "ip-cidr";

interface VpnStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
  vpnCert: {serverCert: string, clientCert: string};
}

export class VpnStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: VpnStackProps) {
    super(scope, id, props);

    const { vpc, vpnCert, } = props;

    // create a new security group for the client vpn endpoint
    const securityGroup = new ec2.SecurityGroup(this, 'SG-Bastion', {
      vpc: vpc,
      allowAllOutbound: true,
      description: 'SG for Bastion VPN access',
      securityGroupName: 'SG-Bastion'
    });

    // create ip cidr helper object to extract the AWS DNS server IP
    const ipCidr = new IPCIDR(vpc.vpcCidrBlock);

    // create the new client vpn endpoint
    const clientVpnEndpoint = vpc.addClientVpnEndpoint(`Bastion-Client-VPN`, {
      // cidr range from which client will connect. must not overlap with that of the VPC
      cidr: "30.0.0.0/16",
      serverCertificateArn: `arn:aws:acm:${this.region}:${this.account}:certificate/${vpnCert.serverCert}`,
      clientCertificateArn: `arn:aws:acm:${this.region}:${this.account}:certificate/${vpnCert.clientCert}`,
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
    });

    const cfClientVpnEndpoint = clientVpnEndpoint.node.defaultChild as ec2.CfnClientVpnEndpoint;

    // add a tag to the client vpn endpoint to change name of it to desired name
    cfClientVpnEndpoint.addPropertyOverride("TagSpecifications", [
      {
        ResourceType: "client-vpn-endpoint",
        Tags: [{ Key: "Name", Value: `Bastion-Client-VPN` }],
      },
    ]);
  }
}
