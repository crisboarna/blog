package com.engineermindscape.blog.teamcity.lib;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.props.BaseStackProps;
import com.engineermindscape.blog.teamcity.utils.Utils;
import software.amazon.awscdk.CustomResource;
import software.amazon.awscdk.CustomResourceProps;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.efs.*;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;
import software.amazon.awscdk.customresources.*;

import java.util.Collections;

public class EfsStack extends Stack {
    public EfsStack(final Construct scope, final String id, final BaseStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;

        IVpc vpc = Utils.getVpc(this, projectName, stackEnv);

        SecurityGroup efsClusterSG = Utils.createSecurityGroup(this, projectName, stackEnv, vpc, "EFS", false);

        FileSystem fileSystem = FileSystem.Builder
                .create(this, String.format("%s-TeamCity-EFS-%s", projectName, stackEnv))
                .vpc(vpc)
                .securityGroup(efsClusterSG)
                .fileSystemName(String.format("%s-TeamCity-EFS-%s", projectName, stackEnv))
                .removalPolicy(RemovalPolicy.DESTROY)
                .enableAutomaticBackups(true)
                .build();

        AccessPoint efsLambdaAccessPointMysql = fileSystem
                .addAccessPoint(String.format("%s-TeamCity-EFS-AccessPoint-%s", projectName, stackEnv),
                     AccessPointOptions
                             .builder()
                             .path("/ci")
                             .createAcl(Acl
                                   .builder()
                                   .ownerUid("65534")
                                   .ownerGid("65534")
                                   .permissions("777")
                                   .build())
                             .posixUser(PosixUser
                                   .builder()
                                   .uid("65534")
                                   .gid("65534")
                                   .build())
                             .build()
                );

        CustomResource lambdaCreateEFSDirsResource = this.getEfsDirectoryConfigurator(
                projectName,
                stackEnv,
                vpc,
                efsLambdaAccessPointMysql,
                efsClusterSG
        );

        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_EFS_ECS_ARN, fileSystem.getFileSystemArn());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_EFS_ECS_ID, fileSystem.getFileSystemId());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_VPC_SG_EFS, efsClusterSG.getSecurityGroupId());
    }

    private CustomResource getEfsDirectoryConfigurator(String projectName, ENV stackEnv, IVpc vpc, AccessPoint efsLambdaAccessPoint, SecurityGroup efsClusterSG) {
        Function lambdaCreateDirs = Function.Builder
                .create(this, String.format("%s-Infra-Custom-Resource-EFS-Dir-Creator-%s", projectName, stackEnv))
                .functionName(String.format("%s-Infra-Custom-Resource-EFS-Dir-Creator-%s", projectName, stackEnv))
                .runtime(Runtime.NODEJS_18_X)
                .handler("index.handler")
                .code(Code
                        .fromInline("const fs = require('fs');\n" +
                                "const path = require('path');\n" +
                                "\n" +
                                "exports.handler = async (event) => {\n" +
                                "  console.log('Creating efs directories');\n" +
                                "  const efsMountPath = '/mnt/efs';\n" +
                                "  const directories = [\n" +
                                "    path.join(efsMountPath, 'mysql/data'),\n" +
                                "    path.join(efsMountPath, 'teamcity/data')\n" +
                                "  ];\n" +
                                "\n" +
                                "  for (const dir of directories) {\n" +
                                "    if (!fs.existsSync(dir)) {\n" +
                                "      fs.mkdirSync(dir, { recursive: true });\n" +
                                "      fs.chmodSync(dir, 0o777);\n" +
                                "    }\n" +
                                "  }\n" +
                                "\n" +
                                "  return {\n" +
                                "    statusCode: 200,\n" +
                                "    body: JSON.stringify({ message: 'Directories created successfully' }),\n" +
                                "  };\n" +
                                "};"))
                .filesystem(software.amazon.awscdk.services.lambda.FileSystem.fromEfsAccessPoint(efsLambdaAccessPoint, "/mnt/efs"))
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder()
                                           .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                           .build())
                .securityGroups(Collections.singletonList(efsClusterSG))
                .build();

        Provider provider = new Provider(this, projectName + "-EFS-Custom-Resource-Provider-" + stackEnv,
                ProviderProps.builder()
                             .providerFunctionName(String.format("%s-TeamCity-EFS-Dir-Creator-Provider-%s", projectName, stackEnv))
                             .onEventHandler(lambdaCreateDirs)
                             .build());

        return new CustomResource(this, projectName + "-EFS-Custom-Resource-" + stackEnv,
                CustomResourceProps.builder()
                                   .serviceToken(provider.getServiceToken())
                                   .build()
        );
    }
}
