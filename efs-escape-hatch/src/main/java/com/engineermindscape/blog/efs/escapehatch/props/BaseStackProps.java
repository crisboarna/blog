package com.engineermindscape.blog.efs.escapehatch.props;

import com.engineermindscape.blog.efs.escapehatch.config.ENV;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * BaseStackProps
 * <p>
 *     BaseStackProps is a class that contains the common properties for all stacks.
 *     It is used to create the stackProps for all stacks.
 *     It is also used to create the BaseStackPropsBuilder.
 * </p>
 */
public class BaseStackProps implements StackProps {

    public final String projectName;
    public final ENV stackEnv;
    public Environment env;
    public String description;
    private final StackProps stackProps;

    public BaseStackProps(Environment env, @Nullable String description, String projectName, ENV stackEnv) {
        if(env == null) throw new RuntimeException("env is required");
        if(description == null || description.isEmpty()) throw new RuntimeException("description is required");
        if(projectName == null || projectName.isEmpty()) throw new RuntimeException("projectName is required");
        if(stackEnv == null) throw new RuntimeException("stackEnv is required");

        this.env = env;
        this.description = description;
        this.projectName = projectName;
        this.stackEnv = stackEnv;
        this.stackProps = StackProps.builder()
                                    .env(env)
                                    .description(description)
                                    .build();
    }

    /**
     * BaseStackPropsBuilder
     * <p>
     *     BaseStackPropsBuilder is a builder class that is used to create the BaseStackProps object.
     * </p>
     */
    static public BaseStackPropsBuilder builder() {
        return new BaseStackPropsBuilder();
    }

    public StackProps getStackProps() {
        return stackProps;
    }
}
