package com.engineermindscape.blog.efs.escapehatch.config;

import java.util.Arrays;
import java.util.List;

public class Config {
    public static final String SSM_VPC_ID = "/vpc/id/";
    public static final String SSM_VPC_NAME = "/vpc/name/";
    public static final String SSM_VPC_ENDPOINT_S3_PREFIX_ID = "/vpc/endpoint/s3/prefix/id/";
    public static final String SSM_VPC_SG_EFS = "/vpc/sg/efs/";
    public static final String SSM_ECS_KMS_EXEC_ARN = "/ecs/kms/exec/arn/";
    public static final String SSM_EFS_ECS_ARN = "/efs/arn/";
    public static final String SSM_EFS_ECS_ID = "/efs/id/";
    public static final String SSM_ECS_NAME = "/ecs/name/";
    public static final String VPC_CIDR = "10.0.0.0/21";
    public static final String VPC_VPN_CIDR = "30.0.0.0/16";
}
