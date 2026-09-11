// Init script for the pre-shade Keycloak classpath overlap check.
//
// Applied only by the keycloak-classpath-overlap CI workflow (and locally
// when reproducing it) via `gradlew -I <this file> <task>`. It is NOT applied
// by the normal build.
//
// It registers a `collectRuntimeClasspathForOverlap` task on every subproject
// that copies the resolved `runtimeClasspath` files into
// `build/runtime-classpath-for-overlap/`. The shadow plugin's `exclude(...)`
// directives inside the `shadowJar` task only affect PACKAGING (what goes into
// the fat jar); they do NOT affect dependency RESOLUTION. So the resolved
// `runtimeClasspath` is the true PRE-shade input set -- every jar that would
// be bundled if no excludes/relocations were applied. `compileOnly` deps (the
// keycloak-* jars) are NOT on `runtimeClasspath`, so this set is exactly the
// twilio + transitive jars that risk overlapping with what Keycloak ships at
// runtime. The overlap script then compares these against the Keycloak
// distribution's runtime jars.
//
// The task is a no-op on subprojects that have no `runtimeClasspath`
// configuration (e.g. the theme subproject's setup is different), so it can be
// registered on all subprojects without breaking the build.

allprojects {
    afterEvaluate {
        val cfg = configurations.findByName("runtimeClasspath") ?: return@afterEvaluate
        tasks.register("collectRuntimeClasspathForOverlap") {
            group = "verification"
            description =
                "Copy the resolved runtimeClasspath jars into " +
                "build/runtime-classpath-for-overlap/ for the pre-shade " +
                "Keycloak classpath overlap check."
            // Resolving the configuration forces dependency resolution; this
            // is what we want (it fails fast on unresolvable deps).
            dependsOn(cfg)
            val outDir = layout.buildDirectory.dir("runtime-classpath-for-overlap")
            doLast {
                val dest = outDir.get().asFile
                dest.mkdirs()
                // Start from a clean dir so stale jars/manifest from a previous
                // run (e.g. an older twilio version) cannot linger and skew the
                // overlap comparison.
                dest.listFiles()?.forEach { it.delete() }
                // Resolve to ResolvedArtifact so we can record each jar's
                // Maven coordinates (group:artifact:version). The overlap
                // script matches these against the shadowJar exclude(...)
                // directives to classify each overlapping jar as covered
                // (excluded -> whole jar removed from the fat jar) or not.
                val artifacts = cfg.resolvedConfiguration.resolvedArtifacts.toList()
                val manifestFile = java.io.File(dest, "MANIFEST.txt")
                manifestFile.parentFile.mkdirs()
                // Two distinct resolved modules can share a filename (same
                // artifactId+version, different groups). Flattening would then
                // overwrite one jar and mis-associate the manifest entry,
                // which can hide a real overlap or misclassify coverage. Fail
                // loudly instead of silently dropping a jar.
                val seen = mutableMapOf<String, String>()
                manifestFile.bufferedWriter().use { w ->
                    artifacts.forEach { art ->
                        val id = art.moduleVersion.id
                        val coord = "${id.group}:${id.name}:${id.version}"
                        val name = art.file.name
                        val prior = seen[name]
                        if (prior != null && prior != coord) {
                            throw GradleException(
                                "collectRuntimeClasspathForOverlap: two " +
                                "distinct resolved artifacts share filename " +
                                "'$name': '$prior' and '$coord'. Refusing to " +
                                "flatten; rename or resolve the collision."
                            )
                        }
                        seen[name] = coord
                        // <group>:<artifact>:<version>\t<jarfilename>
                        w.write("$coord\t$name\n")
                    }
                }
                // Copy every resolved jar into the dir.
                artifacts.forEach { art ->
                    art.file.copyTo(java.io.File(dest, art.file.name), overwrite = true)
                }
                logger.lifecycle(
                    "[collectRuntimeClasspathForOverlap] {} copied {} runtime-classpath file(s) into {} (manifest: {})",
                    project.path, artifacts.size, dest, manifestFile.name,
                )
            }
        }
    }
}
