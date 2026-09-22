/*
 * Copyright (c) 2023-2026 MacArthur Foundation
 * License: Expat (MIT) license.
 */
plugins {
    `java-library`
    id("pdc-versioning")
    id("pdc-publishing")
    // Test coverage
    jacoco
    // Lint checks (Google Java Style). GLM-5.3-Flash
    checkstyle
    // Error Prone (plus NullAway) runs inside javac. GLM-5.3-Flash
    id("net.ltgt.errorprone")
}

// We expect the current LTS version of the JDK for IDEs, compilation, etc.: 25.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// We target the version of the JRE currently used by Keycloak: 17.
tasks.compileJava {
    options.release.set(17)
}

repositories {
    mavenCentral()
}

dependencies {
    // These org.keycloak jars are already on the classpath within keycloak, so they do not need
    // to be declared as part of the runtime classpath.
    compileOnly("org.keycloak:keycloak-core:26.7.4")
    compileOnly("org.keycloak:keycloak-server-spi:26.7.4")
    compileOnly("org.keycloak:keycloak-server-spi-private:26.7.4")
    compileOnly("org.keycloak:keycloak-services:26.7.4")
    compileOnly("com.github.dasniko:keycloak-spi-bom:26.7.0")
    // Use JUnit Jupiter for testing.
    testRuntimeOnly("org.junit.platform:junit-platform-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    // In keycloak, slf4j is bridged to jboss-logging. For test runtime here use slf4j-simple.
    testImplementation("org.slf4j:slf4j-simple:2.0.20")
    // To create mock instances
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.keycloak:keycloak-core:26.7.4")
    // compileOnly above for main sources (Keycloak provides it at runtime) but a test-runtime
    // dependency here, so tests exercise the real SimpleHttp class and will catch a future
    // removal from Keycloak at test time. Its published POM also brings Apache HttpClient 4
    // (SimpleHttp's transport) in transitively, version-managed by Keycloak.
    testImplementation("org.keycloak:keycloak-server-spi-private:26.7.4")
    testImplementation("org.keycloak:keycloak-server-spi:26.7.4")
    testImplementation("org.keycloak:keycloak-services:26.7.4")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
