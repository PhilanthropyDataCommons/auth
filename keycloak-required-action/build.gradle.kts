/*
 * Copyright (c) 2023-2026 MacArthur Foundation
 * License: Apache License 2.0
 */
plugins {
  `java-library`
  id("pdc-versioning")
  id("pdc-publishing")
  // Lint checks (Google Java Style). GLM-5.3-Flash
  checkstyle
  // Error Prone (plus NullAway) runs inside javac. GLM-5.3-Flash
  id("net.ltgt.errorprone")
}

// We expect the current LTS version of the JDK for IDEs, compilation, etc.: 17.
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
}
