package com.engineermindscape.blog.teamcity.lib;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.props.VpcStackProps;
import com.engineermindscape.blog.teamcity.utils.Utils;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Address;
import software.amazon.awssdk.services.ec2.model.DescribeAddressesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeAddressesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.utils.Pair;
import software.constructs.Construct;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class VpcStack extends Stack {
    public VpcStack(final Construct scope, final String id, final VpcStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        Environment env = props.env;
        String region = env.getRegion();
        ENV stackEnv = props.stackEnv;

        List<SubnetConfiguration> subnetsPublic = IntStream.range(0, 1)
                                                           .mapToObj(i -> SubnetConfiguration.builder()
                                                                                             .subnetType(SubnetType.PUBLIC)
                                                                                             .name(String.format("Public-%d-", i))
                                                                                             .cidrMask(24)
                                                                                             .build())
                                                           .toList();

        List<SubnetConfiguration> subnetsPrivate = IntStream.range(0, 1)
                                                            .mapToObj(i -> SubnetConfiguration.builder()
                                                                                              .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                                                                              .name(String.format("Private-%d-", i))
                                                                                              .cidrMask(24)
                                                                                              .build())
                                                            .toList();

        String vpcName = String.format("%s-VPC-%s", projectName, stackEnv);

        Vpc vpc = Vpc.Builder.create(this, vpcName)
                             .vpcName(vpcName)
                             .enableDnsHostnames(true)
                             .enableDnsSupport(true)
                             .subnetConfiguration(Stream.concat(subnetsPublic.stream(), subnetsPrivate.stream())
                                                        .collect(Collectors.toList()))
                             .ipAddresses(IpAddresses.cidr(props.cidrVpc))
                             .maxAzs(2)
                             .build();

        SecurityGroup bastionSG = Utils.createSecurityGroup(this, projectName, stackEnv, vpc, "Bastion", false);

        List<Map<String, Object>> configurations = Arrays.asList(
                Map.of("name", "ECS",
                        "services", Arrays.asList(
                                InterfaceVpcEndpointAwsService.ECS,
                                InterfaceVpcEndpointAwsService.ECS_TELEMETRY,
                                InterfaceVpcEndpointAwsService.ECS_AGENT),
                        "parameter", Config.SSM_VPC_SG_ENDPOINT_ECS),
                Map.of("name", "ECR",
                        "services", Arrays.asList(
                                InterfaceVpcEndpointAwsService.ECR,
                                InterfaceVpcEndpointAwsService.ECR_DOCKER),
                        "parameter", Config.SSM_VPC_SG_ENDPOINT_ECR),
                Map.of("name", "CWLogs",
                        "services", Collections.singletonList(InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS),
                        "parameter", Config.SSM_VPC_SG_ENDPOINT_CW_LOGS),
                Map.of("name", "SM",
                        "services", Collections.singletonList(InterfaceVpcEndpointAwsService.SECRETS_MANAGER),
                        "parameter", Config.SSM_VPC_SG_ENDPOINT_SM),
                Map.of("name", "SSM",
                        "services", Arrays.asList(InterfaceVpcEndpointAwsService.SSM, InterfaceVpcEndpointAwsService.SSM_MESSAGES),
                        "parameter", Config.SSM_VPC_SG_ENDPOINT_SSM),
                Map.of("name", "KMS",
                        "services", Collections.singletonList(InterfaceVpcEndpointAwsService.KMS),
                        "parameter", Config.SSM_VPC_SG_ENDPOINT_KMS),
                Map.of("name", "CodeArtifact",
                        "services", Arrays.asList(InterfaceVpcEndpointAwsService.CODEARTIFACT_API, InterfaceVpcEndpointAwsService.CODEARTIFACT_REPOSITORIES),
                        "parameter", Config.SSM_VPC_SG_ENDPOINT_CODEARTIFACT),
                Map.of("name", "STS",
                        "services", Collections.singletonList(InterfaceVpcEndpointAwsService.STS),
                        "parameter", Config.SSM_VPC_SG_ENDPOINT_STS)
        );

        configurations.stream().map(config -> {
            String name = (String) config.get("name");
            List<InterfaceVpcEndpointAwsService> services = (List<InterfaceVpcEndpointAwsService>) config.get("services");
            String parameter = (String) config.get("parameter");
            SecurityGroup sg = Utils.createSecurityGroup(this, projectName, stackEnv, vpc, "VPC-Endpoint-" + name, false);
            services.forEach(service -> {
                vpc.addInterfaceEndpoint(
                        projectName + "-" + name + "-Interface-Endpoint-" + stackEnv,
                        InterfaceVpcEndpointOptions
                                .builder()
                                .privateDnsEnabled(true)
                                .service(service)
                                .securityGroups(Collections.singletonList(sg))
                                .subnets(SubnetSelection.builder()
                                                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                                        .build())
                                .build()
                );
            });
            Utils.createSsmParameter(this, projectName, stackEnv, parameter, sg.getSecurityGroupId());
            return Pair.of(name, sg);
        }).forEach(pair -> {
            String name = pair.left();
            SecurityGroup sg = pair.right();
            bastionSG.addEgressRule(sg, Port.tcp(443), "Allow access to " + name);
            sg.addIngressRule(bastionSG, Port.tcp(443), "Allow access from VPN");
        });

        vpc.addGatewayEndpoint(
                projectName + "-S3-Gateway-Endpoint-" + stackEnv,
                GatewayVpcEndpointOptions
                        .builder()
                        .subnets(Collections.singletonList(
                                SubnetSelection
                                        .builder()
                                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                        .build()))
                        .service(GatewayVpcEndpointAwsService.S3)
                        .build()
        );

        String s3PrefixListId = Utils.getAwsVpcPrefixListId(this, projectName, stackEnv);

        List<String> elasticIps = this.getVPCElasticIps(region).stream()
                                      .map(ip -> String.format("%s/32", ip))
                                      .toList();

        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_EIP_IP_1, elasticIps.get(0));
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_EIP_IP_2, elasticIps.get(1));
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_VPC_ID, vpc.getVpcId());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_VPC_NAME, vpcName);
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_VPC_SG_BASTION, bastionSG.getSecurityGroupId());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_VPC_ENDPOINT_S3_PREFIX_ID, s3PrefixListId);
    }

    private List<String> getVPCElasticIps(String region) {
        List<String> elasticIps = new ArrayList<>();

        try (Ec2Client ec2Client = Ec2Client.builder()
                                            .region(Region.of(region))
                                            .build()) {
            DescribeAddressesResponse describeAddressesResponse = ec2Client.describeAddresses(
                    DescribeAddressesRequest.builder()
                                            .filters(
                                                    Filter.builder()
                                                          .name("domain")
                                                          .values("vpc")
                                                          .build()
                                            )
                                            .build()
            );

            for (Address address : describeAddressesResponse.addresses()) {
                if (address.publicIp() != null) {
                    elasticIps.add(address.publicIp());
                }
            }
            return elasticIps;
        }
    }
}
