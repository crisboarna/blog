package com.engineermindscape.blog.teamcity.props;

import java.util.List;

/**
 * RepositoriesStackPropsBuilder
 * <p>
 *     RepositoriesStackPropsBuilder is a builder class that is used to create the RepositoriesStackProps object.
 * </p>
 */
public class EcrStackPropsBuilder extends BaseStackPropsBuilder<EcrStackPropsBuilder> {
    private List<String> repositoryNames;
    private List<String> accountIds;

    public EcrStackPropsBuilder repositoryNames(List<String> repositoryNames) {
        this.repositoryNames = repositoryNames;
        return self();
    }

    public EcrStackPropsBuilder accountIds(List<String> accountIds) {
        this.accountIds = accountIds;
        return self();
    }

    public EcrStackProps build() {
        return new EcrStackProps(env, description, projectName, stackEnv, repositoryNames, accountIds);
    }

    @Override
    protected EcrStackPropsBuilder self() {
        return this;
    }
}
