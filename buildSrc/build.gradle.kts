// Build logic for the auth multi-project build. Provides the
// `pdc-versioning` convention plugin applied by each subproject to compute a
// per-jar version from git and stamp the jar manifest. GLM-5.2
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}
