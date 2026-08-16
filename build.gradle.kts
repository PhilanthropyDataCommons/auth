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
}
