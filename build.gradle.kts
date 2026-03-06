plugins {
    id("com.android.application") version "7.4.2" apply false
    id("com.android.library") version "7.4.2" apply false
    kotlin("android") version "1.8.22" apply false
}

tasks.register("test") {
    dependsOn(":app:testDebugUnitTest")
    dependsOn(":core-network:test")
}

tasks.register("lintDebug") {
    dependsOn(":app:lintDebug")
    dependsOn(":core-network:lintDebug")
}

tasks.register("connectedDebugAndroidTest") {
    dependsOn(":app:connectedDebugAndroidTest")
}
