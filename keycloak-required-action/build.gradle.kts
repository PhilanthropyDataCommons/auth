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


// We target the version of the JRE currently used by Keycloak: 17.
tasks.compileJava {
  options.release.set(17)
}

repositories {
  mavenCentral()
}

ext {
  set("keycloakVersion", "22.0.1")
}

dependencies {
  compileOnly("org.keycloak:keycloak-core:${project.ext.get("keycloakVersion")}")
  compileOnly("org.keycloak:keycloak-server-spi:${project.ext.get("keycloakVersion")}")
  compileOnly("org.keycloak:keycloak-server-spi-private:${project.ext.get("keycloakVersion")}")
  compileOnly("org.keycloak:keycloak-services:${project.ext.get("keycloakVersion")}")
}
