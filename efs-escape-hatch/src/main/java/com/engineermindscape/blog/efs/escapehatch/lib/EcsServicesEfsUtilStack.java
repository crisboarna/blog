package com.engineermindscape.blog.efs.escapehatch.lib;

import com.engineermindscape.blog.efs.escapehatch.config.Config;
import com.engineermindscape.blog.efs.escapehatch.config.ENV;
import com.engineermindscape.blog.efs.escapehatch.props.BaseStackProps;
import com.engineermindscape.blog.efs.escapehatch.utils.Utils;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class EcsServicesEfsUtilStack extends Stack {
    public EcsServicesEfsUtilStack(final Construct scope, final String id, final BaseStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;
        String nameEfsUtil = "EFS-Util";
        String sharedVolumeName = "util";

        // get parameters from ECS and EFS stacks
        List<IStringParameter> parameters = Utils.getSsmParameters(
                this,
                projectName,
                stackEnv,
                Arrays.asList(
                        Config.SSM_ECS_NAME,
                        Config.SSM_ECS_KMS_EXEC_ARN,
                        Config.SSM_EFS_ECS_ID
                )
        );

        // get VPC construct
        IVpc vpc = Utils.getVpc(this, projectName, stackEnv);

        // create security group for the EFS Util service
        ISecurityGroup sgEcsEfsUtil = Utils.createSecurityGroup(
                this,
                projectName,
                stackEnv,
                vpc,
                "ECS-EFS-Util",
                false
        );

        // Get security group for the EFS file system
        List<ISecurityGroup> securityGroups = Stream
                .of(
                        Config.SSM_VPC_SG_EFS
                )
                .map(sgParamName -> Utils.getSecurityGroup(this, projectName, stackEnv, sgParamName))
                .toList();

        ISecurityGroup sgEfs = securityGroups.get(0);

        String ecsClusterName = parameters.get(0).getStringValue();
        String ecsSsmKmsExecArn = parameters.get(1).getStringValue();
        String ecsEfsId = parameters.get(2).getStringValue();

        // Add rules to allow NFS port traffic between the EFS and EFS Util
        sgEfs.addIngressRule(sgEcsEfsUtil, Port.tcp(2049), "Allow access from ECS EFS Util to EFS");
        sgEcsEfsUtil.addEgressRule(sgEfs, Port.tcp(2049), "Allow access from ECS EFS Util to EFS");
        // Allow access to WAN to pull busybox image from DockerHub
        sgEcsEfsUtil.addEgressRule(Peer.anyIpv4(), Port.tcp(443), "Allow access to AWS Services / DockerHub");

        // Import ECS cluster from ECS stack
        ICluster ecsCluster = Utils.getCluster(this, projectName, stackEnv, vpc, ecsClusterName, sgEcsEfsUtil);

        // Create AWS Cloudwatch Log Group for the task
        LogGroup logGroupEfsUtil = Utils.createLogGroup(this, String.format("/ecs/fargate/service/%s-Log-%s-%s", projectName, nameEfsUtil, stackEnv));

        // Create IAM Role for the task service
        Role ecsRoleTask = this.getEcsServiceTaskRole(
                projectName,
                stackEnv,
                nameEfsUtil,
                ecsSsmKmsExecArn
        );

        // Create IAM Role for the task execution
        Role ecsRoleExecution = this.getEcsServiceExecutionRole(
                projectName,
                stackEnv,
                nameEfsUtil
        );

        // Allow task role to read all the required parameters
        parameters.forEach(parameter -> parameter.grantRead(ecsRoleTask));

        String fargateFamilyName = String.format("%s-%s-Task-%s", projectName, nameEfsUtil, stackEnv);

        // Create Task Definition which mounts the EFS filesystem from /ci
        FargateTaskDefinition taskDefinition = FargateTaskDefinition
                .Builder.create(this, fargateFamilyName)
                        .family(fargateFamilyName)
                        .volumes(Collections.singletonList(
                            Volume.builder()
                                  .name(sharedVolumeName)
                                  .efsVolumeConfiguration(
                                          EfsVolumeConfiguration.builder()
                                                                .fileSystemId(ecsEfsId)
                                                                .rootDirectory("/ci")
                                                                .transitEncryption("ENABLED")
                                                                .transitEncryptionPort(2049)
                                                                .build()
                                  )
                                  .build()
                                )
                        )
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .taskRole(ecsRoleTask)
                        .executionRole(ecsRoleExecution)
                        .runtimePlatform(RuntimePlatform.builder()
                            .cpuArchitecture(CpuArchitecture.ARM64)
                            .operatingSystemFamily(OperatingSystemFamily.LINUX)
                            .build()
                        )
                        .build();

        // define busybox container with 'sleep infinity' entrypoint to always run
        // that will have in /efs the EFS Filesystem mounted from /ci so we have
        // the mapping of /ci(EFS) -> /efs(ECS Util)
        ContainerDefinition agentContainer = taskDefinition.addContainer(String.format("%s-%s-Container-%s", projectName, nameEfsUtil, stackEnv),
                ContainerDefinitionProps
                        .builder()
                        .containerName(nameEfsUtil.toLowerCase().replace("-", "_"))
                        .taskDefinition(taskDefinition)
                        .readonlyRootFilesystem(false)
                        .essential(true)
                        .entryPoint(Arrays.asList("sh", "-c", "sleep infinity"))
                        .image(ContainerImage.fromRegistry(String.format("%s:%s", "busybox","1.36-uclibc")))
                        .logging(this.getTaskLoggingConfig(projectName, logGroupEfsUtil))
                        .healthCheck(this.getTaskHealthCheck())
                        .linuxParameters(Utils.getLinuxParameters(this, projectName, stackEnv, nameEfsUtil))
                        .build());

        agentContainer.addMountPoints(MountPoint.builder()
                                                .containerPath("/efs")
                                                .readOnly(false)
                                                .sourceVolume(sharedVolumeName)
                                                .build());
        // As we cannot use ECS Exec in a standalone task definition container, we
        // define a simple ECS Service that will run this container allowing us to
        // land with a tty shell within
        FargateService
                .Builder.create(this, String.format("%s-%s-Service-%s", projectName, nameEfsUtil, stackEnv))
                        .serviceName(String.format("%s-%s-%s", projectName, nameEfsUtil, stackEnv))
                        .cluster(ecsCluster)
                        .taskDefinition(taskDefinition)
                        .securityGroups(Collections.singletonList(sgEcsEfsUtil))
                        .enableExecuteCommand(true)
                        .vpcSubnets(SubnetSelection
                                .builder()
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .build())
                        .capacityProviderStrategies(Collections.singletonList(
                                CapacityProviderStrategy
                                        .builder()
                                        .capacityProvider("FARGATE")
                                        .weight(1)
                                        .build()))
                        .build();
    }

    private LogDriver getTaskLoggingConfig(String projectName, LogGroup logGroup) {
        return LogDriver.awsLogs(
                AwsLogDriverProps.builder()
                                 .logGroup(logGroup)
                                 .streamPrefix(projectName)
                                 .build());
    }

    private HealthCheck getTaskHealthCheck() {
        return HealthCheck
                .builder()
                .command(Arrays.asList("CMD-SHELL", "exit 0"))
                .interval(Duration.seconds(30))
                .timeout(Duration.seconds(5))
                .startPeriod(Duration.seconds(10))
                .retries(3)
                .build();
    }

    private Role getEcsServiceTaskRole(String projectName, ENV stackEnv, String name, String ssmExecKmsArn) {
        return Role.Builder.create(this, String.format("%s-ECS-TeamCity-%s-Task-Role", projectName, name))
                           .roleName(String.format("%s-ECS-TeamCity-%s-Task-Role-%s", projectName, name, stackEnv))
                           .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                           .managedPolicies(Collections.singletonList(
                                   ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                           ))
                           .inlinePolicies(new HashMap<String, PolicyDocument>() {
                               {
                                   put("ECS-Exec-Command-Policy", Utils.getEcsExecIamPolicy(ssmExecKmsArn));
                               }
                           })
                           .build();
    }

    private Role getEcsServiceExecutionRole(String projectName, ENV stackEnv, String name) {
        return Role.Builder.create(this, String.format("%s-ECS-TeamCity-%s-Execution-Role", projectName, name))
                           .roleName(String.format("%s-ECS-TeamCity-%s-Execution-Role-%s", projectName, name, stackEnv))
                           .assumedBy(new CompositePrincipal(
                                           new ServicePrincipal("ecs-tasks.amazonaws.com")
                                   )
                           )
                           .managedPolicies(Collections.singletonList(
                                   ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                           ))
                           .build();
    }
}