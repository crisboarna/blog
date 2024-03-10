package com.engineermindscape.blog.teamcity.bin;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.lib.*;
import com.engineermindscape.blog.teamcity.props.BaseStackProps;
import com.engineermindscape.blog.teamcity.props.EcrStackProps;
import com.engineermindscape.blog.teamcity.props.VpcStackProps;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;

import java.util.Arrays;
import java.util.Optional;

public class IacApp {
    public static void main(final String[] args) {
        App app = new App();

        String projectName = (String) app.getNode().tryGetContext("projectName");
        System.out.println(app.getNode().tryGetContext("stackEnv"));
        Optional<ENV> stackEnvOptional = ENV.get((String) app.getNode().tryGetContext("stackEnv"));

        if (!stackEnvOptional.isPresent()) {
            throw new RuntimeException("Invalid stackEnv. Please pass -c stackEnv=<ENV>");
        }

        if (projectName.isEmpty()) {
            throw new RuntimeException("Invalid projectName. Please pass -c projectName=<PROJECT_NAME>");
        }

        ENV stackEnv = stackEnvOptional.get();

        Environment env = Environment.builder()
                                     .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                                     .region(System.getenv("CDK_DEFAULT_REGION"))
                                     .build();

        IacApp.getGlobalResources(app, projectName, stackEnv, env);

        app.synth();
    }

    private static void getGlobalResources(App app, String projectName, ENV stackEnv, Environment env) {
        boolean skipDependencies = app.getNode().tryGetContext("SKIP_DEPENDENCIES") != null ? Boolean.parseBoolean((String) app.getNode().tryGetContext("SKIP_DEPENDENCIES")) : true;

        new EcrStack(app, projectName + "-ECR-" + stackEnv,
                EcrStackProps.builder()
                             .env(env)
                             .description("Contains the ECR repositories.")
                             .projectName(projectName)
                             .stackEnv(stackEnv)
                             .repositoryNames(Arrays.asList(
                                             Config.ECR_NAME_TC_MYSQL,
                                             Config.ECR_NAME_TC_SERVER,
                                             Config.ECR_NAME_TC_AGENT,
                                             Config.ECR_NAME_TC_KANIKO,
                                             Config.ECR_NAME_TC_EFS_UTIL
                                     )
                             )
                             .accountIds(Config.ACCOUNT_IDS)
                             .build()
        );

        Stack codeArtifactStack = new CodeArtifactStack(app, projectName + "-CodeArtifact-" + stackEnv,
                BaseStackProps.builder()
                              .env(env)
                              .description("Contains the CodeArtifact and all related resources.")
                              .projectName(projectName)
                              .stackEnv(stackEnv)
                              .build()
        );

        Stack vpcStack = new VpcStack(app, projectName + "-VPC-" + stackEnv,
                VpcStackProps.builder()
                             .env(env)
                             .description("Contains the VPC and all related resources.")
                             .projectName(projectName)
                             .stackEnv(stackEnv)
                             .cidrVpc(Config.VPC_CIDR)
                             .cidrVpn(Config.VPC_VPN_CIDR)
                             .build()
        );

        Stack ecsStack = new EcsStack(app, projectName + "-ECS-" + stackEnv,
                BaseStackProps.builder()
                              .env(env)
                              .description("Contains the ECS cluster and all related resources. Does not contains container definitions.")
                              .projectName(projectName)
                              .stackEnv(stackEnv)
                              .build()
        );

        if (!skipDependencies) {
            ecsStack.addDependency(vpcStack);
        }

        Stack efsStack = new EfsStack(app, projectName + "-EFS-" + stackEnv,
                BaseStackProps.builder()
                              .env(env)
                              .description("Contains the EFS file system and all related resources.")
                              .projectName(projectName)
                              .stackEnv(stackEnv)
                              .build()
        );

        if (!skipDependencies) {
            efsStack.addDependency(vpcStack);
        }

        Stack ecsServicesTeamCityMysqlStack = new EcsServicesTeamCityMySQLStack(app, projectName + "-ECS-Services-TeamCity-MySQL-" + stackEnv,
                BaseStackProps.builder()
                              .env(env)
                              .description("Contains the ECS Fargate TeamCity MySQL services.")
                              .projectName(projectName)
                              .stackEnv(stackEnv)
                              .build()
        );

        if (!skipDependencies) {
            ecsServicesTeamCityMysqlStack.addDependency(ecsStack);
            ecsServicesTeamCityMysqlStack.addDependency(efsStack);
        }

        Stack ecsServicesTeamCityServerStack = new EcsServicesTeamCityServerStack(app, projectName + "-ECS-Services-TeamCity-Server-" + stackEnv,
                BaseStackProps.builder()
                              .env(env)
                              .description("Contains the ECS Fargate TeamCity Server services.")
                              .projectName(projectName)
                              .stackEnv(stackEnv)
                              .build()
        );

        if (!skipDependencies) {
            ecsServicesTeamCityServerStack.addDependency(ecsStack);
            ecsServicesTeamCityServerStack.addDependency(efsStack);
        }

        new EcsServicesTeamCityAgentStack(app, projectName + "-ECS-Services-TeamCity-Agent-" + stackEnv,
                BaseStackProps.builder()
                              .env(env)
                              .description("Contains the ECS Fargate TeamCity Agent services.")
                              .projectName(projectName)
                              .stackEnv(stackEnv)
                              .build()
        );
    }
}

