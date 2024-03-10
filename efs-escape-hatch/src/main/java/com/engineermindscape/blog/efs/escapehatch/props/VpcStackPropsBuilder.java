package com.engineermindscape.blog.efs.escapehatch.props;

/**
 * VpcStackPropsBuilder
 * <p>
 */
public class VpcStackPropsBuilder extends BaseStackPropsBuilder<VpcStackPropsBuilder> {
    private String cidrVpc;
    private String cidrVpn;

    public VpcStackPropsBuilder cidrVpc(String cidrVpc) {
        this.cidrVpc = cidrVpc;
        return self();
    }

    public VpcStackPropsBuilder cidrVpn(String cidrVpn) {
        this.cidrVpn = cidrVpn;
        return self();
    }

    public VpcStackProps build() {
        return new VpcStackProps(env, description, projectName, stackEnv, cidrVpc, cidrVpn);
    }

    @Override
    protected VpcStackPropsBuilder self() {
        return this;
    }
}
