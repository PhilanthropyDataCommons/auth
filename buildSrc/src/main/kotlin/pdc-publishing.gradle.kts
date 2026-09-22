// Convention plugin: Maven publication to GitHub Packages. Applied by each
// subproject via `id("pdc-publishing")`, alongside `id("pdc-versioning")`.
//
// Publishes each plain jar (components["java"], built by the `jar` task) to
// the GitHub Packages Maven registry under group org.philanthropydatacommons.
// The registry is the source of truth for "is this GAV released?"; the
// workflow GETs each jar from the registry before uploading -- a 200 both
// proves the GAV is published and delivers the canonical bytes (so unchanged
// jars are not re-uploaded and release assets are byte-identical to the
// canonical jar). A concurrent publish racing for the same new GAV yields a
// 409 from the registry, tolerated as "already published".

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
            // Every subproject applies `java-library` before this plugin, so the
            // `java` component (the plain `jar` output) is available at
            // registration time.
            from(components["java"])
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
                        val jarFile = (tasks.findByName("jar") as? Jar)?.archiveFile?.get()?.asFile
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
