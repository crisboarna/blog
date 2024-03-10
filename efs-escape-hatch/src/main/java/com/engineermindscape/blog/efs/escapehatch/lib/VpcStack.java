package com.engineermindscape.blog.efs.escapehatch.lib;

import com.engineermindscape.blog.efs.escapehatch.config.Config;
import com.engineermindscape.blog.efs.escapehatch.config.ENV;
import com.engineermindscape.blog.efs.escapehatch.props.VpcStackProps;
import com.engineermindscape.blog.efs.escapehatch.utils.Utils;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class VpcStack extends Stack {
    public VpcStack(final Construct scope, final String id, final VpcStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;

        List<SubnetConfiguration> subnetsPublic =
                IntStream.range(0, 1)
                   .mapToObj(i -> SubnetConfiguration.builder()
                             .subnetType(SubnetType.PUBLIC)
                             .name(String.format("Public-%d-", i))
                             .cidrMask(24)
                             .build())
                   .toList();

        List<SubnetConfiguration> subnetsPrivate =
                IntStream.range(0, 1)
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
                             .subnetConfiguration(
                                     Stream.concat(
                                             subnetsPublic.stream(),
                                             subnetsPrivate.stream())
                                           .collect(Collectors.toList()))
                             .ipAddresses(IpAddresses.cidr(props.cidrVpc))
                             .maxAzs(2)
                             .build();

        Utils.createSsmParameter(
                this,
                projectName,
                stackEnv,
                Config.SSM_VPC_ID,
                vpc.getVpcId()
        );
        Utils.createSsmParameter(
                this,
                projectName,
                stackEnv,
                Config.SSM_VPC_NAME,
                vpcName
        );
    }
}
