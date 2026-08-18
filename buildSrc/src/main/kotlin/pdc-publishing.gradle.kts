// Convention plugin: Maven publication to GitHub Packages. Applied by each
// subproject via `id("pdc-publishing")`, alongside `id("pdc-versioning")`.
//
// Publishes each jar to the GitHub Packages Maven registry under group
// org.philanthropydatacommons. For subprojects using the shadow plugin the
// fat jar is published from components["shadow"] (the deployable artifact);
// for plain subprojects from components["java"] (selection deferred to
// afterEvaluate so plugin order does not matter). The plain -plain jar is
// not published. The registry is the source of truth for "is this GAV
// released?"; the workflow GETs each jar from the registry before uploading
// -- a 200 both proves the GAV is published and delivers the canonical bytes
// (so unchanged jars are not re-uploaded and release assets are byte-identical
// to the canonical jar). A concurrent publish racing for the same new GAV
// yields a 409 from the registry, tolerated as "already published". GLM-5.2

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.BasicAuthentication

plugins {
    `maven-publish`
}

publishing {
    repositories {
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/PhilanthropyDataCommons/auth")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        register<MavenPublication>("mavenJava") {
            // artifactId = project name -> GAV org.philanthropydatacommons:<module>:<version>
            artifactId = project.name
            // Defer the software-component selection until all plugins are
            // applied (afterEvaluate), so the order of `id("pdc-publishing")`
            // vs `id("com.gradleup.shadow")` in a subproject's build.gradle.kts
            // does not matter. An eager `plugins.hasPlugin(...)` check here
            // evaluates at publication-registration time -- before shadow is
            // applied in twilio-keycloak-provider (where pdc-publishing is
            // listed before shadow) -- so it would select components["java"]
            // and publish the classifier-only -plain jar instead of the
            // deployable fat jar. The shadow plugin exposes a `shadow`
            // software component whose main artifact is the fat jar; plain
            // subprojects use the `java` component. GLM-5.2
            val publication = this
            project.afterEvaluate {
                val component = if (plugins.hasPlugin("com.gradleup.shadow")) {
                    components["shadow"]
                } else {
                    components["java"]
                }
                publication.from(component)
            }
        }
    }
}

// printPublished: after `publish`, emit the jars uploaded this run. The
// workflow reads this to know which jars to attach to the GitHub Release.
// Records "<module>=<version>|<jar path>" per published jar to
// <root>/build/published.txt, appended by each subproject's publish task.
// GLM-5.2
val publishedFile = rootProject.layout.buildDirectory.file("published.txt").get().asFile

gradle.taskGraph.whenReady(
    object : org.gradle.api.Action<org.gradle.api.execution.TaskExecutionGraph> {
        override fun execute(graph: org.gradle.api.execution.TaskExecutionGraph) {
            for (task in graph.allTasks) {
                val n = task.name
                // Restrict to this plugin instance's project: each subproject
                // applying this plugin registers its own whenReady listener, so
                // without this guard every listener would match every other
                // subproject's publish task by name and record jars that were
                // never uploaded (one upload would record all three modules).
                // GLM-5.2
                if (task.project == project && n.startsWith("publishMavenJavaPublication") && n.endsWith("ToGithubRepository")) {
                    task.doLast {
                        if (task.state.failure != null) return@doLast
                        val jarTask = tasks.findByName("shadowJar") ?: tasks.findByName("jar")
                        val jarFile = (jarTask as? Jar)?.archiveFile?.get()?.asFile
                        publishedFile.parentFile.mkdirs()
                        publishedFile.appendText(
                            "${project.name}=${project.version}|${jarFile?.absolutePath ?: ""}\n",
                        )
                    }
                }
            }
        }
    },
)

tasks.register("printPublished") {
    doLast {
        val f = rootProject.layout.buildDirectory.file("published.txt").get().asFile
        if (f.exists()) {
            f.readLines().forEach { println(it) }
        }
    }
}
