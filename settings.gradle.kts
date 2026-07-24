plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "climat"
include("climatEngine")
include("integrationTests")
include("playground")
