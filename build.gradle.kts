plugins {
    kotlin("multiplatform") version "2.4.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    // id("com.dorongold.task-tree") version "2.1.1"
}

fun getProp(name: String) = project.properties[name].toString()

group = getProp("group")
version = getProp("version")

allprojects {
    repositories {
        mavenCentral()
    }
}

kotlin {

    jvmToolchain(25)

    js {
        compilations["main"].packageJson {
            description = getProp("description")
            customField(
                "bin",
                mapOf(
                    "climat" to "./kotlin/climat.js",
                ),
            )
            arrayOf("repository", "homepage", "author", "license", "description")
                .forEach {
                    customField(it, getProp(it))
                }
            customField("keywords", getProp("keywords").split(","))
            customField(
                "engines",
                mapOf(
                    "node" to ">=22.23",
                ),
            )
        }

        // Not really for browser, but it is the only way to use webpack
        browser {
            webpackTask {
                mainOutputFileName.set("kotlin/climat.js")
                output.libraryTarget = "commonjs2"
            }
            useCommonJs()
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            kotlin.srcDirs("src/main/kotlin")
            dependencies {
                implementation(devNpm("copy-webpack-plugin", "^12.0.2"))

                implementation(npm("colors", "^1.4.0"))
                implementation(npm("fs-extra", "11.1.0"))
                implementation(npm("node-fetch", "^2.6.8"))
                implementation(npm("untildify", "^4.0.0"))
                implementation(npm("fs-extra", "11.1.0"))
                implementation(npm("yesno", "^0.4.0"))

                implementation(project("climatEngine"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

                // https://github.com/Kotlin/kotlinx-nodejs/issues/16
                implementation(files("./lib/kotlinx-nodejs-0.0.7.klib"))

                implementation("org.lighthousegames:logging-js:1.5.0")
            }
        }
    }

    ktlint.ignoreFailures = true

    ktlint.filter {
        exclude { it.file.path.contains("generated") }
    }
}

// Every supported microshell target ships inside the single npm package. At ~520 KB each that is
// ~1.5 MB total — cheap enough to avoid per-platform packages and their publish-ordering hazards.
val microshellTargets =
    mapOf(
        "LinuxX64" to ("linuxX64" to "linux-x64"),
        "MacosX64" to ("macosX64" to "darwin-x64"),
        "MacosArm64" to ("macosArm64" to "darwin-arm64"),
    )

tasks {
    register<Copy>("copyLicenceAndReadme") {
        from("LICENSE.md", "README.md")
        into("build/js/packages/${project.name}")
        dependsOn("kotlinNpmInstall")
    }

    register<Copy>("copyMicroshellBinaries") {
        description = "Stages the microshell binaries so webpack can bundle them into the npm package."
        dependsOn("kotlinNpmInstall")
        val microshell = project(":microshell")
        microshellTargets.forEach { (taskSuffix, names) ->
            val (buildDirName, npmName) = names
            // Apple targets are disabled on a Linux host (and vice versa); those links are skipped
            // and simply contribute no file, so a local build still produces a working package for
            // the host platform.
            dependsOn("${microshell.path}:linkReleaseExecutable$taskSuffix")
            from(microshell.layout.buildDirectory.file("bin/$buildDirName/releaseExecutable/climat-msh.kexe")) {
                rename { "climat-msh-$npmName" }
            }
        }
        into("build/js/packages/${project.name}/msh")
    }

    named("jsBrowserProductionWebpack") {
        dependsOn("copyLicenceAndReadme", "copyMicroshellBinaries")
    }

    named("jsBrowserDistribution") {
        // webpack's CopyPlugin does not preserve file modes, so the binaries arrive non-executable
        // and every `act microsh` would die with EACCES. Restore the bit on the packaged output.
        doLast {
            layout.buildDirectory
                .dir("dist/js/productionExecutable/msh")
                .get()
                .asFile
                .listFiles()
                ?.forEach { it.setExecutable(true, false) }
        }
    }
}

// --- Combined GitHub Pages site ---
// GitHub Pages only serves one site per repo, so the docs (Docusaurus) and
// playground (Kotlin/JS) static sites are merged into a single output:
//   /            -> redirects to /docs/
//   /docs/       -> the Docusaurus site
//   /playground/ -> the playground app
val ghPagesBasePath = (findProperty("ghPagesBasePath") as String?) ?: "/${rootProject.name}"
val ghPagesDir = layout.buildDirectory.dir("gh-pages")
val docsDir = file("docs")
val docsBuildDir = docsDir.resolve("build")

tasks {
    register<Exec>("npmInstallDocs") {
        workingDir = docsDir
        commandLine("npm", "ci")
        inputs.file(docsDir.resolve("package-lock.json"))
        outputs.dir(docsDir.resolve("node_modules"))
    }

    register<Exec>("buildDocsSite") {
        dependsOn("npmInstallDocs")
        workingDir = docsDir
        commandLine("npm", "run", "build")
        environment("DOCUSAURUS_BASE_URL", "$ghPagesBasePath/docs/")
        inputs.dir(docsDir.resolve("docs"))
        inputs.dir(docsDir.resolve("src"))
        inputs.dir(docsDir.resolve("static"))
        inputs.file(docsDir.resolve("docusaurus.config.js"))
        inputs.file(docsDir.resolve("sidebars.js"))
        outputs.dir(docsBuildDir)
    }

    register<Copy>("assembleGithubPages") {
        group = "distribution"
        description = "Builds docs and playground and merges them into a single GitHub Pages site."
        dependsOn("buildDocsSite", ":playground:jsBrowserDistribution")

        into(ghPagesDir)
        from(docsBuildDir) { into("docs") }
        from(project(":playground").layout.buildDirectory.dir("dist/js/productionExecutable")) {
            into("playground")
        }

        doLast {
            ghPagesDir.get().asFile.resolve("index.html").writeText(
                """
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="utf-8" />
                    <meta http-equiv="refresh" content="0; url=docs/" />
                    <link rel="canonical" href="docs/" />
                    <title>climat</title>
                </head>
                <body>
                    <p>Redirecting to <a href="docs/">the docs</a>&hellip;</p>
                </body>
                </html>
                """.trimIndent(),
            )
        }
    }
}
