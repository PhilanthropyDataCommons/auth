import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import java.net.URI
import java.util.jar.Manifest
import java.util.regex.Pattern
import java.util.zip.ZipFile
import java.util.zip.ZipException

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
    id("com.gradleup.shadow") version "9.6.1"
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
    compileOnly("org.keycloak:keycloak-core:26.7.4")
    compileOnly("org.keycloak:keycloak-server-spi:26.7.4")
    compileOnly("org.keycloak:keycloak-server-spi-private:26.7.4")
    compileOnly("org.keycloak:keycloak-services:26.7.4")
    compileOnly("com.github.dasniko:keycloak-spi-bom:26.7.0")
    // Twilio's dependencies are used by our extension but not intended to be further exposed.
    // The shadow plugin jar (shadowJar task) will include this and its dependencies.
    implementation("com.twilio.sdk:twilio:13.0.1")

    // Use JUnit Jupiter for testing.
    testRuntimeOnly("org.junit.platform:junit-platform-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    // In keycloak, slf4j is bridged to jboss-logging. For test runtime here use slf4j-simple.
    testImplementation("org.slf4j:slf4j-simple:2.0.20")
    // To create mock instances
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.keycloak:keycloak-core:26.7.4")
    testImplementation("org.keycloak:keycloak-server-spi-private:26.7.4")
    testImplementation("org.keycloak:keycloak-server-spi:26.7.4")
    testImplementation("org.keycloak:keycloak-services:26.7.4")
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

// ---------------------------------------------------------------------------
// Pre-shade Keycloak classpath overlap check
// ---------------------------------------------------------------------------
// checkKeycloakClasspathOverlap FAILs on any twilio jar whose classes overlap
// Keycloak runtime jars and is not excluded by exclude(dependency(...));
// relocate(...) is defensive only. It reads the shadowJar task's live
// dependencyFilter and relocators, so a commented-out exclude is not active.

val overlapRuntimeCfg = configurations.named("runtimeClasspath")

val overlapJavaTarget = run {
    val raw = project.findProperty("overlapCheckJavaTarget") as String?
    if (raw == null) {
        17
    } else {
        val parsed = raw.trim().toIntOrNull()
        if (parsed == null || parsed <= 0) {
            throw GradleException(
                "overlapCheckJavaTarget must be a positive integer (got: '$raw')."
            )
        }
        parsed
    }
}

val keycloakVersion = configurations.named("compileClasspath").map { cfg ->
    cfg.resolvedConfiguration.resolvedArtifacts
        .firstOrNull { art ->
            val id = art.moduleVersion.id
            id.group == "org.keycloak" && id.name == "keycloak-core"
        }?.moduleVersion?.id?.version
        ?: throw GradleException(
            "checkKeycloakClasspathOverlap: could not resolve the " +
                "org.keycloak:keycloak-core version from the compileClasspath " +
                "configuration; the overlap check cannot determine which " +
                "Keycloak distribution to download."
        )
}

tasks.register("checkKeycloakClasspathOverlap") {
    group = "verification"
    description =
        "Detect twilio runtime-classpath jars whose classes overlap with " +
            "Keycloak's runtime classpath and are not covered by an " +
            "exclude(dependency(...)) in the shadowJar task."

    val kcVer = keycloakVersion
    val distDir = layout.buildDirectory.dir(
        keycloakVersion.map { v -> "keycloak-overlap-dist/keycloak-$v" }
    )

    doLast {
        val kcVerValue = kcVer.get()
        val shadowJar =
            tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>(
                "shadowJar"
            ).get()
        val filter = shadowJar.dependencyFilter.get()
        val relocators = shadowJar.relocators.get()

        val kcRoot = distDir.get().asFile
        // Complete only when lib/lib/main exists; a half-moved unpack leaves subdirs.
        if (!File(kcRoot, "lib/lib/main").isDirectory) {
            kcRoot.deleteRecursively()
            kcRoot.parentFile.mkdirs()
            val url = URI(
                "https://github.com/keycloak/keycloak/releases/download/" +
                    "$kcVerValue/keycloak-$kcVerValue.zip"
            ).toURL()
            logger.lifecycle("[checkKeycloakClasspathOverlap] downloading $url")
            val tmpZip = File(kcRoot.parentFile, "keycloak-$kcVerValue.zip")
            val staging = File(kcRoot.parentFile, "keycloak-$kcVerValue-tmp")
            try {
                url.openStream().use { input ->
                    tmpZip.outputStream().use { output -> input.copyTo(output) }
                }
                staging.deleteRecursively()
                project.copy {
                    from(project.zipTree(tmpZip))
                    into(staging)
                }
                val top =
                    staging.listFiles { f -> f.isDirectory }!!.singleOrNull()
                        ?: throw GradleException(
                            "checkKeycloakClasspathOverlap: downloaded " +
                                "Keycloak zip did not unpack to a single " +
                                "top-level directory."
                        )
                if (!top.renameTo(kcRoot)) {
                    throw GradleException(
                        "checkKeycloakClasspathOverlap: could not move " +
                            "unpacked Keycloak $kcVerValue to $kcRoot."
                    )
                }
            } finally {
                tmpZip.delete()
                staging.deleteRecursively()
            }
        }

        // --- Gather the resolved twilio runtime-classpath jars (pre-shade). ---
        val twilioArtifacts =
            overlapRuntimeCfg.get().resolvedConfiguration.resolvedArtifacts
                .map { art ->
                    val id = art.moduleVersion.id
                    Triple(id.group, id.name, id.version) to art.file
                }
        val twilioJars = twilioArtifacts.sortedBy { it.second.absolutePath }

        // A jar is excluded iff the live filter omits it (surviving files = not excluded).
        val survivingFiles = filter.resolve(overlapRuntimeCfg.get()).files
        val coveredByExclude: Set<File> =
            twilioJars.map { it.second }.toSet() - survivingFiles

        // --- Scan the Keycloak runtime jars (prune deployment subtree) ---
        val kcJars = overlapGatherKeycloakJars(kcRoot)
        if (kcJars.isEmpty()) {
            throw GradleException(
                "checkKeycloakClasspathOverlap: no .jar files found under " +
                    "${kcRoot}/lib or ${kcRoot}/providers; the distribution " +
                    "may not have unpacked correctly."
            )
        }
        val kcIndex = mutableMapOf<String, MutableList<File>>()
        for (kj in kcJars) {
            for (cls in overlapListClasses(kj, overlapJavaTarget)) {
                kcIndex.getOrPut(cls) { mutableListOf() }.add(kj)
            }
        }

        // --- Overlap: per twilio jar, classes that also appear in Keycloak ---
        val overlap = linkedMapOf<File, MutableMap<String, MutableList<File>>>()
        for ((_, tjar) in twilioJars) {
            for (cls in overlapListClasses(tjar, overlapJavaTarget)) {
                val kjs = kcIndex[cls] ?: continue
                overlap.getOrPut(tjar) { mutableMapOf() }
                    .getOrPut(cls) { mutableListOf() }.addAll(kjs)
            }
        }

        val totalOverlappingClasses = overlap.values.sumOf { it.size }
        val twilioJarsWithOverlap = overlap.size

        val covered = mutableListOf<File>()
        val uncovered = linkedMapOf<File, MutableMap<String, MutableList<File>>>()
        for ((tjar, classes) in overlap) {
            if (tjar in coveredByExclude) covered.add(tjar)
            else uncovered[tjar] = classes
        }

        overlapPrintReport(
            kcRoot, twilioJars, kcJars, coveredByExclude.size, relocators,
            overlap, totalOverlappingClasses, twilioJarsWithOverlap,
            covered, uncovered, twilioArtifacts,
        )
    }
}

// ---------------------------------------------------------------------------
// Post-shade "no leftover classes" check
// ---------------------------------------------------------------------------
// checkKeycloakShading builds the shadowJar and FAILs unless every shipped
// .class is from an excluded jar, a relocated entry under
// org.philanthropydatacommons.shadow.*, or the provider's own package; it also
// FAILs on a relocate miss (a claimed class not renamed in the built jar) and
// on a misdirected relocator (destination outside the shadow namespace).
// "Relocated" is verified via the public Relocator API against the BUILT jar.
tasks.register("checkKeycloakShading") {
    group = "verification"
    description =
        "Build the shadowJar and fail if any shipped .class is neither " +
            "excluded nor relocated (no leftover un-relocated classes), " +
            "and fail if any class a relocator claims was not actually " +
            "renamed in the built fat jar."

    dependsOn("shadowJar")

    doLast {
        val shadowJarTask =
            tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>(
                "shadowJar"
            ).get()
        val fatJar = shadowJarTask.archiveFile.get().asFile
        if (!fatJar.isFile) {
            throw GradleException(
                "checkKeycloakShading: shadowJar produced no output at " +
                    "$fatJar; run shadowJar first."
            )
        }
        val relocators = shadowJarTask.relocators.get()
        val filter = shadowJarTask.dependencyFilter.get()

        // Jars whose classes ship in the fat jar (must be relocated or be our own).
        val survivingJars: Set<File> =
            filter.resolve(overlapRuntimeCfg.get()).files

        val ownPackageClassPrefix = "org/philanthropydatacommons/auth/"

        // Expected shaded entry for each class a relocator claims; only these + ownPackage may ship.
        // A destination outside org.philanthropydatacommons.shadow.* is rejected (not accepted)
        // so a no-op or wrong-namespace relocator cannot validate itself by matching its own output.
        val shadowNamespacePrefix = "org.philanthropydatacommons.shadow."
        val expectedShadedEntries = mutableSetOf<String>()
        val misdirected = mutableListOf<Pair<String, String>>()
        val misdirectedEntries = mutableSetOf<String>()
        for (jarPath in survivingJars) {
            for (cls in overlapListClasses(jarPath, overlapJavaTarget)) {
                val fqn = cls.removeSuffix(".class").replace("/", ".")
                val reloc = relocators.firstOrNull { it.canRelocateClass(fqn) }
                    ?: continue
                val shaded = reloc.relocateClass(
                    com.github.jengelman.gradle.plugins.shadow.relocation
                        .RelocateClassContext(fqn)
                )
                if (!shaded.startsWith(shadowNamespacePrefix)) {
                    misdirected.add(fqn to shaded)
                    misdirectedEntries.add(shaded.replace(".", "/") + ".class")
                    continue
                }
                expectedShadedEntries.add(shaded.replace(".", "/") + ".class")
            }
        }

        // Leftover: shipped class that is neither ownPackage nor a recomputed shaded entry.
        val fatClasses = overlapListClasses(fatJar, overlapJavaTarget)
        val leftover = mutableListOf<String>()
        for (cls in fatClasses) {
            if (cls.startsWith(ownPackageClassPrefix)) continue
            if (cls in expectedShadedEntries) continue
            if (cls in misdirectedEntries) continue
            leftover.add(cls.removeSuffix(".class").replace("/", "."))
        }

        // Relocate miss: claimed source class whose shaded entry is absent from the built jar.
        val relocateMisses = mutableListOf<Pair<String, String>>()
        for (jarPath in survivingJars) {
            for (cls in overlapListClasses(jarPath, overlapJavaTarget)) {
                val fqn = cls.removeSuffix(".class").replace("/", ".")
                val reloc = relocators.firstOrNull { it.canRelocateClass(fqn) }
                    ?: continue
                val shaded = reloc.relocateClass(
                    com.github.jengelman.gradle.plugins.shadow.relocation
                        .RelocateClassContext(fqn)
                )
                if (!shaded.startsWith(shadowNamespacePrefix)) continue
                val shadedEntry = shaded.replace(".", "/") + ".class"
                if (shadedEntry !in fatClasses) {
                    relocateMisses.add(fqn to shadedEntry)
                }
            }
        }

        overlapShadingPrint(fatJar, fatClasses.size,
            expectedShadedEntries.size, leftover, relocateMisses, misdirected)
    }
}

val overlapMrEntryPattern = Pattern.compile("^META-INF/versions/(\\d+)/(.+)$")

// Read the exact Multi-Release main attribute; a prefix scan would match a
// named-section attribute such as Multi-Release-Status and false-positive.
fun overlapIsMultiRelease(zf: ZipFile): Boolean {
    val entry = zf.getEntry("META-INF/MANIFEST.MF") ?: return false
    return zf.getInputStream(entry).use { input ->
        val value = Manifest(input).mainAttributes.getValue("Multi-Release")
        value != null && value.trim().lowercase() == "true"
    }
}

// Return the logical .class entry names in a jar. In a multi-release jar
// (manifest Multi-Release: true), META-INF/versions/<n>/<x> overrides <x>
// only on a runtime >= <n>, so n <= javaTarget is normalized and n > it is
// dropped; in a non-MR jar, versions/ entries are dropped. module-info.class
// and directories are excluded. Read errors throw (fail loudly).
fun overlapListClasses(jar: File, javaTarget: Int): Set<String> {
    return try {
        ZipFile(jar).use { zf ->
            val classes = mutableSetOf<String>()
            val mr = overlapIsMultiRelease(zf)
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.endsWith("/") || !name.endsWith(".class")) continue
                val m = overlapMrEntryPattern.matcher(name)
                if (m.matches()) {
                    if (!mr || m.group(1).toInt() > javaTarget) continue
                    val logical = m.group(2)
                    if (logical == "module-info.class" ||
                        logical.endsWith("/module-info.class")) continue
                    classes.add(logical)
                } else {
                    if (name == "module-info.class" ||
                        name.contains("/module-info.class")) continue
                    classes.add(name)
                }
            }
            classes
        }
    } catch (e: ZipException) {
        throw GradleException(
            "checkKeycloakClasspathOverlap: could not read jar $jar: ${e.message}", e
        )
    } catch (e: java.io.IOException) {
        throw GradleException(
            "checkKeycloakClasspathOverlap: could not read $jar: ${e.message}", e
        )
    }
}

