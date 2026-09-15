plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ktlint)
}

val appVersionName = property("app.versionName") as String
val dotEnv =
  rootProject.layout.projectDirectory
    .file(".env")
    .asFile
    .takeIf { it.exists() }
    ?.readLines()
    ?.mapNotNull { line ->
      val trimmed = line.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("#") || "=" !in trimmed) {
        null
      } else {
        val key = trimmed.substringBefore("=").trim()
        val value = trimmed.substringAfter("=").substringBefore(" #").trim()
        key to value
      }
    }?.toMap()
    .orEmpty()

fun envVar(key: String): String? =
  providers.environmentVariable(key).orNull?.takeIf { it.isNotBlank() }
    ?: dotEnv[key]?.takeIf { it.isNotBlank() }

val releaseSigningEnvKeys = listOf("KEYSTORE_PATH", "KEY_ALIAS", "KEYSTORE_PASSWORD")
val missingReleaseSigningEnvKeys = releaseSigningEnvKeys.filter { envVar(it).isNullOrBlank() }

android {
  namespace = "com.NovelRegEx.app"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.NovelRegEx.app"
    minSdk = 31
    targetSdk = 36
    versionCode = (property("app.versionCode") as String).toInt()
    versionName = appVersionName
  }

  signingConfigs {
    if (missingReleaseSigningEnvKeys.isEmpty()) {
      create("release") {
        storeFile = file(requireNotNull(envVar("KEYSTORE_PATH")))
        keyAlias = requireNotNull(envVar("KEY_ALIAS"))
        storePassword = requireNotNull(envVar("KEYSTORE_PASSWORD"))
        keyPassword = envVar("KEY_PASSWORD") ?: requireNotNull(envVar("KEYSTORE_PASSWORD"))
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      if (missingReleaseSigningEnvKeys.isEmpty()) signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    create("prerelease") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  buildFeatures {
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  androidResources {
    noCompress += "js"
  }
}

kotlin {
  jvmToolchain(17)
}

ktlint {
  additionalEditorconfig.set(mapOf("max_line_length" to "150"))
}

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.webkit)
  implementation(libs.material)
  testImplementation("junit:junit:4.13.2")
}

val validateReleaseSigning =
  tasks.register("validateReleaseSigning") {
    description = "Validate environment variables required for signing the release APK."
    doLast {
      check(missingReleaseSigningEnvKeys.isEmpty()) {
        "Missing release signing environment variables: ${missingReleaseSigningEnvKeys.joinToString()}"
      }
    }
  }

tasks.matching { it.name == "assembleRelease" }.configureEach {
  mustRunAfter(validateReleaseSigning)
}

tasks.register<Copy>("buildReleaseApk") {
  group = "build"
  description = "Builds the signed release APK and copies it to the root apk directory."
  dependsOn(validateReleaseSigning)
  dependsOn("assembleRelease")

  val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
  from(releaseApk)
  into(rootProject.layout.projectDirectory.dir("apk"))
  rename { "NovelRegEx-v$appVersionName.apk" }

  doFirst {
    check(releaseApk.get().asFile.exists()) {
      "Release APK was not found: ${releaseApk.get().asFile}"
    }
  }
}
