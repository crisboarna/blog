package com.engineermindscape.blog.teamcity.lib;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.props.BaseStackProps;
import com.engineermindscape.blog.teamcity.utils.Utils;
import software.amazon.awscdk.Arn;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.applicationautoscaling.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.servicediscovery.IPrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespaceAttributes;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EcsServicesTeamCityMySQLStack extends Stack {
    public EcsServicesTeamCityMySQLStack(final Construct scope, final String id, final BaseStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;
        String nameMysql = "MySQL";
        String nameVolumeMountEfs = "efs";

        List<String> parametersValues = Utils.getSsmParametersValues(
                this,
                projectName,
                stackEnv,
                Arrays.asList(
                        Config.SSM_ECS_NAME,
                        Config.SSM_ECS_KMS_EXEC_ARN,
                        Config.SSM_EFS_ECS_ID,
                        Config.SSM_EFS_ECS_ARN,
                        Config.SSM_SERVICE_REGISTRY_ARN,
                        Config.SSM_SERVICE_REGISTRY_ID,
                        Config.SSM_SERVICE_REGISTRY_NAME,
                        Config.SSM_VPC_ENDPOINT_S3_PREFIX_ID
                )
        );

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

        IVpc vpc = Utils.getVpc(this, projectName, stackEnv);

        ISecurityGroup sgEcsMysql = Utils.createSecurityGroup(
                this,
                projectName,
                stackEnv,
                vpc,
                "ECS-MySQL",
                false
        );

        List<ISecurityGroup> securityGroups = Stream
                .of(
                        Config.SSM_VPC_SG_ENDPOINT_ECR,
                        Config.SSM_VPC_SG_ENDPOINT_ECR_DKR,
                        Config.SSM_VPC_SG_ENDPOINT_CW_LOGS,
                        Config.SSM_VPC_SG_ENDPOINT_SM,
                        Config.SSM_VPC_SG_ENDPOINT_SSM,
                        Config.SSM_VPC_SG_ENDPOINT_KMS,
                        Config.SSM_VPC_SG_EFS,
                        Config.SSM_VPC_SG_BASTION
                )
                .map(sgParamName -> Utils.getSecurityGroup(this, projectName, stackEnv, sgParamName))
                .collect(Collectors.toList());

        ISecurityGroup sgEcr = securityGroups.get(0);
        ISecurityGroup sgEcrDkr = securityGroups.get(1);
        ISecurityGroup sgCwLogs = securityGroups.get(2);
        ISecurityGroup sgSm = securityGroups.get(3);
        ISecurityGroup sgSSm = securityGroups.get(4);
        ISecurityGroup sgKms = securityGroups.get(5);
        ISecurityGroup sgEfs = securityGroups.get(6);
        ISecurityGroup sgBastion = securityGroups.get(7);

        String ecsClusterName = parametersValues.get(0);
        String ecsSsmKmsExecArn = parametersValues.get(1);
        String ecsEfsId = parametersValues.get(2);
        String ecsEfsArn = parametersValues.get(3);
        String ecsDnsNamespaceArn = parametersValues.get(4);
        String ecsDnsNamespaceId = parametersValues.get(5);
        String ecsDnsNamespaceName = parametersValues.get(6);
        String s3PrefixList = parametersValues.get(7);

        ISecret secretMysqlUser = secrets.get(0);
        ISecret secretMysqlPassword = secrets.get(1);
        ISecret secretMysqlDatabase = secrets.get(2);

        sgEcr.addIngressRule(sgEcsMysql, Port.tcp(443), "Allow access from ECS MySQL to ECR");
        sgEcsMysql.addEgressRule(sgEcr, Port.tcp(443), "Allow access from ECS MySQL to ECR");

        sgEcrDkr.addIngressRule(sgEcsMysql, Port.tcp(443), "Allow access from ECS MySQL to ECR DKR");
        sgEcsMysql.addEgressRule(sgEcrDkr, Port.tcp(443), "Allow access from ECS MySQL to ECR DKR");

        sgCwLogs.addIngressRule(sgEcsMysql, Port.tcp(443), "Allow access from ECS MySQL to ECR DKR");
        sgEcsMysql.addEgressRule(sgCwLogs, Port.tcp(443), "Allow access from ECS MySQL to CW Logs");

        sgSm.addIngressRule(sgEcsMysql, Port.tcp(443), "Allow access from ECS MySQL to SM");
        sgEcsMysql.addEgressRule(sgSm, Port.tcp(443), "Allow access from ECS MySQL to SM");

        sgSSm.addIngressRule(sgEcsMysql, Port.tcp(443), "Allow access from ECS TC Server to SSM");
        sgEcsMysql.addEgressRule(sgSSm, Port.tcp(443), "Allow access from ECS TC Server to SSM");

        sgKms.addIngressRule(sgEcsMysql, Port.tcp(443), "Allow access from ECS TC Server to KMS");
        sgEcsMysql.addEgressRule(sgKms, Port.tcp(443), "Allow access from ECS TC Server to KMS");

        sgEfs.addIngressRule(sgEcsMysql, Port.tcp(2049), "Allow access from ECS MySQL to EFS");
        sgEcsMysql.addEgressRule(sgEfs, Port.tcp(2049), "Allow access from ECS MySQL to EFS");

        sgEcsMysql.addIngressRule(sgBastion, Port.tcp(3306), "Allow access from ECS MySQL to VPN");
        sgBastion.addEgressRule(sgEcsMysql, Port.tcp(3306), "Allow access from ECS MySQL to VPN");

        sgEcsMysql.addEgressRule(Peer.prefixList(s3PrefixList), Port.tcp(443), "Allow access from ECS MySQL to S3");

        IPrivateDnsNamespace dnsNamespace = this.getDnsNameSpace(
                projectName,
                stackEnv,
                ecsDnsNamespaceName,
                ecsDnsNamespaceArn,
                ecsDnsNamespaceId
        );

        ICluster ecsCluster = Utils.getCluster(this, projectName, stackEnv, vpc, ecsClusterName, sgEcsMysql);

        LogGroup logGroupMysql = Utils.createLogGroup(this, String.format("/ecs/fargate/service/%s-Log-%s-%s", projectName, nameMysql, stackEnv));

        Role ecsRoleTask = this.getEcsServiceTaskRole(
                projectName,
                stackEnv,
                nameMysql,
                ecsSsmKmsExecArn,
                ecsEfsArn
        );

        Role ecsRoleExecution = this.getEcsServiceExecutionRole(
                projectName,
                stackEnv,
                nameMysql,
                ecsSsmKmsExecArn
        );

        secrets.forEach(secret -> secret.grantRead(ecsRoleTask));

        IRepository repositoryMysql = Repository.fromRepositoryArn(
                this,
                String.format("%s-ECR-Repository-%s-%s", projectName, nameMysql, stackEnv),
                String.format("arn:aws:ecr:%s:%s:repository/%s", this.getRegion(), this.getAccount(), Config.ECR_NAME_TC_MYSQL)
        );

        String fargateFamilyName = String.format("%s-TeamCity-%s-Task-%s", projectName, nameMysql, stackEnv);
        FargateTaskDefinition taskDefinition = FargateTaskDefinition
                .Builder.create(this, fargateFamilyName)
                        .family(fargateFamilyName)
                        .volumes(Collections.singletonList(
                                        Volume.builder()
                                              .name(nameVolumeMountEfs)
                                              .efsVolumeConfiguration(
                                                      EfsVolumeConfiguration.builder()
                                                                            .fileSystemId(ecsEfsId)
                                                                            .rootDirectory("/ci/mysql/data")
                                                                            .transitEncryption("ENABLED")
                                                                            .transitEncryptionPort(2049)
                                                                            .build()
                                              )
                                              .build()
                                )
                        )
                        .cpu(512)
                        .memoryLimitMiB(1024)
                        .taskRole(ecsRoleTask)
                        .executionRole(ecsRoleExecution)
                        .runtimePlatform(RuntimePlatform.builder()
                                                        .cpuArchitecture(CpuArchitecture.ARM64)
                                                        .operatingSystemFamily(OperatingSystemFamily.LINUX)
                                                        .build()
                        )
                        .build();

        ContainerDefinition serverContainer = taskDefinition.addContainer(String.format("%s-%s-Container-%s", projectName, nameMysql, stackEnv),
                ContainerDefinitionProps
                        .builder()
                        .containerName(nameMysql)
                        .taskDefinition(taskDefinition)
                        .readonlyRootFilesystem(false)
                        .essential(true)
                        .image(ContainerImage.fromEcrRepository(repositoryMysql, "8.1.0"))
                        .logging(Utils.getTaskLoggingConfig(projectName, logGroupMysql))
                        .healthCheck(this.getTaskHealthCheck())
                        .linuxParameters(Utils.getLinuxParameters(this, projectName, stackEnv, nameMysql))
                        .secrets(new HashMap<String, Secret>() {{
                            put("MYSQL_ROOT_PASSWORD", Secret.fromSecretsManager(secretMysqlPassword));
                            put("MYSQL_USER", Secret.fromSecretsManager(secretMysqlUser));
                            put("MYSQL_PASSWORD", Secret.fromSecretsManager(secretMysqlPassword));
                            put("MYSQL_DATABASE", Secret.fromSecretsManager(secretMysqlDatabase));
                        }})
                        .build());

        serverContainer.addMountPoints(MountPoint.builder()
                                                 .containerPath("/var/lib/mysql")
                                                 .readOnly(false)
                                                 .sourceVolume(nameVolumeMountEfs)
                                                 .build());

        FargateService fargateService = FargateService
                .Builder.create(this, String.format("%s-TeamCity-%s-Service-%s", projectName, nameMysql, stackEnv))
                        .serviceName(String.format("%s-TeamCity-%s-%s", projectName, nameMysql, stackEnv))
                        .cluster(ecsCluster)
                        .taskDefinition(taskDefinition)
                        .securityGroups(Collections.singletonList(sgEcsMysql))
                        .enableExecuteCommand(true)
                        .cloudMapOptions(CloudMapOptions
                                .builder()
                                .cloudMapNamespace(dnsNamespace)
                                .name(nameMysql.toLowerCase())
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

        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_VPC_SG_ECS_MYSQL, sgEcsMysql.getSecurityGroupId());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_ECS_MYSQL_URL, nameMysql.toLowerCase().concat(".").concat(ecsDnsNamespaceName));

        Utils.addOutOfHoursDowntime(this, projectName, stackEnv, nameMysql, fargateService);
    }

    private HealthCheck getTaskHealthCheck() {
        return HealthCheck
                .builder()
                .command(Arrays.asList("CMD-SHELL", "mysqladmin ping -h localhost"))
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
                           .managedPolicies(Collections.singletonList(
                                   ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                           ))
                           .inlinePolicies(new HashMap<String, PolicyDocument>() {
                               {
                                   put("EFS", Utils.getEfsMountIamPolicy(efsArn));
                                   put("ECS-Exec-Command-Policy", Utils.getEcsExecIamPolicy(ssmExecKmsArn));
                               }
                           })
                           .build();
    }

    private Role getEcsServiceExecutionRole(String projectName, ENV stackEnv, String name, String ssmExecKmsArn) {
        return Role.Builder.create(this, String.format("%s-ECS-TeamCity-%s-Execution-Role", projectName, name))
                           .roleName(String.format("%s-ECS-TeamCity-%s-Execution-Role-%s", projectName, name, stackEnv))
                           .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                           .managedPolicies(Collections.singletonList(
                                   ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                           ))
                           .build();
    }
}