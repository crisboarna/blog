package com.engineermindscape.blog.teamcity.utils;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.*;
import software.amazon.awscdk.services.applicationautoscaling.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.ssm.IParameter;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.amazon.awscdk.services.ssm.StringParameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for common methods used in the project.
 */
public class Utils {

    /**
     * Creates a SSM parameter.
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @param name
     * @param value
     * @return StringParameter
     */
    public static StringParameter createSsmParameter(Stack scope, String projectName, ENV stackEnv, String name, String value) {
        return StringParameter.Builder.create(scope, String.format("%s-VPC-Param-Name-C-%s-%s", projectName, name, stackEnv))
                                      .parameterName(String.format("/%s%s%s", projectName.toLowerCase(), name, stackEnv.name().toLowerCase()))
                                      .stringValue(value)
                                      .build();
    }

    /**
     * Gets a list of SSM parameters given list of names without unwrapping values from SSM.
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @param names
     * @return List<IParameter>
     */
    public static List<IStringParameter> getSsmParameters(Stack scope, String projectName, ENV stackEnv, List<String> names) {
        return names.stream()
                    .map(name ->
                            StringParameter.fromStringParameterName(
                                    scope,
                                    String.format("%s-VPC-Param-Name-GP-%s-%s", projectName, name, stackEnv),
                                    String.format("/%s%s%s", projectName.toLowerCase(), name, stackEnv.name().toLowerCase()))
                    )
                    .collect(Collectors.toList());
    }

    /**
     * Gets a list of SSM parameters values given list of names and returns raw values.
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @param names
     * @return List<String>
     */
    public static List<String> getSsmParametersValues(Stack scope, String projectName, ENV stackEnv, List<String> names) {
        return names.stream()
                    .map(name ->
                            StringParameter.fromStringParameterName(
                                                   scope,
                                                   String.format("%s-VPC-Param-Name-GV-%s-%s", projectName, name, stackEnv),
                                                   String.format("/%s%s%s", projectName.toLowerCase(), name, stackEnv.name().toLowerCase()))
                                           .getStringValue())
                    .collect(Collectors.toList());
    }

    /**
     * Gets a list of SSM parameters values given list of names and returns decrypted values.
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @param names
     * @return List<String>
     */
    public static List<String> getSecretValues(Stack scope, String projectName, ENV stackEnv, List<String> names) {
        return names.stream()
                    .map(name ->
                            Secret.fromSecretNameV2(
                                          scope,
                                          String.format("%s-VPC-Secret-Name-%s-%s", projectName, name, stackEnv),
                                          String.format("/%s%s%s", projectName.toLowerCase(), name, stackEnv.name().toLowerCase()))
                                  .getSecretValue().toString())
                    .collect(Collectors.toList());
    }

    /**
     * Gets a list of SSM parameters values given list of names and returns Secret objects.
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @param names
     * @return List<ISecret>
     */
    public static List<ISecret> getSecrets(Stack scope, String projectName, ENV stackEnv, List<String> names) {
        return names.stream()
                    .map(name ->
                            Secret.fromSecretNameV2(
                                    scope,
                                    String.format("%s-VPC-Secret-Name-%s-%s", projectName, name, stackEnv),
                                    String.format("/%s%s%s", projectName.toLowerCase(), name, stackEnv.name().toLowerCase()))
                    )
                    .collect(Collectors.toList());
    }

    /**
     * Retrieves the VPC for the current project, environment
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @return IVpc
     */
    public static IVpc getVpc(Stack scope, String projectName, ENV stackEnv) {
        List<String> lookupsContext = Stream.of(Config.SSM_VPC_ID, Config.SSM_VPC_NAME)
                                            .map(param -> StringParameter
                                                    .valueFromLookup(scope,
                                                            String.format("/%s%s%s", projectName.toLowerCase(), param, stackEnv.name().toLowerCase())))
                                            .collect(Collectors.toList());
        String vpcId = lookupsContext.get(0);
        String vpcName = lookupsContext.get(1);

        return Vpc.fromLookup(scope, String.format("%s-ECS-VPC-Import-%s", projectName, stackEnv),
                VpcLookupOptions.builder()
                                .vpcId(vpcId)
                                .vpcName(vpcName)
                                .isDefault(false)
                                .build());
    }

