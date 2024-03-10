package com.engineermindscape.blog.teamcity.props;

import com.engineermindscape.blog.teamcity.config.ENV;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.Environment;

/**
 * VpcStackProps
 * <p>
 *     VpcStackProps is a class that contains the properties for the VpcStack.
 *     It is used to create the stackProps for the VpcStack.
 *     It is also used to create the VpcStackPropsBuilder.
 * </p>
 */
public class VpcStackProps extends BaseStackProps {
    public final String cidrVpc;
    public final String cidrVpn;

    public VpcStackProps(Environment env, @Nullable String description, String projectName, ENV stackEnv, String cidrVpc, String cidrVpn) {
        super(env, description, projectName, stackEnv);

        if(cidrVpc == null || cidrVpc.isEmpty()) throw new RuntimeException("cidrVpc is required");
        if(!cidrVpc.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\/\\d+$")) throw new RuntimeException("cidrVpc is invalid");
        if(cidrVpc.split("\\/")[1].equals("0")) throw new RuntimeException("cidrVpc is invalid");

        if(cidrVpn == null || cidrVpn.isEmpty()) throw new RuntimeException("cidrVpn is required");
        if(!cidrVpn.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\/\\d+$")) throw new RuntimeException("cidrVpn is invalid");
        if(cidrVpn.split("\\/")[1].equals("0")) throw new RuntimeException("cidrVpn is invalid");

        this.cidrVpc = cidrVpc;
        this.cidrVpn = cidrVpn;
    }

    static public VpcStackPropsBuilder builder() {
        return new VpcStackPropsBuilder();
    }
}
