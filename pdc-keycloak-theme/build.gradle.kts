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

// We target the class version of the JRE used in the keycloak container: 11.
tasks.compileJava {
    options.release.set(11)
}

repositories {
    mavenCentral()
}

// Declare dependency on a font package so that we can include font resources.
dependencies {
    implementation("org.webjars.npm:fontsource__source-sans-pro:4.5.11")
}

// Create a task that will unpack just the .woff and .woff2 files of one font.
tasks.register<Copy>("unpackSourceSansPro") {
    // See https://docs.gradle.org/8.0.2/userguide/working_with_files.html#sec:unpacking_archives_example
    from(zipTree(configurations.runtimeClasspath.get().filter{ it.name.contains("source-sans-pro") }.files.first())) {
        include("META-INF/resources/webjars/fontsource__source-sans-pro/*/files/source-sans-pro*-400-*.woff*")
        include("META-INF/resources/webjars/fontsource__source-sans-pro/*/files/source-sans-pro*-600-*.woff*")
        include("META-INF/resources/webjars/fontsource__source-sans-pro/*/files/source-sans-pro*-700-*.woff*")
        include("META-INF/resources/webjars/fontsource__source-sans-pro/*/400.css")
        include("META-INF/resources/webjars/fontsource__source-sans-pro/*/600.css")
        include("META-INF/resources/webjars/fontsource__source-sans-pro/*/700.css")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(5).toTypedArray())
            relativePath = relativePath.prepend("source-sans-pro")
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("resources/main/theme/pdc-keycloak-theme/login/resources/fonts"))
}

// Make sure that the font unpacking task runs before the jar task.
tasks.jar {
    dependsOn(tasks.get("unpackSourceSansPro"))
}