// Find every .jar on the Keycloak Quarkus runtime classpath (26.x layout):
//   lib/lib/main        -- main runtime classpath
//   lib/lib/boot        -- boot/launcher classloader
//   lib/app             -- the Keycloak application itself (keycloak.jar)
//   lib/quarkus         -- Quarkus-generated/transformed runtime bytecode
//   lib/lib/deployment  -- build-time ONLY (pruned; would cause false overlaps)
//   providers           -- user-dropped provider jars (this shadow jar)
fun overlapGatherKeycloakJars(kcRoot: File): List<File> {
    val deploymentSub = File(kcRoot, "lib/lib/deployment")
    val out = mutableListOf<File>()
    for (sub in listOf("lib", "providers")) {
        val base = File(kcRoot, sub)
        if (!base.isDirectory) continue
        base.walkTopDown()
            .onEnter { dir -> dir.absolutePath != deploymentSub.absolutePath }
            .forEach { f -> if (f.isFile && f.name.endsWith(".jar")) out.add(f) }
    }
    return out.sortedBy { it.absolutePath }
}

fun overlapClassRelocates(fqn: String, relocators: Set<Relocator>): Boolean =
    relocators.any { it.canRelocateClass(fqn) }

// Print the post-shade verdict, throwing on failure. Passes iff no leftover
// (every shipped .class is ownPackage or relocated) and no relocate misses.
fun overlapShadingPrint(
    fatJar: File,
    fatClassCount: Int,
    expectedShadedCount: Int,
    leftover: List<String>,
    relocateMisses: List<Pair<String, String>>,
    misdirected: List<Pair<String, String>>,
) {
    val inGithubActions = System.getenv("GITHUB_ACTIONS") == "true"
    val bar = "=".repeat(78)
    val dash = "-".repeat(78)
    println(bar)
    println("Post-shade \"no leftover classes\" report (checkKeycloakShading)")
    println(bar)
    println("Built fat jar:                 $fatJar")
    println("  .class entries shipped:       $fatClassCount")
    println("  expected relocated entries:   $expectedShadedCount")
    println("Leftover un-relocated classes: ${leftover.size}")
    println("Relocate misses:               ${relocateMisses.size}")
    println("Misdirected relocators:        ${misdirected.size}")
    println(dash)

    if (leftover.isEmpty() && relocateMisses.isEmpty() && misdirected.isEmpty()) {
        println("OK: every shipped .class is either the provider's own package")
        println("(org.philanthropydatacommons.auth) or a relocated entry under")
        println("org.philanthropydatacommons.shadow.*, and every class a")
        println("relocator claims was actually renamed in the built fat jar.")
        if (inGithubActions) {
            println(
                "::notice::Post-shade OK: $fatClassCount shipped .class " +
                    "entries; 0 leftover, 0 relocate misses."
            )
        }
        return
    }

    if (leftover.isNotEmpty()) {
        println(
            "FAIL (leftover): ${leftover.size} shipped .class entries are " +
                "neither the provider's own package nor a relocated entry " +
                "(they would sit on the classpath under their original " +
                "package and may collide with or be shadowed by Keycloak's " +
                "own copies). Add a `relocate(...)` for the offending " +
                "package, or `exclude(dependency(...))` the jar if it is " +
                "already on Keycloak's classpath or unused."
        )
        if (inGithubActions) {
            println(
                "::error::Post-shade leftover: ${leftover.size} shipped " +
                    ".class entries are un-relocated and not the provider's " +
                    "own package; see the job log."
            )
        }
        for (fqn in leftover.sorted()) println("  leftover: $fqn")
        println()
    }
    if (relocateMisses.isNotEmpty()) {
        println(
            "FAIL (relocate miss): ${relocateMisses.size} class(es) a " +
                "relocator claims were not actually renamed in the built " +
                "fat jar -- the expected shaded entry is absent. This is " +
                "a shadow-plugin or configuration bug (e.g. a multi-" +
                "release-jar versioned entry that slipped through)."
        )
        if (inGithubActions) {
            println(
                "::error::Post-shade relocate miss: ${relocateMisses.size} " +
                    "claimed class(es) were not renamed in the built fat " +
                    "jar; see the job log."
            )
        }
        for ((src, shaded) in relocateMisses.sortedBy { it.first }) {
            println("  relocate miss: $src -> (missing) $shaded")
        }
        println()
    }
    if (misdirected.isNotEmpty()) {
        println(
            "FAIL (misdirected): ${misdirected.size} relocator(s) direct " +
                "classes outside org.philanthropydatacommons.shadow.* -- the " +
                "shaded jar would ship classes under their original (or " +
                "wrong) package and may collide with or be shadowed by " +
                "Keycloak. Point every relocate(...) at " +
                "org.philanthropydatacommons.shadow.<sub>."
        )
        if (inGithubActions) {
            println(
                "::error::Post-shade misdirected: ${misdirected.size} " +
                    "relocator(s) direct outside the shadow namespace; " +
                    "see the job log."
            )
        }
        for ((src, shaded) in misdirected.sortedBy { it.first }) {
            println("  misdirected: $src -> $shaded")
        }
        println()
    }
    throw GradleException(
        "checkKeycloakShading: ${leftover.size} leftover un-relocated " +
            "class(es), ${relocateMisses.size} relocate miss(es), and " +
            "${misdirected.size} misdirected relocator(s) in the built " +
            "shadow jar. Every shipped class must be either excluded, " +
            "relocated under org.philanthropydatacommons.shadow.*, or the " +
            "provider's own package (org.philanthropydatacommons.auth)."
    )
}

