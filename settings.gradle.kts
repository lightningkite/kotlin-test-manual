plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "kotlin-test-manual"

include(":kotlin-test-manual-plugin")
include(":kotlin-test-manual-runtime")