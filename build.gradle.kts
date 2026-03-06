plugins {
    id("com.android.library") version "7.4.2" apply false
    kotlin("android") version "1.8.22" apply false
}

tasks.register("test") {
    dependsOn(":core-network:test")
}

tasks.register("lintDebug") {
    dependsOn(":core-network:lintDebug")
}