    /**
     * Gets security group given the SSM parameter name containing the ID of the security group.
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @param securityGroupParamName
     * @return ISecurityGroup
     */
    public static ISecurityGroup getSecurityGroup(Stack scope, String projectName, ENV stackEnv, String securityGroupParamName) {
        List<String> parameters = Utils.getSsmParametersValues(
                scope,
                projectName,
                stackEnv,
                Collections.singletonList(securityGroupParamName)
        );
        String sgGroupId = parameters.get(0);

        return SecurityGroup.fromSecurityGroupId(
                scope,
                String.format("%s-ECS-SG-Import-%s-%s", projectName, securityGroupParamName, stackEnv),
                sgGroupId,
                SecurityGroupImportOptions
                        .builder()
                        .allowAllOutbound(false)
                        .build()
        );
    }

    /**
     * Creates a security group.
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @param vpc
     * @param securityGroupName
     * @param allowAllOutbound
     * @return SecurityGroup
     */
    public static SecurityGroup createSecurityGroup(
            Stack scope,
            String projectName,
            ENV stackEnv,
            IVpc vpc,
            String securityGroupName,
            Boolean allowAllOutbound
    ) {
        return SecurityGroup
                .Builder.create(scope, securityGroupName)
                        .vpc(vpc)
                        .securityGroupName(projectName + "-" + securityGroupName + "-SG-" + stackEnv)
                        .description("SG where " + securityGroupName + " is running")
                        .allowAllOutbound(allowAllOutbound)
                        .allowAllIpv6Outbound(allowAllOutbound)
                        .build();
    }

    /**
     * Gets the prefix list ID for the S3 Gateway Endpoint via a custom resource Lambda.
     *
     * @param scope
     * @param projectName
     * @param stackEnv
     * @return String
     */
    public static String getAwsVpcPrefixListId(Stack scope, String projectName, ENV stackEnv) {
        AwsSdkCall awsSdkCall = AwsSdkCall
                .builder()
                .service("EC2")
                .action("describePrefixLists")
                .parameters(new HashMap<String, Object>() {{
                    put("Filters", Collections.singletonList(
                            new HashMap<String, Object>() {{
                                put("Name", "prefix-list-name");
                                put("Values", Collections.singletonList("com.amazonaws." + scope.getRegion() + ".s3"));
                            }}
                    ));
                }})
                .physicalResourceId(PhysicalResourceId.fromResponse("PrefixLists.0.PrefixListId"))
                .build();

        AwsCustomResource customResource = new AwsCustomResource(
                scope,
                projectName + "action" + stackEnv,
                AwsCustomResourceProps
                        .builder()
                        .functionName(projectName + "-Infra-Custom-Resource-Prefix-List-Id-" + stackEnv)
                        .onCreate(awsSdkCall)
                        .onUpdate(awsSdkCall)
                        .policy(AwsCustomResourcePolicy.fromSdkCalls(
                                SdkCallsPolicyOptions
                                        .builder()
                                        .resources(Collections.singletonList("*"))
                                        .build())
                        )
                        .build());
        return customResource.getResponseField("PrefixLists.0.PrefixListId");
    }

    public static ICluster getCluster(Stack scope, String projectName, ENV stackEnv, IVpc vpc, String ecsClusterName, ISecurityGroup securityGroup) {
        return Cluster.fromClusterAttributes(scope, projectName + "-ECS-Cluster-Import-" + stackEnv,
                ClusterAttributes
                        .builder()
                        .vpc(vpc)
                        .clusterName(ecsClusterName)
                        .clusterArn(String.format("arn:aws:ecs:%s:%s:cluster/%s", scope.getRegion(), scope.getAccount(), ecsClusterName))
                        .securityGroups(Collections.singletonList(securityGroup))
                        .build());
    }

    public static LinuxParameters getLinuxParameters(Stack scope, String projectName, ENV stackEnv, String taskName) {
        return LinuxParameters
                .Builder.create(
                        scope,
                        String.format("%s-%s-Linux-Parameters-%s", projectName, taskName, stackEnv))
                        .initProcessEnabled(true)
                        .build();
    }

    public static LogDriver getTaskLoggingConfig(String projectName, LogGroup logGroup) {
        return LogDriver.awsLogs(
                AwsLogDriverProps.builder()
                                 .logGroup(logGroup)
                                 .streamPrefix(projectName)
                                 .build());
    }

    public static LogGroup createLogGroup(Stack scope, String logGroupName) {
        return LogGroup.Builder.create(scope, logGroupName)
                               .logGroupName(logGroupName)
                               .removalPolicy(RemovalPolicy.DESTROY)
                               .build();
    }

