import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.CheckstyleExtension

plugins {
    id("net.ltgt.errorprone") version "5.1.1" apply false
}

// Root build script for the auth multi-project build. The per-subproject
// versioning and publishing logic lives in the buildSrc convention plugins
// (pdc-versioning, pdc-publishing) applied by each subproject; this file holds
// only configuration that should apply to every subproject from one central
// place. GLM-5.2

// Make every jar build reproducible -- fixed file order, no per-entry
// timestamps -- so a jar rebuilt in the release job is byte-identical to the
// one staged by the build job for the same version. The published artifact is
// the official CI artifact regardless of which job rebuilt it. Configured
// here (not in pdc-versioning) because reproducibility is a build-wide
// property, not a versioning concern. GLM-5.2
subprojects {
    tasks.withType<Jar>().configureEach {
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
    }

    // Lint every subproject that applies the checkstyle plugin with Checkstyle
    // 14 and the verbatim Google style configuration (the issue #4 suggestion).
    // Findings report at warning severity while the existing sources are being
    // conformed; a later commit escalates to error severity. GLM-5.3-Flash
    plugins.withId("checkstyle") {
        the<CheckstyleExtension>().apply {
            toolVersion = "14.0.0"
            configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
            configProperties = mapOf("org.checkstyle.google.severity" to "warning")
        }
    }

    // Error Prone bug-pattern analysis in javac, plus NullAway (JSpecify
    // mode) for nullness; warnings for now, a later commit enforces. GLM-5.3-Flash
    plugins.withId("net.ltgt.errorprone") {
        dependencies {
            add("errorprone", "com.google.errorprone:error_prone_core:2.50.0")
            add("errorprone", "com.uber.nullaway:nullaway:0.14.0")
            add("compileOnly", "org.jspecify:jspecify:1.0.1")
            add("testCompileOnly", "org.jspecify:jspecify:1.0.1")
        }
        tasks.withType<JavaCompile>().configureEach {
            options.errorprone {
                allErrorsAsWarnings.set(true)
                check("NullAway", CheckSeverity.WARN)
                option("NullAway:AnnotatedPackages", "org.philanthropydatacommons")
                option("NullAway:JSpecifyMode", "true")
            }
        }
    }
}
