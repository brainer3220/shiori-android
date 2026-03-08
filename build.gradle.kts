import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream
import java.io.File

plugins {
    id("com.android.application") version "7.4.2" apply false
    id("com.android.library") version "7.4.2" apply false
    kotlin("android") version "1.8.22" apply false
}

fun adbExecutable(): String {
    val sdkRoot = System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }
        ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/Library/Android/sdk"
    val candidate = File(sdkRoot, "platform-tools/adb")
    return if (candidate.exists()) candidate.absolutePath else "adb"
}

fun org.gradle.api.Project.runCommand(command: List<String>): Pair<Int, String> {
    val output = ByteArrayOutputStream()
    val result = exec {
        workingDir = rootDir
        commandLine(command)
        standardOutput = output
        errorOutput = output
        isIgnoreExitValue = true
    }
    return result.exitValue to output.toString().trim()
}

val verifyPhoneConnectedDevice = tasks.register("verifyPhoneConnectedDevice") {
    group = "verification"
    description = "Checks that a phone emulator or device is connected"

    doLast {
        val adb = adbExecutable()
        val (devicesExitCode, devicesOutput) = project.runCommand(listOf(adb, "devices"))
        if (devicesExitCode != 0) {
            throw GradleException("Unable to query adb devices. Ensure Android platform-tools are installed and adb is available.")
        }

        val connectedSerials = devicesOutput.lineSequence()
            .drop(1)
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val columns = line.split(Regex("\\s+"))
                if (columns.size >= 2 && columns[1] == "device") columns[0] else null
            }
            .toList()

        val selectedSerial = System.getenv("ANDROID_SERIAL")
            ?.takeIf { connectedSerials.contains(it) }
            ?: when (connectedSerials.size) {
                0 -> throw GradleException(
                    "No connected Android phone or phone emulator found. Start a phone AVD such as Pixel_8_API_34, or connect a smartphone, then retry.",
                )

                1 -> connectedSerials.single()
                else -> throw GradleException(
                    "More than one Android device is connected (${connectedSerials.joinToString()}). Disconnect extras or set ANDROID_SERIAL to the phone you want to test.",
                )
            }

        val (_, characteristicsOutput) = project.runCommand(
            listOf(adb, "-s", selectedSerial, "shell", "getprop", "ro.build.characteristics"),
        )
        val characteristics = characteristicsOutput.trim()
        if (characteristics.contains("watch", ignoreCase = true)) {
            throw GradleException(
                "Connected device '$selectedSerial' reports Wear OS characteristics ($characteristics). Connect a general Android phone or phone emulator instead.",
            )
        }
    }
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

tasks.register("phoneDebugAndroidTest") {
    group = "verification"
    description = "Runs instrumentation tests on a connected Android phone or phone emulator"
    dependsOn(verifyPhoneConnectedDevice)
    dependsOn(":app:connectedDebugAndroidTest")
}

gradle.projectsEvaluated {
    project(":app").tasks.named("connectedDebugAndroidTest").configure {
        mustRunAfter(verifyPhoneConnectedDevice)
    }
}
