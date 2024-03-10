package com.engineermindscape.blog.teamcity.config;

import java.util.Arrays;
import java.util.List;

public class Config {
    public static final String SSM_EIP_IP_1 = "/eip/ip/1/";
    public static final String SSM_EIP_IP_2 = "/eip/ip/2/";
    public static final String SSM_VPC_ID = "/vpc/id/";
    public static final String SSM_VPC_NAME = "/vpc/name/";
    public static final String SSM_VPC_ENDPOINT_S3_PREFIX_ID = "/vpc/endpoint/s3/prefix/id/";
    public static final String SSM_VPC_SG_BASTION = "/vpc/sg/bastion/";
    public static final String SSM_VPC_SG_ENDPOINT_ECS = "/vpc/sg/endpoint/ecs/";
    public static final String SSM_VPC_SG_ENDPOINT_ECR = "/vpc/sg/endpoint/ecr/";
    public static final String SSM_VPC_SG_ENDPOINT_ECR_DKR = "/vpc/sg/endpoint/ecr/dkr/";
    public static final String SSM_VPC_SG_ENDPOINT_CW_LOGS = "/vpc/sg/endpoint/cw/logs/";
    public static final String SSM_VPC_SG_ENDPOINT_SM = "/vpc/sg/endpoint/secrets/manager/";
    public static final String SSM_VPC_SG_ENDPOINT_SSM = "/vpc/sg/endpoint/ssm/";
    public static final String SSM_VPC_SG_ENDPOINT_KMS = "/vpc/sg/endpoint/kms/";
    public static final String SSM_VPC_SG_ENDPOINT_CODEARTIFACT = "/vpc/sg/endpoint/codeartifact/";
    public static final String SSM_VPC_SG_ENDPOINT_STS = "/vpc/sg/endpoint/sts/";
    public static final String SSM_VPC_SG_ALB = "/vpc/sg/alb/";
    public static final String SSM_VPC_SG_EFS = "/vpc/sg/efs/";
    public static final String SSM_VPC_SG_ECS_MYSQL = "/vpc/sg/ecs/mysql/";
    public static final String SSM_VPC_SG_ECS_TC_SERVER = "/vpc/sg/ecs/tc/server/";
    public static final String SSM_VPC_SG_ECS_TC_MYSQL = "/vpc/sg/ecs/tc/agent/";
    public static final String SSM_ECS_KMS_EXEC_ARN = "/ecs/kms/exec/arn/";
    public static final String SSM_EFS_ECS_ARN = "/efs/arn/";
    public static final String SSM_EFS_ECS_ID = "/efs/id/";
    public static final String SSM_ECS_NAME = "/ecs/name/";
    public static final String SSM_ALB_DNS = "/alb/dns/";
    public static final String SSM_ALB_ARN = "/alb/arn/";
    public static final String SSM_ALB_TG_SERVER = "/alb/tg/server/";
    public static final String SSM_ECS_MYSQL_URL = "/ecs/mysql/url/";
    public static final String SSM_ECS_SERVER_URL = "/ecs/server/url/";
    public static final String SSM_ECS_ALB_IP = "/ecs/alb/ip/";
    public static final String SSM_TC_AGENT_SERVER_URL = "/agent/server/url/";
    public static final String SSM_SERVICE_REGISTRY_ARN = "/ecs/dns/namespace/arn/";
    public static final String SSM_SERVICE_REGISTRY_ID = "/ecs/dns/namespace/id/";
    public static final String SSM_SERVICE_REGISTRY_NAME = "/ecs/dns/namespace/name/";
    public static final String SSM_TC_SERVER_DNS_PUBLIC = "/teamcity/dns/";

    public static final String SECRET_MYSQL_USER = "/mysql/user/";
    public static final String SECRET_MYSQL_PASSWORD = "/mysql/password/";
    public static final String SECRET_MYSQL_DATABASE = "/mysql/database/";

    public static final String VPC_CIDR = "10.0.0.0/21";
    public static final String VPC_VPN_CIDR = "30.0.0.0/16";

    public static final String ECR_NAME_TC_MYSQL = "ci-mysql";
    public static final String ECR_NAME_TC_SERVER = "ci-server";
    public static final String ECR_NAME_TC_AGENT = "ci-agent";
    public static final String ECR_NAME_TC_KANIKO = "ci-agent-kaniko";
    public static final String ECR_NAME_TC_EFS_UTIL = "ci-efs-util";
    public static final String DNS_SSL_CERTIFICATE_ID = "e5e4d02a-da21-4d5f-bd86-4ef26d3103da";
    public static final String DNS_HOSTNAME = "smartlink-teamcity.collinsontech.com";
    public static final String CODEARTIFACT_DOMAIN_NAME = "valuedynamx";
    public static final String CODEARTIFACT_REPO_MAVEN_CENTRAL_NAME = "maven-central-store";
    public static final String CODEARTIFACT_REPO_NPM_NAME = "npm-store";
    public static final String CODEARTIFACT_REPO_MAVEN_RELEASES_NAME = "releases";
    public static final String CODEARTIFACT_REPO_MAVEN_SNAPSHOTS_NAME = "snapshots";
    public static final List<String> WAF_IP_WHITELIST = List.of();
    public static final List<String> ACCOUNT_IDS = List.of();
    public static final List<String> ALB_PUBLIC_IPS = Arrays.asList(
            "52.7.7.255/32",
            "3.217.114.95/32"
    );
    public static List<String> GH_GIT_IP_WHITELIST = Arrays.asList(
            "192.30.252.0/22",
            "185.199.108.0/22",
            "140.82.112.0/20",
            "143.55.64.0/20",
            "20.201.28.151/32",
            "20.205.243.166/32",
            "20.87.245.0/32",
            "20.248.137.48/32",
            "20.207.73.82/32",
            "20.27.177.113/32",
            "20.200.245.247/32",
            "20.175.192.147/32",
            "20.233.83.145/32",
            "20.29.134.23/32",
            "20.201.28.152/32",
            "20.205.243.160/32",
            "20.87.245.4/32",
            "20.248.137.50/32",
            "20.207.73.83/32",
            "20.27.177.118/32",
            "20.200.245.248/32",
            "20.175.192.146/32",
            "20.233.83.149/32",
            "20.29.134.19/32"
    );
}
