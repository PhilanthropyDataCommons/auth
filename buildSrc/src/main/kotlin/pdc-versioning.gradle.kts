// Convention plugin: per-jar versioning from git. Applied by each subproject
// via `id("pdc-versioning")`.
//
// Each jar's version is the UTC committer date (YYYYMMDD) plus the first seven
// characters of the commit SHA1 of the most recent commit that touched either
// shared build infrastructure (everything outside the component subdirectories
// -- repo-root files plus .github, gradle, buildSrc, and any future top-level
// directory) or this jar's own subdirectory, whichever is later. So a change
// to gradlew, the workflow files, gradle config, or buildSrc bumps every jar;
// a change inside one subdirectory bumps only that jar. This is the scheme
// documented in README.md (example
// pdc-keycloak-theme-20240313-05793e4.jar). If git is unavailable or no commit
// matches, the version falls back to 0.0.0-SNAPSHOT so IDE imports still work.
//
// The "most recent commit touching shared build infra OR this subdir" is found
// with a single `git log -1` over the union of those pathspecs (positive "."
// minus the OTHER component subdirs, plus this subdir), so git's own commit
// ordering decides the newest deterministically (no separate timestamp
// comparison that could tie-break wrong on equal %ct values). GLM-5.2

import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
}

// Component subdirectories to exclude from the "shared build infra" pathspecs.
// Derived from the project model that settings.gradle.kts builds (its `include`
// declarations) rather than hardcoded here, so this shared plugin does not have
// to enumerate the very subprojects that apply it. .path uses the OS file
// separator (backslash on Windows), but git pathspecs require forward slashes,
// so normalize to '/' -- a no-op on Linux/macOS, essential for correct
// per-jar versioning on Windows. GLM-5.2
val componentSubdirs = rootProject.subprojects.map {
    it.projectDir.relativeTo(rootProject.projectDir).path.replace('\\', '/')
}

