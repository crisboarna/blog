package com.engineermindscape.blog.teamcity.lib;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.props.BaseStackProps;
import com.engineermindscape.blog.teamcity.utils.Utils;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ExecuteCommandConfiguration;
import software.amazon.awscdk.services.ecs.ExecuteCommandLogConfiguration;
import software.amazon.awscdk.services.ecs.ExecuteCommandLogging;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.iam.ArnPrincipal;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespaceProps;
import software.amazon.awscdk.services.wafv2.*;
import software.constructs.Construct;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EcsStack extends Stack {
    public EcsStack(final Construct scope, final String id, final BaseStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;

        IVpc vpc = Utils.getVpc(this, projectName, stackEnv);

        List<String> eipIps = Utils.getSsmParametersValues(
                this,
                projectName,
                stackEnv,
                Arrays.asList(
                        Config.SSM_EIP_IP_1,
                        Config.SSM_EIP_IP_2
                )
        );

        SecurityGroup albSG = Utils.createSecurityGroup(this, projectName, stackEnv, vpc, "ALB", false);
        ISecurityGroup bastionSG = Utils.getSecurityGroup(this, projectName, stackEnv, Config.SSM_VPC_SG_BASTION);

        albSG.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "Allow access from WAN to ALB");
        albSG.addIngressRule(Peer.anyIpv4(), Port.tcp(443), "Allow access from WAN to ALB");

        albSG.addIngressRule(bastionSG, Port.tcp(80), "Allow access from ALB to ECS");
        bastionSG.addEgressRule(albSG, Port.tcp(80), "Allow access from ALB to ECS");

        Key kmsKey = this.createKmsKey(projectName + "-KMS-Key-Execute-Command-" + stackEnv);

        Cluster cluster = Cluster.Builder.create(this, projectName + "-ECS-Cluster-" + stackEnv)
                                         .vpc(vpc)
                                         .containerInsights(true)
                                         .enableFargateCapacityProviders(true)
                                         .executeCommandConfiguration(
                                                 ExecuteCommandConfiguration
                                                         .builder()
                                                         .kmsKey(kmsKey)
                                                         .logging(ExecuteCommandLogging.OVERRIDE)
                                                         .logConfiguration(ExecuteCommandLogConfiguration
                                                                 .builder()
                                                                 .cloudWatchEncryptionEnabled(false)
                                                                 .cloudWatchLogGroup(
                                                                         this.createLogGroup(projectName + "-ECS-Execute-Command-Log-Group-" + stackEnv))
                                                                 .build())
                                                         .build())
                                         .clusterName(projectName + "-ECS-" + stackEnv)
                                         .build();

        ApplicationLoadBalancer alb = new ApplicationLoadBalancer(this, projectName + "-ALB-" + stackEnv,
                ApplicationLoadBalancerProps
                        .builder()
                        .loadBalancerName(projectName + "-ALB-" + stackEnv)
                        .internetFacing(true)
                        .http2Enabled(true)
                        .deletionProtection(false)
                        .vpc(vpc)
                        .securityGroup(albSG)
                        .build()
        );

        ApplicationTargetGroup tgTcServer = new ApplicationTargetGroup(this, String.format("%s-%s-%s", projectName, "TeamCity-Server", stackEnv),
                ApplicationTargetGroupProps
                        .builder()
                        .targetGroupName(String.format("%s-%s-%s", projectName, "TC-Server", stackEnv))
                        .port(8111)
                        .vpc(vpc)
                        .protocol(ApplicationProtocol.HTTP)
                        .targetType(TargetType.IP)
                        .healthCheck(HealthCheck
                                .builder()
                                .path("/healthCheck/healthy")
                                .healthyHttpCodes("200-299")
                                .build())
                        .build()
        );

        ApplicationListener albListenerHttps = alb.addListener(projectName + "-ALB-Listener-HTTPS-TC-Server-" + stackEnv,
                BaseApplicationListenerProps
                        .builder()
                        .port(443)
                        .protocol(ApplicationProtocol.HTTPS)
                        .certificates(Collections.singletonList(
                                        ListenerCertificate.fromArn(
                                                String.format("arn:aws:acm:%s:%s:certificate/%s",
                                                        this.getRegion(),
                                                        this.getAccount(),
                                                        Config.DNS_SSL_CERTIFICATE_ID)
                                        )
                                )
                        )
                        .defaultAction(
                                ListenerAction.fixedResponse(
                                        404,
                                        FixedResponseOptions
                                                .builder()
                                                .contentType("application/json")
                                                .messageBody("{\"errorId\": 404,\"errorCode\":\"404\",\"message\":\"Resource not found\",\"field\":\"url\"}")
                                                .build()
                                )
                        )
                        .build());

        alb.addListener(projectName + "-ALB-Listener-HTTP-TC-Server-" + stackEnv,
                BaseApplicationListenerProps
                        .builder()
                        .port(80)
                        .protocol(ApplicationProtocol.HTTP)
                        .defaultAction(ListenerAction.redirect(
                                RedirectOptions
                                        .builder()
                                        .protocol(ApplicationProtocol.HTTPS.name())
                                        .permanent(false)
                                        .build()))
                        .build()
        );

        new ApplicationListenerRule(this, projectName + "-ALR-TC-Server-" + stackEnv,
                ApplicationListenerRuleProps
                        .builder()
                        .listener(albListenerHttps)
                        .priority(1)
                        .conditions(Collections.singletonList(
                                        ListenerCondition
                                                .hostHeaders(
                                                        Collections.singletonList(Config.DNS_HOSTNAME))
                                )
                        )
                        .targetGroups(Collections.singletonList(tgTcServer))
                        .build()
        );

        PrivateDnsNamespace dnsNamespace = new PrivateDnsNamespace(this, projectName + "-ECS-Private-DNS-Namespace-" + stackEnv,
                PrivateDnsNamespaceProps
                        .builder()
                        .name(projectName.toLowerCase() + ".internal")
                        .vpc(vpc)
                        .build()
        );

        this.createWaf(projectName, stackEnv, alb.getLoadBalancerArn(), eipIps);

        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_VPC_SG_ALB, albSG.getSecurityGroupId());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_ECS_KMS_EXEC_ARN, kmsKey.getKeyArn());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_ECS_NAME, cluster.getClusterName());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_ALB_ARN, alb.getLoadBalancerArn());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_ALB_TG_SERVER, tgTcServer.getTargetGroupArn());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_SERVICE_REGISTRY_ARN, dnsNamespace.getNamespaceArn());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_SERVICE_REGISTRY_ID, dnsNamespace.getNamespaceId());
        Utils.createSsmParameter(this, projectName, stackEnv, Config.SSM_SERVICE_REGISTRY_NAME, dnsNamespace.getNamespaceName());
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

    private void createWaf(String projectName, ENV stackEnv, String albArn, List<String> eipIps) {
        CfnIPSet cfnIPSet = CfnIPSet.Builder.create(this, projectName + "-WAF-IP-Sets-" + stackEnv)
                                            .addresses(Stream.concat(Config.WAF_IP_WHITELIST.stream(), eipIps.stream())
                                                             .collect(Collectors.toList()))
                                            .ipAddressVersion("IPV4")
                                            .scope("REGIONAL")
                                            .build();

        CfnRuleGroup cfnRuleGroup = new CfnRuleGroup(this, projectName + "-WAF-Rule-Group-" + stackEnv,
                CfnRuleGroupProps
                        .builder()
                        .name(projectName + "-WAF-Rule-Group-CMS-IP-Whitelist-" + stackEnv)
                        .description("Block if not in IP whitelist")
                        .scope("REGIONAL")
                        .visibilityConfig(CfnRuleGroup.VisibilityConfigProperty
                                .builder()
                                .cloudWatchMetricsEnabled(true)
                                .metricName(projectName.toLowerCase() + "-ip-whitelist-group-" + stackEnv.toString().toLowerCase())
                                .sampledRequestsEnabled(true)
                                .build())
                        .capacity(100)
                        .rules(Collections.singletonList(
                                new HashMap<String, Object>() {{
                                    put("name", "IP-WhiteList");
                                    put("priority", 0);
                                    put("visibilityConfig", new HashMap<String, Object>() {{
                                        put("cloudWatchMetricsEnabled", true);
                                        put("metricName", projectName.toLowerCase() + "-ip-whitelist-" + stackEnv.toString().toLowerCase());
                                        put("sampledRequestsEnabled", true);
                                    }});
                                    put("action", new HashMap<String, Object>() {{
                                        put("block", new HashMap<String, Object>() {{
                                        }});
                                    }});
                                    put("statement", new HashMap<String, Object>() {{
                                        put("orStatement", new HashMap<String, Object>() {{
                                            put("statements",
                                                    Arrays.asList(
                                                            new HashMap<String, Object>() {{
                                                                put("notStatement", new HashMap<String, Object>() {{
                                                                    put("statement", new HashMap<String, Object>() {{
                                                                        put("ipSetReferenceStatement", new HashMap<String, Object>() {{
                                                                            put("arn", cfnIPSet.getAttrArn());
                                                                            put("ipSetForwardedIPConfig", new HashMap<String, Object>() {{
                                                                                put("headerName", "X-Forwarded-For");
                                                                                put("fallbackBehavior", "MATCH");
                                                                            }});
                                                                        }});
                                                                    }});
                                                                }});
                                                            }},
                                                            new HashMap<String, Object>() {{
                                                                put("notStatement", new HashMap<String, Object>() {{
                                                                    put("statement", new HashMap<String, Object>() {{
                                                                        put("byteMatchStatement", new HashMap<String, Object>() {{
                                                                            put("fieldToMatch", new HashMap<String, Object>() {{
                                                                                put("singleHeader", new HashMap<String, Object>() {{
                                                                                    put("name", "host");
                                                                                }});
                                                                            }});
                                                                            put("positionalConstraint", "CONTAINS");
                                                                            put("searchString", Config.DNS_HOSTNAME);
                                                                            put("textTransformations", Collections.singleton(
                                                                                    new HashMap<String, Object>() {{
                                                                                        put("priority", 0);
                                                                                        put("type", "NONE");
                                                                                    }}
                                                                            ));
                                                                        }});
                                                                    }});
                                                                }});
                                                            }}
                                                    )
                                            );
                                        }});
                                    }});
                                }}
                        ))
                        .build()
        );

        CfnWebACL webACL = new CfnWebACL(this, projectName + "-WAF-" + stackEnv,
                CfnWebACLProps
                        .builder()
                        .name(projectName + "-WAF-" + stackEnv)
                        .description("WAF for " + projectName + " " + stackEnv)
                        .defaultAction(CfnWebACL.DefaultActionProperty
                                .builder()
                                .allow(CfnWebACL.AllowActionProperty.builder().build())
                                .build())
                        .visibilityConfig(CfnWebACL.VisibilityConfigProperty
                                .builder()
                                .cloudWatchMetricsEnabled(true)
                                .metricName(projectName.toLowerCase() + "-waf-" + stackEnv.toString().toLowerCase())
                                .sampledRequestsEnabled(true)
                                .build())
                        .scope("REGIONAL")
                        .rules(Arrays.asList(
                                new HashMap<String, Object>() {{
                                    put("name", "DNS-IP-WhiteList");
                                    put("priority", 0);
                                    put("overrideAction", new HashMap<String, Object>() {{
                                        put("none", new HashMap<String, Object>() {{
                                        }});
                                    }});

                                    put("visibilityConfig", new HashMap<String, Object>() {{
                                        put("cloudWatchMetricsEnabled", true);
                                        put("metricName", projectName.toLowerCase() + "-waf-dns-ip-" + stackEnv.toString().toLowerCase());
                                        put("sampledRequestsEnabled", true);
                                    }});
                                    put("statement", new HashMap<String, Object>() {{
                                        put("ruleGroupReferenceStatement", new HashMap<String, Object>() {{
                                            put("arn", cfnRuleGroup.getAttrArn());
                                        }});
                                    }});
                                }},
                                new HashMap<String, Object>() {{
                                    put("name", "AWS-AWSManagedRulesAmazonIpReputationList");
                                    put("priority", 10);
                                    put("overrideAction", new HashMap<String, Object>() {{
                                        put("none", new HashMap<String, Object>() {{
                                        }});
                                    }});
                                    put("visibilityConfig", new HashMap<String, Object>() {{
                                        put("cloudWatchMetricsEnabled", true);
                                        put("metricName", projectName.toLowerCase() + "-waf-amazonipreputationlist-" + stackEnv.toString().toLowerCase());
                                        put("sampledRequestsEnabled", true);
                                    }});
                                    put("statement", new HashMap<String, Object>() {{
                                        put("managedRuleGroupStatement", new HashMap<String, Object>() {{
                                            put("vendorName", "AWS");
                                            put("name", "AWSManagedRulesAmazonIpReputationList");
                                        }});
                                    }});
                                }},
                                new HashMap<String, Object>() {{
                                    put("name", "AWS-AWSManagedRulesSQLiRuleSet");
                                    put("priority", 20);
                                    put("overrideAction", new HashMap<String, Object>() {{
                                        put("none", new HashMap<String, Object>() {{
                                        }});
                                    }});
                                    put("visibilityConfig", new HashMap<String, Object>() {{
                                        put("cloudWatchMetricsEnabled", true);
                                        put("metricName", projectName.toLowerCase() + "-waf-amazonsqli-" + stackEnv.toString().toLowerCase());
                                        put("sampledRequestsEnabled", true);
                                    }});
                                    put("statement", new HashMap<String, Object>() {{
                                        put("managedRuleGroupStatement", new HashMap<String, Object>() {{
                                            put("vendorName", "AWS");
                                            put("name", "AWSManagedRulesSQLiRuleSet");
                                            put("excludedRules", Arrays.asList(
                                                    new HashMap<String, Object>() {{
                                                        put("name", "SQLi_QUERYARGUMENTS");
                                                    }},
                                                    new HashMap<String, Object>() {{
                                                        put("name", "SQLiExtendedPatterns_QUERYARGUMENTS");
                                                    }}
                                            ));
                                        }});
                                    }});
                                }},
                                new HashMap<String, Object>() {{
                                    put("name", "AWS-AWSManagedRuleLinux");
                                    put("priority", 30);
                                    put("overrideAction", new HashMap<String, Object>() {{
                                        put("none", new HashMap<String, Object>() {{
                                        }});
                                    }});
                                    put("visibilityConfig", new HashMap<String, Object>() {{
                                        put("cloudWatchMetricsEnabled", true);
                                        put("metricName", projectName.toLowerCase() + "-waf-amazonlinux-" + stackEnv.toString().toLowerCase());
                                        put("sampledRequestsEnabled", true);
                                    }});
                                    put("statement", new HashMap<String, Object>() {{
                                        put("managedRuleGroupStatement", new HashMap<String, Object>() {{
                                            put("vendorName", "AWS");
                                            put("name", "AWSManagedRulesLinuxRuleSet");
                                        }});
                                    }});
                                }}
                        ))
                        .build()
        );

        new CfnWebACLAssociation(this, projectName + "-WAF-Association-" + stackEnv,
                CfnWebACLAssociationProps.builder()
                                         .webAclArn(webACL.getAttrArn())
                                         .resourceArn(albArn)
                                         .build()
        );
    }
}
