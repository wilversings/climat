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
    val linux = linuxX64()
    val nativeTargets = listOf(linux, macosX64(), macosArm64())
    nativeTargets.forEach { target ->
        target.binaries.executable {
            baseName = "climat-msh"
            entryPoint = "com.climat.microshell.main"
        }
    }

    // `platform.posix` drags in Kotlin/Native's bundled posix.klib, whose linux_x64 def
    // hardcodes `linkerOpts = -lresolv -lm -lpthread -lutil -lcrypt -lrt` regardless of which
    // symbols are used. We only need a handful of plain libc calls, so linuxX64 binds its own
    // minimal cinterop (see Posix.kt for the per-target actuals) with no linkerOpts, avoiding
    // libutil/libcrypt/libresolv entirely. macOS has no equivalent problem and keeps using
    // `platform.posix` directly, so it isn't given this cinterop.
    linux.compilations.getByName("main") {
        cinterops {
            create("posix") {
                defFile(project.file("src/nativeInterop/cinterop/posix.def"))
            }
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
