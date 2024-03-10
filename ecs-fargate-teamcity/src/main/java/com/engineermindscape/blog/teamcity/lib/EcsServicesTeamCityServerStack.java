package com.engineermindscape.blog.teamcity.lib;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.props.BaseStackProps;
import com.engineermindscape.blog.teamcity.utils.Utils;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetGroupAttributes;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.servicediscovery.IPrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespaceAttributes;
import software.amazon.awscdk.services.ssm.IParameter;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EcsServicesTeamCityServerStack extends Stack {
    public EcsServicesTeamCityServerStack(final Construct scope, final String id, final BaseStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;
        String nameServer = "Server";
        String nameVolumeMountEfs = "efs";
        String nameVolumeMountDocker = "docker";

        List<String> parameterNames = Arrays.asList(
                Config.SSM_ECS_NAME,
                Config.SSM_ECS_KMS_EXEC_ARN,
                Config.SSM_EFS_ECS_ID,
                Config.SSM_EFS_ECS_ARN,
                Config.SSM_ALB_ARN,
                Config.SSM_ALB_TG_SERVER,
                Config.SSM_ECS_MYSQL_URL,
                Config.SSM_VPC_ENDPOINT_S3_PREFIX_ID,
                Config.SSM_SERVICE_REGISTRY_ARN,
                Config.SSM_SERVICE_REGISTRY_ID,
                Config.SSM_SERVICE_REGISTRY_NAME,
                Config.SSM_TC_SERVER_DNS_PUBLIC
        );

        List<IStringParameter> parameters = Utils.getSsmParameters(
                this,
                projectName,
                stackEnv,
                parameterNames
        );

        String ecsClusterName = parameters.get(0).getStringValue();
        String ecsSsmKmsExecArn = parameters.get(1).getStringValue();
        String ecsEfsId = parameters.get(2).getStringValue();
        String ecsEfsArn = parameters.get(3).getStringValue();
        String albArn = parameters.get(4).getStringValue();
        String albTgArnServer = parameters.get(5).getStringValue();
        IParameter paramMysqlUrl = parameters.get(6);
        String s3PrefixList = parameters.get(7).getStringValue();
        String ecsDnsNamespaceArn = parameters.get(8).getStringValue();
        String ecsDnsNamespaceId = parameters.get(9).getStringValue();
        String ecsDnsNamespaceName = parameters.get(10).getStringValue();
        IParameter ssmTcServerDnsPublic = parameters.get(11);

        List<ISecret> secrets = Utils.getSecrets(
                this,
                projectName,
                stackEnv,
                Arrays.asList(
                        Config.SECRET_MYSQL_USER,
                        Config.SECRET_MYSQL_PASSWORD,
                        Config.SECRET_MYSQL_DATABASE
                )
        );

        ISecret secretMysqlUser = secrets.get(0);
        ISecret secretMysqlPassword = secrets.get(1);
        ISecret secretMysqlDatabase = secrets.get(2);

        IVpc vpc = Utils.getVpc(this, projectName, stackEnv);

        ISecurityGroup sgEcsTcServer = Utils.createSecurityGroup(
                this,
                projectName,
                stackEnv,
                vpc,
                "ECS-TeamCity-Server",
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
                        Config.SSM_VPC_SG_EFS,
                        Config.SSM_VPC_SG_ALB,
                        Config.SSM_VPC_SG_ECS_MYSQL,
                        Config.SSM_VPC_SG_BASTION
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
        ISecurityGroup sgEfs = securityGroups.get(7);
        ISecurityGroup sgAlb = securityGroups.get(8);
        ISecurityGroup sgEcsMysql = securityGroups.get(9);
        ISecurityGroup sgBastion = securityGroups.get(10);

        sgEcs.addIngressRule(sgEcsTcServer, Port.tcp(443), "Allow access from ECS TC Server to ECS");
        sgEcsTcServer.addEgressRule(sgEcs, Port.tcp(443), "Allow access from ECS TC Server to ECS");

        sgEcr.addIngressRule(sgEcsTcServer, Port.tcp(443), "Allow access from ECS TC Server to ECR");
        sgEcsTcServer.addEgressRule(sgEcr, Port.tcp(443), "Allow access from ECS TC Server to ECR");

        sgEcrDkr.addIngressRule(sgEcsTcServer, Port.tcp(443), "Allow access from ECS TC Server to ECR DKR");
        sgEcsTcServer.addEgressRule(sgEcrDkr, Port.tcp(443), "Allow access from ECS TC Server to ECR DKR");

        sgCwLogs.addIngressRule(sgEcsTcServer, Port.tcp(443), "Allow access from ECS TC Server to ECR DKR");
        sgEcsTcServer.addEgressRule(sgCwLogs, Port.tcp(443), "Allow access from ECS TC Server to CW Logs");

        sgSm.addIngressRule(sgEcsTcServer, Port.tcp(443), "Allow access from ECS TC Server to SM");
        sgEcsTcServer.addEgressRule(sgSm, Port.tcp(443), "Allow access from ECS TC Server to SM");

        sgSSm.addIngressRule(sgEcsTcServer, Port.tcp(443), "Allow access from ECS TC Server to SSM");
        sgEcsTcServer.addEgressRule(sgSSm, Port.tcp(443), "Allow access from ECS TC Server to SSM");

        sgKms.addIngressRule(sgEcsTcServer, Port.tcp(443), "Allow access from ECS TC Server to KMS");
        sgEcsTcServer.addEgressRule(sgKms, Port.tcp(443), "Allow access from ECS TC Server to KMS");

        sgBastion.addIngressRule(sgEcsTcServer, Port.tcp(443), "Allow access from ECS TC Server to Bastion");
        sgEcsTcServer.addEgressRule(sgBastion, Port.tcp(443), "Allow access from ECS TC Server to Bastion");

        sgEfs.addIngressRule(sgEcsTcServer, Port.tcp(2049), "Allow access from ECS TC Server to EFS");
        sgEcsTcServer.addEgressRule(sgEfs, Port.tcp(2049), "Allow access from ECS TC Server to EFS");

        sgEcsMysql.addIngressRule(sgEcsTcServer, Port.tcp(3306), "Allow access from ECS TC Server to ECS MySQL");
        sgEcsTcServer.addEgressRule(sgEcsMysql, Port.tcp(3306), "Allow access from ECS TC Server to ECS MySQL");

        sgEcsTcServer.addIngressRule(sgAlb, Port.tcp(8111), "Allow access from ALB to ECS TC Server");
        sgAlb.addEgressRule(sgEcsTcServer, Port.tcp(8111), "Allow access from ALB to ECS TC Server ");

        Config.GH_GIT_IP_WHITELIST.forEach(ip -> sgEcsTcServer.addEgressRule(Peer.ipv4(ip), Port.tcp(443), "Allow access from ECS TC to GitHub Git"));

        sgEcsTcServer.addEgressRule(Peer.prefixList(s3PrefixList), Port.tcp(443), "Allow access from ECS MySQL to S3");

        IPrivateDnsNamespace dnsNamespace = this.getDnsNameSpace(
                projectName,
                stackEnv,
                ecsDnsNamespaceName,
                ecsDnsNamespaceArn,
                ecsDnsNamespaceId
        );

        ICluster ecsCluster = Utils.getCluster(this, projectName, stackEnv, vpc, ecsClusterName, sgEcsTcServer);

        LogGroup logGroupServer = Utils.createLogGroup(this, String.format("/ecs/fargate/service/%s-Log-%s-%s", projectName, nameServer, stackEnv));

        Role ecsRoleTask = this.getEcsServiceTaskRole(
                projectName,
                stackEnv,
                nameServer,
                ecsSsmKmsExecArn,
                ecsEfsArn
        );

        Role ecsRoleExecution = this.getEcsServiceExecutionRole(
                projectName,
                stackEnv,
                nameServer
        );

        secrets.forEach(secret -> secret.grantRead(ecsRoleTask));
        parameters.forEach(parameter -> parameter.grantRead(ecsRoleTask));

        IRepository repositoryServer = Repository.fromRepositoryArn(
                this,
                String.format("%s-ECR-Repository-%s-%s", projectName, nameServer, stackEnv),
                String.format("arn:aws:ecr:%s:%s:repository/%s", this.getRegion(), this.getAccount(), Config.ECR_NAME_TC_SERVER)
        );

        IApplicationTargetGroup tgTcServer = ApplicationTargetGroup.fromTargetGroupAttributes(
                this,
                projectName + "-ECS-ALB-TG-TC-Server-Imp-" + stackEnv,
                TargetGroupAttributes
                        .builder()
                        .targetGroupArn(albTgArnServer)
                        .loadBalancerArns(albArn)
                        .build()
        );

        String fargateFamilyName = String.format("%s-TeamCity-%s-Task-%s", projectName, nameServer, stackEnv);
        FargateTaskDefinition taskDefinition = FargateTaskDefinition
                .Builder.create(this, fargateFamilyName)
                        .family(fargateFamilyName)
                        .volumes(Arrays.asList(
                                        Volume.builder()
                                              .name(nameVolumeMountEfs)
                                              .efsVolumeConfiguration(
                                                      EfsVolumeConfiguration.builder()
                                                                            .fileSystemId(ecsEfsId)
                                                                            .rootDirectory("/ci/teamcity/data")
                                                                            .transitEncryption("ENABLED")
                                                                            .transitEncryptionPort(2049)
                                                                            .build()
                                              )
                                              .build(),
                                        Volume.builder()
                                              .name(nameVolumeMountDocker)
                                              .build()
                                )
                        )
                        .cpu(2048)
                        .memoryLimitMiB(4096)
                        .taskRole(ecsRoleTask)
                        .executionRole(ecsRoleExecution)
                        .runtimePlatform(RuntimePlatform.builder()
                                                        .cpuArchitecture(CpuArchitecture.ARM64)
                                                        .operatingSystemFamily(OperatingSystemFamily.LINUX)
                                                        .build()
                        )
                        .build();

        ContainerDefinition serverContainer = taskDefinition.addContainer(String.format("%s-%s-Container-%s", projectName, nameServer, stackEnv),
                ContainerDefinitionProps
                        .builder()
                        .containerName(nameServer)
                        .taskDefinition(taskDefinition)
                        .readonlyRootFilesystem(false)
                        .essential(true)
                        .portMappings(Collections.singletonList(
                                PortMapping
                                        .builder()
                                        .containerPort(8111)
                                        .protocol(Protocol.TCP)
                                        .build()
                        ))
                        .image(ContainerImage.fromEcrRepository(repositoryServer, "2023.11.4"))
                        .logging(Utils.getTaskLoggingConfig(projectName, logGroupServer))
                        .healthCheck(this.getTaskHealthCheck())
                        .linuxParameters(Utils.getLinuxParameters(this, projectName, stackEnv, nameServer))
                        .environment(new HashMap<String, String>() {{
                            put("MYSQL_PORT", "3306");
                        }})
                        .secrets(new HashMap<String, Secret>() {{
                            put("MYSQL_HOST", Secret.fromSsmParameter(paramMysqlUrl));
                            put("MYSQL_USER", Secret.fromSecretsManager(secretMysqlUser));
                            put("MYSQL_PASSWORD", Secret.fromSecretsManager(secretMysqlPassword));
                            put("MYSQL_DATABASE", Secret.fromSecretsManager(secretMysqlDatabase));
                            put("TEAMCITY_PUBLIC_URL", Secret.fromSsmParameter(ssmTcServerDnsPublic));
                        }})
                        .build());

        serverContainer.addMountPoints(
                MountPoint.builder()
                          .containerPath("/data/teamcity_server/datadir")
                          .readOnly(false)
                          .sourceVolume(nameVolumeMountEfs)
                          .build(),
                MountPoint.builder()
                          .containerPath("/data/teamcity_server/datadir/system/caches")
                          .readOnly(false)
                          .sourceVolume(nameVolumeMountDocker)
                          .build());

        FargateService fargateService = FargateService
                .Builder.create(this, String.format("%s-TeamCity-%s-Service-%s", projectName, nameServer, stackEnv))
                        .serviceName(String.format("%s-TeamCity-%s-%s", projectName, nameServer, stackEnv))
                        .cluster(ecsCluster)
                        .taskDefinition(taskDefinition)
                        .securityGroups(Collections.singletonList(sgEcsTcServer))
                        .enableExecuteCommand(true)
                        .minHealthyPercent(0)
                        .cloudMapOptions(CloudMapOptions
                                .builder()
                                .cloudMapNamespace(dnsNamespace)
                                .name("tc")
                                .build())
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

        tgTcServer.addTarget(fargateService);

        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_VPC_SG_ECS_TC_SERVER, sgEcsTcServer.getSecurityGroupId());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_ECS_SERVER_URL, "tc.".concat(ecsDnsNamespaceName) + ":8111");

        Utils.addOutOfHoursDowntime(this, projectName, stackEnv, nameServer, fargateService);
    }

    private HealthCheck getTaskHealthCheck() {
        return HealthCheck
                .builder()
//            .command(Arrays.asList("CMD-SHELL", "wget --spider -q http://localhost:8111/healthCheck/healthy"))
                .command(Arrays.asList("CMD-SHELL", "exit 0"))
                .interval(Duration.seconds(30))
                .timeout(Duration.seconds(5))
                .startPeriod(Duration.seconds(30))
                .retries(3)
                .build();
    }

    private IPrivateDnsNamespace getDnsNameSpace(String projectName, ENV stackEnv, String dnsNamespaceName, String dnsNamespaceArn, String dnsNamespaceId) {
        return PrivateDnsNamespace.fromPrivateDnsNamespaceAttributes(this, projectName + "-ECS-Private-DNS-Namespace-Import-" + stackEnv,
                PrivateDnsNamespaceAttributes
                        .builder()
                        .namespaceName(dnsNamespaceName)
                        .namespaceArn(dnsNamespaceArn)
                        .namespaceId(dnsNamespaceId)
                        .build());
    }

    private Role getEcsServiceTaskRole(String projectName, ENV stackEnv, String name, String ssmExecKmsArn, String efsArn) {
        return Role.Builder.create(this, String.format("%s-ECS-TeamCity-%s-Task-Role", projectName, name))
                           .roleName(String.format("%s-ECS-TeamCity-%s-Task-Role-%s", projectName, name, stackEnv))
                           .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                           .managedPolicies(Arrays.asList(
                                   ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy"),
                                   ManagedPolicy.fromAwsManagedPolicyName("AdministratorAccess")
                           ))
                           .inlinePolicies(new HashMap<String, PolicyDocument>() {
                               {
                                   put("EFS", Utils.getEfsMountIamPolicy(efsArn));
                                   put("ECS-Exec-Command-Policy", Utils.getEcsExecIamPolicy(ssmExecKmsArn));
                               }
                           })
                           .build();
    }

    private Role getEcsServiceExecutionRole(String projectName, ENV stackEnv, String name) {
        return Role.Builder.create(this, String.format("%s-ECS-TeamCity-%s-Execution-Role", projectName, name))
                           .roleName(String.format("%s-ECS-TeamCity-%s-Execution-Role-%s", projectName, name, stackEnv))
                           .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                           .managedPolicies(Arrays.asList(
                                   ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                           ))
                           .build();
    }
}