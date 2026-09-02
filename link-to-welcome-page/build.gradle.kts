/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0
 * (see the LICENSE file in this directory).
 *
 * Author: GLM-5.2
 */
plugins {
  `java-library`
  id("pdc-versioning")
  id("pdc-publishing")
  jacoco
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
  compileOnly("org.keycloak:keycloak-core:26.7.2")
  compileOnly("org.keycloak:keycloak-server-spi:26.7.2")
  compileOnly("org.keycloak:keycloak-server-spi-private:26.7.2")
  compileOnly("org.keycloak:keycloak-services:26.7.2")
  // slf4j API only; in Keycloak it is bridged to jboss-logging at runtime.
  compileOnly("org.slf4j:slf4j-api:2.0.18")

  // Use JUnit Jupiter for testing, mirroring the twilio-keycloak-provider module.
  testRuntimeOnly("org.junit.platform:junit-platform-engine:6.1.3")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
  testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
  // In keycloak, slf4j is bridged to jboss-logging. For test runtime here use slf4j-simple.
  testImplementation("org.slf4j:slf4j-simple:2.0.18")
  // To create mock instances (and to mockStatic the Keycloak helpers we delegate to).
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
