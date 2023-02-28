/*
 * Copyright (c) 2023 Open Tech Strategies, LLC
 * License: Expat (MIT) license.
 */
plugins {
    `java-library`
    // Test coverage
    jacoco
    // The shadow plugin can create "fat" and/or "shaded" jars, i.e. include dependencies in the
    // resulting jar. This is useful for including the twilio SDK and its dependencies on the
    // keycloak classpath with a single jar. The alternative would be to copy/include each jar
    // and dependent jars onto the keycloak classpath. See exclusions below because there is some
    // overlap in the twilio and keycloak dependencies.
    id("com.github.johnrengelman.shadow") version "7.1.2"
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

repositories {
    mavenCentral()
}

ext {
    set("keycloakVersion", "20.0.5");
}

dependencies {
    // These org.keycloak jars are already on the classpath within keycloak, so they do not need
    // to be declared as part of the runtime classpath. This also means they are excluded from the
    // jar produced by the shadow plugin via the shadowJar task.
    compileOnly("org.keycloak:keycloak-core:${project.ext.get("keycloakVersion")}")
    compileOnly("org.keycloak:keycloak-server-spi:${project.ext.get("keycloakVersion")}")
    compileOnly("org.keycloak:keycloak-server-spi-private:${project.ext.get("keycloakVersion")}")
    compileOnly("org.keycloak:keycloak-services:${project.ext.get("keycloakVersion")}")
    compileOnly("com.github.dasniko:keycloak-spi-bom:20.0.0")
    // Twilio's dependencies are used by our extension but not intended to be further exposed.
    // The shadow plugin jar (shadowJar task) will include this and its dependencies.
    implementation("com.twilio.sdk:twilio:9.2.3")

    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    // In keycloak, slf4j is bridged to jboss-logging. For test runtime here use slf4j-simple.
    testImplementation("org.slf4j:slf4j-simple:2.0.6")
    // To create mock instances
    testImplementation("org.mockito:mockito-junit-jupiter:5.1.1")
    testImplementation("org.keycloak:keycloak-core:${project.ext.get("keycloakVersion")}")
    testImplementation("org.keycloak:keycloak-server-spi-private:${project.ext.get("keycloakVersion")}")
    testImplementation("org.keycloak:keycloak-server-spi:${project.ext.get("keycloakVersion")}")
    testImplementation("org.keycloak:keycloak-services:${project.ext.get("keycloakVersion")}")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

tasks.shadowJar {
    // A diff of the dependencies of keycloak jars and twilio jars produced the following common
    // dependencies, such that these should be on the keycloak classpath already and should NOT be
    // included in a fat jar. This may be subject to change with revisions of twilio or keycloak.
    // If we did not exclude them (without also relocating them) there could be multiple versions
    // of the same class on the classpath which is bad news.
    dependencies {
        exclude(dependency("com.fasterxml.jackson.core:jackson-annotations:.*"))
        exclude(dependency("com.fasterxml.jackson.core:jackson-core:.*"))
        exclude(dependency("com.fasterxml.jackson.core:jackson-databind:.*"))
        exclude(dependency("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:.*"))
        exclude(dependency("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:.*"))
        exclude(dependency("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:.*"))
        // Woodstox is not in keycloak deps, but other implementations of the same SPIs are.
        exclude(dependency("com.fasterxml.woodstox:woodstox-core:.*"))
        exclude(dependency("commons-codec:commons-codec:.*"))
        exclude(dependency("commons-io:commons-io:.*"))
        // A bridge/adapter for commons-logging is in keycloak: commons-logging-jboss-logging.
        exclude(dependency("commons-logging:commons-logging:.*"))
        exclude(dependency("org.apache.httpcomponents:httpclient:.*"))
        exclude(dependency("org.apache.httpcomponents:httpcore:.*"))
        // Woodstox is not in keycloak deps, but other implementations of the same SPIs are.
        exclude(dependency("org.codehaus.woodstox:stax2-api:.*"))
        exclude(dependency("org.slf4j:.*:.*"))
    }

    // To avoid classpath conflicts, relocate the remaining twilio dependencies:
    relocate("com.twilio", "org.philanthropydatacommons.shadow.com.twilio")
    relocate("org.json", "org.philanthropydatacommons.shadow.org.json")
    relocate("io.jsonwebtoken", "org.philanthropydatacommons.shadow.io.jsonwebtoken")

    // The mergeServiceFiles also relocates the SPI definitions in META-INF/services
    mergeServiceFiles()
}
