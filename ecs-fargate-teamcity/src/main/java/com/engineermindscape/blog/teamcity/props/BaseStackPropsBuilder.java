package com.engineermindscape.blog.teamcity.props;

import com.engineermindscape.blog.teamcity.config.ENV;
import software.amazon.awscdk.Environment;

/**
 * BaseStackPropsBuilder
 * <p>
 *     BaseStackPropsBuilder is a builder class that is used to create the BaseStackProps object.
 *     It is used to create the BaseStackProps object.
 * </p>
 * @param <T>
 */
public class BaseStackPropsBuilder<T extends BaseStackPropsBuilder<T>> {
    protected String projectName;
    protected ENV stackEnv;

    protected Environment env;
    protected String description;

    public T projectName(String projectName) {
        this.projectName = projectName;
        return self();
    }

    public T stackEnv(ENV stackEnv) {
        this.stackEnv = stackEnv;
        return self();
    }

    public T env(Environment env) {
        this.env = env;
        return self();
    }

    public T description(String description) {
        this.description = description;
        return self();
    }

    public BaseStackProps build() {
        return new BaseStackProps(env, description, projectName, stackEnv );
    }

    protected T self() {
        return (T) this;
    }
}