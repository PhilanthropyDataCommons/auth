/*
 * Copyright (c) 2023 Open Tech Strategies, LLC
 * License: Apache License 2.0
 */
plugins {
    `java-library`
}

// We expect the current LTS version of the JDK for IDEs, compilation, etc.: 17.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// We target the version of the JRE used in the keycloak container: 11.
tasks.compileJava {
    options.release.set(11)
}
