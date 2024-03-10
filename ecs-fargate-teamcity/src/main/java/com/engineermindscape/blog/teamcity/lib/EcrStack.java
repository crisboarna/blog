package com.engineermindscape.blog.teamcity.lib;

import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.props.EcrStackProps;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.TagStatus;
import software.amazon.awscdk.services.iam.AccountPrincipal;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EcrStack extends Stack {
    public EcrStack(final Construct scope, final String id, final EcrStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;
        List<String> repositoryNames = props.repositoryNames;
        List<String> accountIds = props.accountIds;

        repositoryNames.forEach(name -> {
            Repository repository = Repository
                    .Builder.create(this, String.format("%s-ECR-Repository-%s-%s", projectName, name, stackEnv))
                            .repositoryName(name)
                            .imageScanOnPush(true)
                            .lifecycleRules(Collections.singletonList(LifecycleRule
                                    .builder()
                                    .description("Expire untagged images")
                                    .rulePriority(1)
                                    .maxImageAge(Duration.days(1))
                                    .tagStatus(TagStatus.UNTAGGED)
                                    .build()))
                            .removalPolicy(RemovalPolicy.DESTROY)
                            .build();

            repository.addToResourcePolicy(new PolicyStatement(
                   PolicyStatementProps
                           .builder()
                           .sid("Pull")
                           .effect(Effect.ALLOW)
                           .principals(accountIds.stream().map(AccountPrincipal::new).collect(Collectors.toList()))
                           .actions(Arrays.asList(
                                   "ecr:BatchCheckLayerAvailability",
                                   "ecr:GetDownloadUrlForLayer",
                                   "ecr:BatchGetImage")
                           )
                           .build()));
        });
    }
}