fun overlapPrintReport(
    kcRoot: File,
    twilioJars: List<Pair<Triple<String, String, String>, File>>,
    kcJars: List<File>,
    excludedCount: Int,
    relocators: Set<Relocator>,
    overlap: Map<File, Map<String, List<File>>>,
    totalOverlappingClasses: Int,
    twilioJarsWithOverlap: Int,
    covered: List<File>,
    uncovered: Map<File, Map<String, List<File>>>,
    twilioArtifacts: List<Pair<Triple<String, String, String>, File>>,
) {
    val inGithubActions = System.getenv("GITHUB_ACTIONS") == "true"
    val bar = "=".repeat(78)
    val dash = "-".repeat(78)
    val coordsFor: (File) -> String = { tjar ->
        twilioArtifacts.firstOrNull { it.second == tjar }
            ?.let { "${it.first.first}:${it.first.second}:${it.first.third}" }
            ?: "(unknown coords)"
    }
    println(bar)
    println("Pre-shade twilio-dependency vs Keycloak-runtime class overlap report")
    println(bar)
    println("Twilio runtime-classpath jars:  ${twilioJars.size}")
    println("Keycloak root:                 $kcRoot")
    println("  jars scanned:                ${kcJars.size}")
    println(
        "shadowJar excludes live:       $excludedCount " +
            "(from ShadowJar.dependencyFilter)"
    )
    println(
        "shadowJar relocates live:      ${relocators.size} " +
            "(from ShadowJar.relocators)"
    )
    println("Twilio jars with overlap:      $twilioJarsWithOverlap")
    println("Overlapping classes (total):   $totalOverlappingClasses")
    println(dash)

    if (overlap.isEmpty()) {
        println("OK: no .class entry in any twilio runtime-classpath jar is also")
        println("present in a Keycloak runtime jar. Twilio pulls in nothing that")
        println("collides with Keycloak's runtime classpath.")
        if (inGithubActions) {
            println(
                "::notice::No pre-shade class overlap between the twilio " +
                    "runtime classpath (${twilioJars.size} jars) and Keycloak " +
                    "runtime (${kcJars.size} jars)."
            )
        }
        return
    }

    for ((tjar, classes) in overlap.toSortedMap(compareBy { it.absolutePath })) {
        val status = if (tjar in covered) "COVERED-exclude" else "NOT-EXCLUDED"
        println(
            "  Twilio-side jar: ${tjar.name}  [${coordsFor(tjar)}]  " +
                "(${classes.size} overlapping class(es))  -> $status"
        )
        val byKc = mutableMapOf<File, MutableList<String>>()
        for ((cls, kjars) in classes) {
            for (kj in kjars) {
                byKc.getOrPut(kj) { mutableListOf() }.add(cls)
            }
        }
        for (kj in byKc.toSortedMap(compareBy { it.absolutePath })) {
            println(
                "    Keycloak jar: ${kj.key.relativeTo(kcRoot)}  " +
                    "(${kj.value.size} class(es))"
            )
            for (c in kj.value.sorted()) {
                val fqn = c.removeSuffix(".class").replace("/", ".")
                val note = if (overlapClassRelocates(fqn, relocators))
                    "  [relocated]" else ""
                println("      - $fqn$note")
            }
        }
        println()
    }
    overlapPrintVerdict(
        kcRoot, covered, uncovered, totalOverlappingClasses,
        twilioJarsWithOverlap, coordsFor, inGithubActions, dash,
    )
}