// Compute this project's version from git. GLM-5.2
val computedVersion: String by lazy {
    val rootDir = rootProject.projectDir
    val subdir = projectDir.relativeTo(rootDir).path.replace('\\', '/') // e.g. "pdc-keycloak-theme"

    fun runGit(vararg args: String): String {
        val pb = ProcessBuilder("git", *args).directory(rootDir)
        // Force the C locale so git's diagnostics are always English. The
        // not-a-git-repo fallback below matches git's literal stderr text "not
        // a git repository"; on a non-English developer machine that text
        // would be localized and the probe would rethrow instead of falling
        // back to 0.0.0-SNAPSHOT. LC_ALL=C overrides LANG/LC_* (and makes
        // GNU gettext ignore LANGUAGE), so it is the single sufficient knob.
        // GLM-5.2
        pb.environment()["LC_ALL"] = "C"
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        val err = proc.errorStream.bufferedReader().readText().trim()
        val status = proc.waitFor()
        if (status != 0) {
            // Include git's stderr in the message so callers can distinguish
            // *why* git failed (e.g. "not a git repository" vs "bad object"),
            // and so a propagated failure still surfaces git's diagnostic
            // (previously redirectError(INHERIT) printed it live; capturing it
            // here preserves it in the exception for the same visibility).
            // GLM-5.2
            throw GradleException("git ${args.joinToString(" ")} failed with exit $status: $err")
        }
        return out
    }

    // The ONLY conditions we tolerate as a 0.0.0-SNAPSHOT fallback (so IDE
    // imports work outside a real checkout) are "the git binary is missing"
    // and "we are not inside a git work tree". We probe with
    // `git rev-parse --is-inside-work-tree`. The catches below inspect the
    // exception *message* and fall back ONLY for those two known cases; any
    // other failure re-throws so it surfaces instead of silently shipping
    // 0.0.0-SNAPSHOT. In particular, git's exit 128 is its generic *fatal*
    // status and fires for BOTH "not a git repository" AND "corrupt objects"
    // (or a pathspec error), so matching on the exit code alone is too broad;
    // we instead match git's literal stderr text "not a git repository",
    // which is the reliable not-a-repo signal. Other exception types
    // (InterruptedException from waitFor, any RuntimeException bug) are not
    // caught at all. GLM-5.2
    fun insideWorkTree(): Boolean =
        try {
            runGit("rev-parse", "--is-inside-work-tree") == "true"
        } catch (e: IOException) {
            // ProcessBuilder.start() throws an IOException whose message is
            // "Cannot run program \"git\": ... No such file or directory" when
            // the git binary is missing/unrunnable. Only that case falls back;
            // any other IOException is unexpected and propagates. GLM-5.2
            val msg = e.message ?: ""
            if (msg.contains("No such file or directory") || msg.contains("Cannot run program")) {
                logger.warn("[pdc-versioning] ${project.name}: git binary unavailable ($msg) -> 0.0.0-SNAPSHOT")
                false
            } else {
                throw e
            }
        } catch (e: GradleException) {
            // runGit includes git's stderr in its message. Only the literal
            // "not a git repository" stderr is the reliable not-a-repo signal;
            // git's exit 128 also fires for corrupt objects / pathspec errors,
            // so matching the exit code would mask those. A GradleException
            // whose message does not contain "not a git repository" (e.g. a
            // corrupt object DB -> "bad object", a pathspec error) propagates.
            // GLM-5.2
            if (e.message?.contains("not a git repository") == true) {
                logger.warn("[pdc-versioning] ${project.name}: not a git repository -> 0.0.0-SNAPSHOT")
                false
            } else {
                throw e
            }
        }

    if (!insideWorkTree()) {
        // insideWorkTree() already logged the specific reason for the fallback. GLM-5.2
        return@lazy "0.0.0-SNAPSHOT"
    }

    // Union of: shared build infra (everything except the component subdirs)
    // plus this project's own subdirectory. A single `git log -1` over this
    // union yields the most recent commit touching either, deterministically.
    // Exclude only the OTHER component subdirs: including the current
    // component in the excludes would remove it from the set, and git's
    // :(exclude) pathspec wins over the later positive <subdir> pathspec
    // (a path matched by an exclude stays excluded and cannot be
    // re-included by a positive pathspec), so commits touching only this
    // component would never bump this component's version. GLM-5.2
    val excludesArgs = componentSubdirs
        .filter { it != subdir }
        .flatMap { listOf(":(exclude)${it}/") }
        .toTypedArray()
    val pathArgs = arrayOf(".", *excludesArgs, subdir)
    val commit = runGit("log", "-1", "--format=%H", "--", *pathArgs)
    if (commit.isEmpty()) {
        logger.warn("[pdc-versioning] ${project.name}: no commits matched -> 0.0.0-SNAPSHOT")
        return@lazy "0.0.0-SNAPSHOT"
    }
    val tsUtc = runGit("show", "-s", "--format=%ct", commit).toLong()
    val dateUtc = Instant.ofEpochSecond(tsUtc)
        .atZone(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    // Take the first seven characters of the full hash directly. `git rev-parse
    // --short=7` returns AT LEAST seven characters and lengthens the abbreviation
    // when a seven-character prefix collides in the object database, which would
    // violate the documented fixed <sha7> format. commit already holds the full
    // hash, so a fixed substring is correct and stable. GLM-5.2
    val sha7 = commit.take(7)
    "${dateUtc}-${sha7}"
}

project.group = "org.philanthropydatacommons"
project.version = computedVersion

// Stamp the version into every jar's manifest. (Reproducibility settings --
// isReproducibleFileOrder / isPreserveFileTimestamps -- live in the root
// build.gradle.kts so they apply to every subproject from one central place
// rather than being coupled to the versioning plugin.) GLM-5.2
tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Implementation-Version" to project.version)
        attributes("Implementation-Title" to project.name)
    }
}

// printVersion: emit "<module>=<version>" so the workflow/humans can read it.
// GLM-5.2
tasks.register("printVersion") {
    doLast {
        println("${project.name}=${project.version}")
    }
}
