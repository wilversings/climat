plugins {
    kotlin("multiplatform") version "2.4.10"
}

kotlin {

    jvmToolchain(25)

    js {
        binaries.library()
        nodejs {
            testTask {
                useMocha {
                    timeout = "30000"
                }
            }
        }
        useCommonJs()
    }

    sourceSets {
        val jsTest by getting {
            kotlin.srcDir("src/test/kotlin")
            resources.srcDir("src/test/resources")

            dependencies {
                implementation(kotlin("test"))

                // https://github.com/Kotlin/kotlinx-nodejs/issues/16
                implementation(files("../lib/kotlinx-nodejs-0.0.7.klib"))

                implementation("io.kotest:kotest-assertions-core:6.2.3")

                runtimeOnly(rootProject)
            }
        }
    }

}
