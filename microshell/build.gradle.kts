plugins {
    kotlin("multiplatform") version "2.4.10"
}

kotlin {
    jvmToolchain(25)

    // Consumed by climatEngine (parsing + plan model). No Node APIs live here.
    js {
        binaries.library()
        nodejs {
            testTask {
                useMocha()
            }
        }
        useCommonJs()
    }

    // The executor. Apple targets only link on a macOS host; CI builds those.
    val nativeTargets = listOf(linuxX64(), macosX64(), macosArm64())
    nativeTargets.forEach { target ->
        target.binaries.executable {
            baseName = "climat-msh"
            entryPoint = "com.climat.microshell.main"
        }
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
