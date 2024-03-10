package com.engineermindscape.blog.efs.escapehatch.config;

import java.util.Arrays;
import java.util.Optional;

public enum ENV {
    DEMO("DEMO");

    private final String name;

    ENV(String name) {
        this.name = name;
    }

    public static Optional<ENV> get(String name) {
        return Arrays.stream(ENV.values())
                     .filter(env -> env.name.equalsIgnoreCase(name))
                     .findFirst();
    }

    @Override
    public String toString() {
        return name;
    }
}