fun overlapPrintVerdict(
    kcRoot: File,
    covered: List<File>,
    uncovered: Map<File, Map<String, List<File>>>,
    totalOverlappingClasses: Int,
    twilioJarsWithOverlap: Int,
    coordsFor: (File) -> String,
    inGithubActions: Boolean,
    dash: String,
) {
    val uncoveredClasses = uncovered.values.sumOf { map -> map.size }
    println("COVERED by exclude(...):      ${covered.size} twilio jar(s)")
    println(
        "NOT EXCLUDED (fail):          ${uncovered.size} twilio jar(s), " +
            "$uncoveredClasses class(es)"
    )

    if (uncovered.isEmpty()) {
        println("OK: every pre-shade overlap is excluded -- each overlapping twilio")
        println("jar matches an exclude(dependency(...)) in the shadowJar task, so")
        println("the whole jar is removed from the fat jar. (relocate(...) directives")
        println("are defensive and were not counted as coverage.)")
        if (inGithubActions) {
            println(
                "::notice::All pre-shade overlaps excluded: " +
                    "$totalOverlappingClasses class(es) across " +
                    "$twilioJarsWithOverlap twilio jar(s) overlap Keycloak " +
                    "runtime but every overlapping jar is excluded in the " +
                    "shadowJar task."
            )
        }
        return
    }

    println(dash)
    println(
        "FAIL: $uncoveredClasses class(es) across ${uncovered.size} " +
            "twilio-side jar(s) overlap with Keycloak runtime jars and are " +
            "NOT excluded by any exclude(dependency(...)) in the shadowJar " +
            "task. The shadow jar would ship these classes (relocation is " +
            "defensive only and does not satisfy this check). Add an " +
            "exclude(dependency(...)) for each overlapping jar to keep the " +
            "fat jar small."
    )
    if (inGithubActions) {
        println(
            "::error::Pre-shade not-excluded overlap: $uncoveredClasses " +
                "class(es) across ${uncovered.size} twilio jar(s) collide " +
                "with Keycloak runtime jars and are not excluded in the " +
                "shadowJar task; see the job log."
        )
    }
    println("NOT-EXCLUDED overlaps (only):")
    for ((tjar, classes) in uncovered.toSortedMap(compareBy { it.absolutePath })) {
        println(
            "  Twilio-side jar: ${tjar.name}  [${coordsFor(tjar)}]  " +
                "(${classes.size} not-excluded class(es))"
        )
        val byKc = mutableMapOf<File, MutableList<String>>()
        for ((cls, kjars) in classes) {
            for (kj in kjars) {
                byKc.getOrPut(kj) { mutableListOf() }.add(cls)
            }
        }
        for (kj in byKc.toSortedMap(compareBy { it.absolutePath })) {
            println(
                "    Keycloak jar: ${kj.key.relativeTo(kcRoot)}  " +
                    "(${kj.value.size} class(es))"
            )
            for (c in kj.value.sorted()) {
                val fqn = c.removeSuffix(".class").replace("/", ".")
                println("      - $fqn")
            }
        }
        println()
    }
    throw GradleException(
        "checkKeycloakClasspathOverlap: $uncoveredClasses class(es) across " +
            "${uncovered.size} twilio-side jar(s) overlap Keycloak runtime " +
            "jars and are NOT excluded in the shadowJar task."
    )
}
