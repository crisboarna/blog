package com.engineermindscape.blog.efs.escapehatch.lib;

import com.engineermindscape.blog.efs.escapehatch.config.Config;
import com.engineermindscape.blog.efs.escapehatch.config.ENV;
import com.engineermindscape.blog.efs.escapehatch.props.BaseStackProps;
import com.engineermindscape.blog.efs.escapehatch.utils.Utils;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ExecuteCommandConfiguration;
import software.amazon.awscdk.services.ecs.ExecuteCommandLogConfiguration;
import software.amazon.awscdk.services.ecs.ExecuteCommandLogging;
import software.amazon.awscdk.services.iam.ArnPrincipal;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import java.util.*;

public class EcsStack extends Stack {
    public EcsStack(final Construct scope, final String id, final BaseStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;

        IVpc vpc = Utils.getVpc(this, projectName, stackEnv);

        Key kmsKey = this.createKmsKey(
                projectName + "-KMS-Key-Execute-Command-" + stackEnv
        );

        Cluster cluster = Cluster
                .Builder
                .create(this, projectName + "-ECS-Cluster-" + stackEnv)
                     .vpc(vpc)
                     .containerInsights(true)
                     .enableFargateCapacityProviders(true)
                     .executeCommandConfiguration(
                             ExecuteCommandConfiguration
                                     .builder()
                                     .kmsKey(kmsKey)
                                     .logging(ExecuteCommandLogging.OVERRIDE)
                                     .logConfiguration(getLogConfiguration(projectName, stackEnv))
                                     .build())
                     .clusterName(projectName + "-ECS-" + stackEnv)
                     .build();

        Utils.createSsmParameter(
                this,
                projectName,
                stackEnv,
                Config.SSM_ECS_KMS_EXEC_ARN,
                kmsKey.getKeyArn()
        );
        Utils.createSsmParameter(
                this,
                projectName,
                stackEnv,
                Config.SSM_ECS_NAME,
                cluster.getClusterName()
        );
    }

    @NotNull
    private ExecuteCommandLogConfiguration getLogConfiguration(String projectName, ENV stackEnv) {
        return ExecuteCommandLogConfiguration
                .builder()
                .cloudWatchEncryptionEnabled(false)
                .cloudWatchLogGroup(
                        this.createLogGroup(projectName + "-ECS-Execute-Command-Log-Group-" + stackEnv))
                .build();
    }

    private LogGroup createLogGroup(String logGroupName) {
        return LogGroup.Builder.create(this, logGroupName)
                               .logGroupName(logGroupName)
                               .removalPolicy(RemovalPolicy.DESTROY)
                               .build();
    }

    private Key createKmsKey(String kmsKeyName) {
        return Key.Builder.create(this, kmsKeyName)
                          .description("Used for ECS Execute Command encryption")
                          .alias(kmsKeyName)
                          .enableKeyRotation(true)
                          .removalPolicy(RemovalPolicy.DESTROY)
                          .policy(PolicyDocument
                                  .Builder.create()
                                          .statements(
                                                  new ArrayList<>(Collections.singletonList(
                                                          PolicyStatement.Builder.create()
                                                                                 .sid("Allow administration of the ECS Execute Command")
                                                                                 .principals(Collections.singletonList(
                                                                                         new ArnPrincipal("arn:aws:iam::" + this.getAccount() + ":root")))
                                                                                 .actions(Collections.singletonList("kms:*"))
                                                                                 .effect(Effect.ALLOW)
                                                                                 .resources(Collections.singletonList("*"))
                                                                                 .build()))
                                          )
                                          .build())
                          .build();
    }
}