    public static void addOutOfHoursDowntime(Stack scope, String projectName, ENV stackEnv, String name, FargateService fargateService) {
        ScalableTarget serviceScalingTarget = ScalableTarget
                .Builder.create(scope, String.format("%s-%s-Scalable-Target-%s", projectName, name, stackEnv))
                        .serviceNamespace(ServiceNamespace.ECS)
                        .resourceId(String.format("service/%s/%s", fargateService.getCluster().getClusterName(), fargateService.getServiceName()))
                        .scalableDimension("ecs:service:DesiredCount")
                        .minCapacity(0)
                        .maxCapacity(1)
                        .role(Role.fromRoleArn(scope, String.format("%s-%s-Scalable-Target-Role-%s", projectName, name, stackEnv),
                                String.format("arn:aws:iam::%s:role/aws-service-role/ecs.application-autoscaling.amazonaws.com/AWSServiceRoleForApplicationAutoScaling_ECSService", scope.getAccount())))
                        .build();

        serviceScalingTarget.scaleOnSchedule(String.format("%s-%s-Scale-Down-Schedule-%s", projectName, name, stackEnv),
                ScalingSchedule
                        .builder()
                        .schedule(Schedule.cron(CronOptions
                                .builder()
                                .minute("0")
                                .hour("20")
                                .day("2-6")
                                .build()))
                        .minCapacity(0)
                        .maxCapacity(0)
                        .build());

        serviceScalingTarget.scaleOnSchedule(String.format("%s-%s-Scale-Up-Schedule-%s", projectName, name, stackEnv),
                ScalingSchedule
                        .builder()
                        .schedule(Schedule.cron(CronOptions
                                .builder()
                                .minute("0")
                                .hour("7")
                                .day("2-6")
                                .build()))
                        .minCapacity(1)
                        .maxCapacity(1)
                        .build());

        CfnScalableTarget cfnScalableTarget = (CfnScalableTarget) serviceScalingTarget.getNode().getDefaultChild();
        cfnScalableTarget.addOverride("Properties.ScheduledActions.0.Timezone", "Europe/London");
        cfnScalableTarget.addOverride("Properties.ScheduledActions.1.Timezone", "Europe/London");
    }

    public static PolicyDocument getEfsMountIamPolicy(String efsArn) {
        return PolicyDocument
                .Builder.create()
                        .statements(Arrays.asList(
                                new PolicyStatement(
                                        PolicyStatementProps.builder()
                                                            .sid("EFSMount")
                                                            .effect(Effect.ALLOW)
                                                            .actions(Arrays.asList(
                                                                    "elasticfilesystem:ClientMount",
                                                                    "elasticfilesystem:ClientWrite"))
                                                            .resources(Collections.singletonList(efsArn))
                                                            .build())
                        ))
                        .build();
    }

    public static PolicyDocument getEcsExecIamPolicy(String ssmExecKmsArn) {
        return PolicyDocument
                .Builder.create()
                        .statements(Arrays.asList(
                                new PolicyStatement(
                                        PolicyStatementProps.builder()
                                                            .sid("SSMExec")
                                                            .effect(Effect.ALLOW)
                                                            .actions(Arrays.asList(
                                                                    "ssmmessages:CreateControlChannel",
                                                                    "ssmmessages:CreateDataChannel",
                                                                    "ssmmessages:OpenControlChannel",
                                                                    "ssmmessages:OpenDataChannel"))
                                                            .resources(Collections.singletonList("*"))
                                                            .build()),
                                new PolicyStatement(
                                        PolicyStatementProps.builder()
                                                            .sid("SSMExecLogs")
                                                            .effect(Effect.ALLOW)
                                                            .actions(Arrays.asList(
                                                                    "logs:DescribeLogGroups",
                                                                    "logs:CreateLogStream",
                                                                    "logs:DescribeLogStreams",
                                                                    "logs:PutLogEvents"))
                                                            .resources(Collections.singletonList("*"))
                                                            .build()),
                                new PolicyStatement(
                                        PolicyStatementProps.builder()
                                                            .sid("SSMExecKMS")
                                                            .effect(Effect.ALLOW)
                                                            .actions(Collections.singletonList(
                                                                    "kms:Decrypt"))
                                                            .resources(Collections.singletonList(ssmExecKmsArn))
                                                            .build())

                        ))
                        .build();
    }
}
