package com.engineermindscape.blog.efs.escapehatch.bin;

import com.engineermindscape.blog.efs.escapehatch.config.Config;
import com.engineermindscape.blog.efs.escapehatch.config.ENV;
import com.engineermindscape.blog.efs.escapehatch.lib.EcsServicesEfsUtilStack;
import com.engineermindscape.blog.efs.escapehatch.lib.EcsStack;
import com.engineermindscape.blog.efs.escapehatch.lib.EfsStack;
import com.engineermindscape.blog.efs.escapehatch.lib.VpcStack;
import com.engineermindscape.blog.efs.escapehatch.props.BaseStackProps;
import com.engineermindscape.blog.efs.escapehatch.props.VpcStackProps;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;

import java.util.Optional;

public class EfsEscapeHatchApp {
    public static void main(final String[] args) {
        App app = new App();

        String projectName = (String) app.getNode().tryGetContext("projectName");
        Optional<ENV> stackEnvOptional = ENV.get((String) app.getNode().tryGetContext("stackEnv"));

        if (stackEnvOptional.isEmpty()) {
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

        EfsEscapeHatchApp.getResources(app, projectName, stackEnv, env);

        app.synth();
    }

    private static void getResources(App app, String projectName, ENV stackEnv, Environment env) {
        boolean skipDependencies = app.getNode().tryGetContext("skipDependencies") != null ? Boolean.parseBoolean((String) app.getNode().tryGetContext("skipDependencies")) : true;

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
                              .description("Contains the ECS cluster and all related resources. Does not contain container definitions.")
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

        Stack ecsEfsUtilStack = new EcsServicesEfsUtilStack(app, projectName + "-ECS-EFS-Util-" + stackEnv,
                BaseStackProps.builder()
                              .env(env)
                              .description("Contains the ECS Fargate EFS Utility services.")
                              .projectName(projectName)
                              .stackEnv(stackEnv)
                              .build()
        );

        if (!skipDependencies) {
            ecsEfsUtilStack.addDependency(ecsStack);
        }
    }
}

