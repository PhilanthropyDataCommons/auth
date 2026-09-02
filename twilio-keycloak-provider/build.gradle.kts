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
    // The shadow plugin can create "fat" and/or "shaded" jars, i.e. include dependencies in the
    // resulting jar. This is useful for including the twilio SDK and its dependencies on the
    // keycloak classpath with a single jar. The alternative would be to copy/include each jar
    // and dependent jars onto the keycloak classpath. See exclusions below because there is some
    // overlap in the twilio and keycloak dependencies.
    id("com.gradleup.shadow") version "9.2.2"
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
    // to be declared as part of the runtime classpath. This also means they are excluded from the
    // jar produced by the shadow plugin via the shadowJar task.
    compileOnly("org.keycloak:keycloak-core:26.7.2")
    compileOnly("org.keycloak:keycloak-server-spi:26.7.2")
    compileOnly("org.keycloak:keycloak-server-spi-private:26.7.2")
    compileOnly("org.keycloak:keycloak-services:26.7.2")
    compileOnly("com.github.dasniko:keycloak-spi-bom:26.7.0")
    // Twilio's dependencies are used by our extension but not intended to be further exposed.
    // The shadow plugin jar (shadowJar task) will include this and its dependencies.
    implementation("com.twilio.sdk:twilio:11.3.4")

    // Use JUnit Jupiter for testing.
    testRuntimeOnly("org.junit.platform:junit-platform-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    // In keycloak, slf4j is bridged to jboss-logging. For test runtime here use slf4j-simple.
    testImplementation("org.slf4j:slf4j-simple:2.0.18")
    // To create mock instances
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.keycloak:keycloak-core:26.7.2")
    testImplementation("org.keycloak:keycloak-server-spi-private:26.7.2")
    testImplementation("org.keycloak:keycloak-server-spi:26.7.2")
    testImplementation("org.keycloak:keycloak-services:26.7.2")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    // The plain (unshaded) jar is not the deployable artifact; qualify it so
    // it does not collide with the unqualified fat jar. GLM-5.2
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    // The fat (shadow) jar is the primary artifact we publish and deploy; give
    // it no classifier so it is `twilio-keycloak-provider-<version>.jar`.
    // GLM-5.2
    archiveClassifier.set("")
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
        // These are not directly referenced by Twilio code.
        exclude(dependency("ch.randelshofer:.*:.*"))
        // Auth0 code is for fancier use cases than ours.
        exclude(dependency("com.auth0:.*:.*"))
        // Google GSON is already in Keycloak.
        exclude(dependency("com.google.code.gson:gson:.*"))
    }

    // To avoid classpath conflicts, relocate the remaining twilio dependencies:
    relocate("com.twilio", "org.philanthropydatacommons.shadow.com.twilio")
    relocate("org.json", "org.philanthropydatacommons.shadow.org.json")
    relocate("io.jsonwebtoken", "org.philanthropydatacommons.shadow.io.jsonwebtoken")
    relocate("org.apache.hc.core5", "org.philanthropydatacommons.shadow.org.apache.hc.core5")
    relocate("org.apache.hc.client5", "org.philanthropydatacommons.shadow.org.apache.hc.client5")
    relocate("org.publicsuffix", "org.philanthropydatacommons.shadow.org.publicsuffix")

    // The mergeServiceFiles also relocates the SPI definitions in META-INF/services
    mergeServiceFiles()
}
