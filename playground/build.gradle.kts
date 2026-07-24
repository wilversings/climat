plugins {
    kotlin("multiplatform") version "2.4.10"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)

    js {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
        useCommonJs()
    }

    sourceSets {
        val jsMain by getting {
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
            dependencies {
                implementation(project(":climatEngine"))

                // CodeMirror 6 editor (the `codemirror` package re-exports basicSetup + EditorView).
                implementation(npm("codemirror", "^6.0.1"))
                implementation(npm("@codemirror/view", "^6.34.1"))
                implementation(npm("@codemirror/state", "^6.4.1"))

                // Custom "climat DSL" syntax highlighting (StreamLanguage + a HighlightStyle).
                implementation(npm("@codemirror/language", "^6.12.4"))
                implementation(npm("@lezer/highlight", "^1.2.3"))
            }
        }
    }
}
