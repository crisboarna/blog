package com.engineermindscape.blog.teamcity.props;

import com.engineermindscape.blog.teamcity.config.ENV;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.Environment;

import java.util.List;

/**
 * RepositoriesStackProps
 * <p>
 *     RepositoriesStackProps is a class that contains the properties for the RepositoriesStack.
 *     It is used to create the stackProps for the RepositoriesStack.
 *     It is also used to create the RepositoriesStackPropsBuilder.
 * </p>
 */
public class EcrStackProps extends BaseStackProps {
    public final List<String> repositoryNames;
    public final List<String> accountIds;

    public EcrStackProps(Environment env, @Nullable String description, String projectName, ENV stackEnv, List<String> repositoryNames, List<String> accountIds) {
        super(env, description, projectName, stackEnv);

        if(repositoryNames == null || repositoryNames.isEmpty()) throw new RuntimeException("repositoryNames is required");
        if(accountIds == null || accountIds.isEmpty()) throw new RuntimeException("accountIds is required");

        this.repositoryNames = repositoryNames;
        this.accountIds = accountIds;
    }

    /**
     * RepositoriesStackPropsBuilder
     * <p>
     *     RepositoriesStackPropsBuilder is a builder class that is used to create the RepositoriesStackProps object.
     * </p>
     */
    static public EcrStackPropsBuilder builder() {
        return new EcrStackPropsBuilder();
    }
}
