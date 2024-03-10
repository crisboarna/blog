package com.engineermindscape.blog.teamcity.lib;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.props.BaseStackProps;
import com.engineermindscape.blog.teamcity.utils.Utils;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EcsServicesTeamCityAgentStack extends Stack {
    public EcsServicesTeamCityAgentStack(final Construct scope, final String id, final BaseStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;
        String nameAgent = "Agent";
        String nameKaniko = "Kaniko";
        String sharedVolumeNameKaniko = "shared_kaniko";
        String sharedVolumeNameWorkspace = "shared_workspace";

        List<IStringParameter> parameters = Utils.getSsmParameters(
                this,
                projectName,
                stackEnv,
                Arrays.asList(
                        Config.SSM_ECS_KMS_EXEC_ARN,
                        Config.SSM_VPC_ENDPOINT_S3_PREFIX_ID
                )
        );

        IVpc vpc = Utils.getVpc(this, projectName, stackEnv);

        ISecurityGroup sgEcsTcAgent = Utils.createSecurityGroup(
                this,
                projectName,
                stackEnv,
                vpc,
                "ECS-TC-Agent",
                false
        );

        List<ISecurityGroup> securityGroups = Stream
                .of(
                        Config.SSM_VPC_SG_ENDPOINT_ECS,
                        Config.SSM_VPC_SG_ENDPOINT_ECR,
                        Config.SSM_VPC_SG_ENDPOINT_ECR_DKR,
                        Config.SSM_VPC_SG_ENDPOINT_CW_LOGS,
                        Config.SSM_VPC_SG_ENDPOINT_SM,
                        Config.SSM_VPC_SG_ENDPOINT_SSM,
                        Config.SSM_VPC_SG_ENDPOINT_KMS,
                        Config.SSM_VPC_SG_ENDPOINT_CODEARTIFACT,
                        Config.SSM_VPC_SG_ENDPOINT_STS,
                        Config.SSM_VPC_SG_BASTION,
                        Config.SSM_VPC_SG_ECS_TC_SERVER
                )
                .map(sgParamName -> Utils.getSecurityGroup(this, projectName, stackEnv, sgParamName))
                .collect(Collectors.toList());

        ISecurityGroup sgEcs = securityGroups.get(0);
        ISecurityGroup sgEcr = securityGroups.get(1);
        ISecurityGroup sgEcrDkr = securityGroups.get(2);
        ISecurityGroup sgCwLogs = securityGroups.get(3);
        ISecurityGroup sgSm = securityGroups.get(4);
        ISecurityGroup sgSSm = securityGroups.get(5);
        ISecurityGroup sgKms = securityGroups.get(6);
        ISecurityGroup sgCodeArtifact = securityGroups.get(7);
        ISecurityGroup sgSts = securityGroups.get(8);
        ISecurityGroup sgBastion = securityGroups.get(9);
        ISecurityGroup sgTcServer = securityGroups.get(10);

        String ecsSsmKmsExecArn = parameters.get(0).getStringValue();
        String s3PrefixList = parameters.get(1).getStringValue();

        sgEcs.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to ECS");
        sgEcsTcAgent.addEgressRule(sgEcs, Port.tcp(443), "Allow access from ECS TC Agent to ECS");

        sgEcr.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to ECR");
        sgEcsTcAgent.addEgressRule(sgEcr, Port.tcp(443), "Allow access from ECS TC Agent to ECR");

        sgEcrDkr.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to ECR DKR");
        sgEcsTcAgent.addEgressRule(sgEcrDkr, Port.tcp(443), "Allow access from ECS TC Agent to ECR DKR");

        sgCwLogs.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to ECR DKR");
        sgEcsTcAgent.addEgressRule(sgCwLogs, Port.tcp(443), "Allow access from ECS TC Agent to CW Logs");

        sgSm.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to SM");
        sgEcsTcAgent.addEgressRule(sgSm, Port.tcp(443), "Allow access from ECS TC Agent to SM");

        sgSSm.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to SSM");
        sgEcsTcAgent.addEgressRule(sgSSm, Port.tcp(443), "Allow access from ECS TC Agent to SSM");

        sgKms.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to KMS");
        sgEcsTcAgent.addEgressRule(sgKms, Port.tcp(443), "Allow access from ECS TC Agent to KMS");

        sgCodeArtifact.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to CodeArtifact");
        sgEcsTcAgent.addEgressRule(sgCodeArtifact, Port.tcp(443), "Allow access from ECS TC Agent to CodeArtifact");

        sgSts.addIngressRule(sgEcsTcAgent, Port.tcp(443), "Allow access from ECS TC Agent to STS");
        sgEcsTcAgent.addEgressRule(sgSts, Port.tcp(443), "Allow access from ECS TC Agent to STS");

        sgEcsTcAgent.addIngressRule(sgBastion, Port.tcp(9090), "Allow access from ECS TC Agent to VPN");
        sgBastion.addEgressRule(sgEcsTcAgent, Port.tcp(9090), "Allow access from ECS TC Agent to VPN");

        sgEcsTcAgent.addEgressRule(Peer.prefixList(s3PrefixList), Port.tcp(443), "Allow access from ECS TC Agent to S3");

        Config.GH_GIT_IP_WHITELIST.forEach(ip -> sgEcsTcAgent.addEgressRule(Peer.ipv4(ip), Port.tcp(443), "Allow access from ECS TC Agent to GitHub Git"));
        Config.ALB_PUBLIC_IPS.forEach(ip -> sgEcsTcAgent.addEgressRule(Peer.ipv4(ip), Port.tcp(443), "Allow access from ECS TC Agent to TC Server"));

        sgEcsTcAgent.addIngressRule(sgTcServer, Port.tcp(9090), "Allow access from ECS TC Agent to TC Server");
        sgTcServer.addEgressRule(sgEcsTcAgent, Port.tcp(9090), "Allow access from ECS TC Agent to TC Server");

        LogGroup logGroupTcAgent = Utils.createLogGroup(this, String.format("/ecs/fargate/service/%s-Log-%s-%s", projectName, nameAgent, stackEnv));
        LogGroup logGroupTcAgentKaniko = Utils.createLogGroup(this, String.format("/ecs/fargate/service/%s-Log-%s-%s", projectName, nameKaniko, stackEnv));

        Role ecsRoleTask = this.getEcsServiceTaskRole(
                projectName,
                stackEnv,
                nameAgent,
                ecsSsmKmsExecArn
        );

        Role ecsRoleExecution = this.getEcsServiceExecutionRole(
                projectName,
                stackEnv,
                nameAgent
        );

        parameters.forEach(parameter -> parameter.grantRead(ecsRoleTask));

        IRepository repositoryAgent = Repository.fromRepositoryArn(
                this,
                String.format("%s-ECR-Repository-%s-%s", projectName, nameAgent, stackEnv),
                String.format("arn:aws:ecr:%s:%s:repository/%s", this.getRegion(), this.getAccount(), Config.ECR_NAME_TC_AGENT)
        );

        IRepository repositoryKaniko = Repository.fromRepositoryArn(
                this,
                String.format("%s-ECR-Repository-%s-%s", projectName, nameKaniko, stackEnv),
                String.format("arn:aws:ecr:%s:%s:repository/%s", this.getRegion(), this.getAccount(), Config.ECR_NAME_TC_KANIKO)
        );

        String fargateFamilyName = String.format("%s-TeamCity-%s-Task-%s", projectName, nameAgent, stackEnv);
        FargateTaskDefinition taskDefinition = FargateTaskDefinition
                .Builder.create(this, fargateFamilyName)
                        .family(fargateFamilyName)
                        .volumes(Arrays.asList(
                                        Volume.builder()
                                              .name(sharedVolumeNameKaniko)
                                              .build(),
                                        Volume.builder()
                                              .name(sharedVolumeNameWorkspace)
                                              .build()
                                )
                        )
                        .cpu(4096)
                        .memoryLimitMiB(8192)
                        .taskRole(ecsRoleTask)
                        .executionRole(ecsRoleExecution)
                        .runtimePlatform(RuntimePlatform.builder()
                                                        .cpuArchitecture(CpuArchitecture.ARM64)
                                                        .operatingSystemFamily(OperatingSystemFamily.LINUX)
                                                        .build()
                        )
                        .build();

        ContainerDefinition agentContainer = taskDefinition.addContainer(String.format("%s-%s-Container-%s", projectName, nameAgent, stackEnv),
                ContainerDefinitionProps
                        .builder()
                        .containerName(nameAgent)
                        .taskDefinition(taskDefinition)
                        .readonlyRootFilesystem(false)
                        .essential(true)
                        .portMappings(Collections.singletonList(PortMapping.builder()
                                                                           .containerPort(9090)
                                                                           .protocol(Protocol.TCP)
                                                                           .build()))
                        .image(ContainerImage.fromEcrRepository(repositoryAgent, "2023.11.4"))
                        .logging(this.getTaskLoggingConfig(projectName, logGroupTcAgent))
                        .healthCheck(this.getTaskHealthCheck())
                        .linuxParameters(Utils.getLinuxParameters(this, projectName, stackEnv, nameAgent))
                        .build());

        ContainerDefinition kanikoContainer = taskDefinition.addContainer(String.format("%s-%s-Container-%s", projectName, nameKaniko, stackEnv),
                ContainerDefinitionProps
                        .builder()
                        .containerName(nameKaniko)
                        .taskDefinition(taskDefinition)
                        .readonlyRootFilesystem(false)
                        .essential(true)
                        .image(ContainerImage.fromEcrRepository(repositoryKaniko, "1.15.0"))
                        .logging(this.getTaskLoggingConfig(projectName, logGroupTcAgentKaniko))
                        .healthCheck(this.getTaskHealthCheck())
                        .linuxParameters(Utils.getLinuxParameters(this, projectName, stackEnv, nameKaniko))
                        .build());

        agentContainer.addMountPoints(MountPoint.builder()
                                                .containerPath("/kaniko/agent")
                                                .readOnly(false)
                                                .sourceVolume(sharedVolumeNameKaniko)
                                                .build());

        kanikoContainer.addMountPoints(MountPoint.builder()
                                                 .containerPath("/kaniko/agent")
                                                 .readOnly(false)
                                                 .sourceVolume(sharedVolumeNameKaniko)
                                                 .build());

        agentContainer.addMountPoints(MountPoint.builder()
                                                .containerPath("/workspace")
                                                .readOnly(false)
                                                .sourceVolume(sharedVolumeNameWorkspace)
                                                .build());

        kanikoContainer.addMountPoints(MountPoint.builder()
                                                 .containerPath("/workspace")
                                                 .readOnly(false)
                                                 .sourceVolume(sharedVolumeNameWorkspace)
                                                 .build());
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
        Stack scope = this;
        return Role.Builder.create(this, String.format("%s-ECS-TeamCity-%s-Task-Role", projectName, name))
                           .roleName(String.format("%s-ECS-TeamCity-%s-Task-Role-%s", projectName, name, stackEnv))
                           .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                           .managedPolicies(Collections.singletonList(
                                   ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                           ))
                           .inlinePolicies(new HashMap<String, PolicyDocument>() {
                               {
                                   put("ECS-Exec-Command-Policy", Utils.getEcsExecIamPolicy(ssmExecKmsArn));
                                   put("CodeArtifact",
                                           PolicyDocument
                                                   .Builder.create()
                                                           .statements(Arrays.asList(
                                                                   new PolicyStatement(
                                                                           PolicyStatementProps.builder()
                                                                                               .sid("CodeArtifactDomainPermissions")
                                                                                               .effect(Effect.ALLOW)
                                                                                               .actions(Arrays.asList(
                                                                                                       "codeartifact:GetAuthorizationToken",
                                                                                                       "codeartifact:GetDomainPermissionsPolicy"
                                                                                               ))
                                                                                               .resources(Collections.singletonList("arn:aws:codeartifact:" + scope.getRegion() + ":" + scope.getAccount() + ":domain/valuedynamx"))
                                                                                               .build()),
                                                                   new PolicyStatement(
                                                                           PolicyStatementProps.builder()
                                                                                               .sid("CodeArtifactRepoPermissions")
                                                                                               .effect(Effect.ALLOW)
                                                                                               .actions(Arrays.asList(
                                                                                                       "codeartifact:GetRepositoryEndpoint",
                                                                                                       "codeartifact:ReadFromRepository"
                                                                                               ))
                                                                                               .resources(Collections.singletonList("arn:aws:codeartifact:" + scope.getRegion() + ":" + scope.getAccount() + ":repository/valuedynamx/npm-store"))
                                                                                               .build())))
                                                           .build());
                                   put("STS",
                                           PolicyDocument
                                                   .Builder.create()
                                                           .statements(Arrays.asList(
                                                                   new PolicyStatement(
                                                                           PolicyStatementProps.builder()
                                                                                               .sid("STSService")
                                                                                               .effect(Effect.ALLOW)
                                                                                               .actions(Collections.singletonList("sts:GetServiceBearerToken"))
                                                                                               .resources(Collections.singletonList("*"))
                                                                                               .build()),
                                                                   new PolicyStatement(
                                                                           PolicyStatementProps.builder()
                                                                                               .sid("STSAssume")
                                                                                               .effect(Effect.ALLOW)
                                                                                               .actions(Collections.singletonList("sts:AssumeRole"))
                                                                                               .resources(Config.ACCOUNT_IDS.stream().map(accountId -> "arn:aws:iam::" + accountId + ":role/" + projectName + "-Cross-Account-CI-Role-*").collect(Collectors.toList()))
                                                                                               .build())
                                                           ))
                                                           .build());
                               }
                           })
                           .build();
    }

    private Role getEcsServiceExecutionRole(String projectName, ENV stackEnv, String name) {
        return Role.Builder.create(this, String.format("%s-ECS-TeamCity-%s-Execution-Role", projectName, name))
                           .roleName(String.format("%s-ECS-TeamCity-%s-Execution-Role-%s", projectName, name, stackEnv))
                           .assumedBy(new CompositePrincipal(
                                           new ServicePrincipal("ecs-tasks.amazonaws.com"),
                                           new ArnPrincipal("arn:aws:iam::" + this.getAccount() + ":user/cristian.boarna")
                                   )
                           )
                           .managedPolicies(Collections.singletonList(
                                   ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                           ))
                           .inlinePolicies(new HashMap<String, PolicyDocument>() {{

                           }})
                           .build();
    }
}