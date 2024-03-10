package com.engineermindscape.blog.teamcity.lib;

import com.engineermindscape.blog.teamcity.config.Config;
import com.engineermindscape.blog.teamcity.config.ENV;
import com.engineermindscape.blog.teamcity.props.BaseStackProps;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.codeartifact.CfnDomain;
import software.amazon.awscdk.services.codeartifact.CfnRepository;
import software.amazon.awscdk.services.iam.*;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

public class CodeArtifactStack extends Stack {
    public CodeArtifactStack(final Construct scope, final String id, final BaseStackProps props) {
        super(scope, id, props.getStackProps());

        String projectName = props.projectName;
        ENV stackEnv = props.stackEnv;
        String domainName = Config.CODEARTIFACT_DOMAIN_NAME;
        String mavenCentralRepositoryName = Config.CODEARTIFACT_REPO_MAVEN_CENTRAL_NAME;
        String npmRepositoryName = Config.CODEARTIFACT_REPO_NPM_NAME;
        String releasesRepositoryName = Config.CODEARTIFACT_REPO_MAVEN_RELEASES_NAME;
        String snapshotsRepositoryName = Config.CODEARTIFACT_REPO_MAVEN_SNAPSHOTS_NAME;

        CfnDomain codeArtifactDomain = CfnDomain
                .Builder
                .create(this, projectName + "-CodeArtifactDomain-" + stackEnv)
                .domainName(domainName)
                .permissionsPolicyDocument(PolicyDocument
                        .Builder.create()
                                .statements(Arrays.asList(
                                        new PolicyStatement(
                                                PolicyStatementProps.builder()
                                                                    .sid("CodeArtifact")
                                                                    .effect(Effect.ALLOW)
                                                                    .actions(Collections.singletonList("codeartifact:*"))
                                                                    .resources(Collections.singletonList("*"))
                                                                    .principals(Config.ACCOUNT_IDS.stream().map(accountId -> new ArnPrincipal("arn:aws:iam::" + accountId + ":root")).collect(Collectors.toList()))
                                                                    .build())
                                ))
                                .build())
                .build();

        CfnRepository mavenCentralRepository = CfnRepository
                .Builder
                .create(this, projectName + "-MavenCentralRepository-" + stackEnv)
                .repositoryName(mavenCentralRepositoryName)
                .description("Maven Central Store")
                .domainName(codeArtifactDomain.getDomainName())
                .externalConnections(Collections.singletonList("public:maven-central"))
                .build();

        mavenCentralRepository.getNode().addDependency(codeArtifactDomain);

        CfnRepository releasesRepository = CfnRepository
                .Builder
                .create(this, projectName + "-ReleasesRepository-" + stackEnv)
                .repositoryName(releasesRepositoryName)
                .description("Releases repository")
                .domainName(codeArtifactDomain.getDomainName())
                .upstreams(Collections.singletonList(mavenCentralRepository.getRepositoryName()))
                .build();

        releasesRepository.getNode().addDependency(mavenCentralRepository);

        CfnRepository snapshotsRepository = CfnRepository
                .Builder
                .create(this, projectName + "-SnapshotsRepository-" + stackEnv)
                .repositoryName(snapshotsRepositoryName)
                .description("Snapshots repository")
                .domainName(codeArtifactDomain.getDomainName())
                .upstreams(Collections.singletonList(releasesRepository.getRepositoryName()))
                .build();

        snapshotsRepository.getNode().addDependency(releasesRepository);


        CfnRepository npmRepository = CfnRepository
                .Builder
                .create(this, projectName + "-NpmRepository-" + stackEnv)
                .repositoryName(npmRepositoryName)
                .description("NPM Store")
                .domainName(codeArtifactDomain.getDomainName())
                .externalConnections(Collections.singletonList("public:npmjs"))
                .build();

        npmRepository.getNode().addDependency(codeArtifactDomain);
    }
}
