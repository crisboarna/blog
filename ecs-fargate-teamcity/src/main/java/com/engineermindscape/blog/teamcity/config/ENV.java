package com.engineermindscape.blog.teamcity.config;

import java.util.Arrays;
import java.util.Optional;

public enum ENV {
    CI("CI");

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
