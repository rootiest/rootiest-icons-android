import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

// The version scripts/release.sh tags (v<appVersion>).
extra["appVersion"] = "0.9.0"

// The versionCode follows from it: major, two digits of minor, two of patch, so 1.2.3 is 10203. Minor and patch stay
// under 100, or a later version could get a lower code.
val (major, minor, patch) = (extra["appVersion"] as String).split(".").map(String::toInt)
require(minor < 100 && patch < 100) { "the versionCode has two digits each for minor and patch" }
extra["appVersionCode"] = major * 10000 + minor * 100 + patch

// The release key's properties file (storeFile, storePassword, keyAlias, keyPassword), named by the Gradle property
// rootiest.signing in ~/.gradle/gradle.properties. Without it (CI, another machine) release builds get the debug key,
// which scripts/release.sh refuses to publish.
extra["releaseKey"] = providers.gradleProperty("rootiest.signing").orNull?.let { path ->
    Properties().apply { file(path).inputStream().use(::load) }
}
