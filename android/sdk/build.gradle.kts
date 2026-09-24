plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "com.github.lelloman.androidoscopy"
version = "2.0.2"

android {
    namespace = "com.lelloman.androidoscopy"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/pairingJniLibs"))
}

val pairingNativeTargets = mapOf(
    "armeabi-v7a" to Pair("armv7-linux-androideabi", "armv7a-linux-androideabi24-clang"),
    "arm64-v8a" to Pair("aarch64-linux-android", "aarch64-linux-android24-clang"),
    "x86" to Pair("i686-linux-android", "i686-linux-android24-clang"),
    "x86_64" to Pair("x86_64-linux-android", "x86_64-linux-android24-clang")
)
val pairingNativeManifest = rootProject.file("../pairing-rate-limit/Cargo.toml")
val sharedLibrarySource = rootProject.file("../../simple-server")
val pairingNativeOutput = layout.buildDirectory.dir("generated/pairingJniLibs")
val buildPairingNative = tasks.register("buildPairingNative") {
    inputs.files(fileTree(pairingNativeManifest.parentFile.resolve("src")), pairingNativeManifest,
        rootProject.file("../pairing-rate-limit/Cargo.lock"), rootProject.file("../simple-server.rev"),
        sharedLibrarySource.resolve("Cargo.toml"), fileTree(sharedLibrarySource.resolve("src")))
    outputs.dir(pairingNativeOutput)
    doLast {
        val ndkHost = if (System.getProperty("os.name").lowercase().contains("mac")) "darwin-x86_64" else "linux-x86_64"
        val ndkBin = android.sdkDirectory.resolve("ndk/${android.ndkVersion}/toolchains/llvm/prebuilt/$ndkHost/bin")
        require(ndkBin.isDirectory) { "Android NDK ${android.ndkVersion} is required at $ndkBin" }
        val targetDir = layout.buildDirectory.dir("pairingRustTarget").get().asFile
        val outputDir = pairingNativeOutput.get().asFile
        pairingNativeTargets.forEach { (abi, targetAndLinker) ->
            val (target, linker) = targetAndLinker
            project.exec {
                commandLine("cargo", "build", "--release", "--locked", "--manifest-path",
                    pairingNativeManifest.absolutePath, "--target", target, "--target-dir", targetDir.absolutePath)
                environment("CARGO_TARGET_${target.uppercase().replace('-', '_')}_LINKER",
                    ndkBin.resolve(linker).absolutePath)
                environment("RUSTFLAGS", listOfNotNull(System.getenv("RUSTFLAGS"),
                    "-C link-arg=-Wl,-z,max-page-size=16384").joinToString(" "))
            }
            copy {
                from(targetDir.resolve("$target/release/libandroidoscopy_pairing_rate_limit.so"))
                into(outputDir.resolve(abi))
            }
        }
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(buildPairingNative) }

val pairingHostOutput = layout.buildDirectory.dir("pairingHostRustTarget")
val buildPairingHostNative = tasks.register<Exec>("buildPairingHostNative") {
    inputs.files(fileTree(pairingNativeManifest.parentFile.resolve("src")), pairingNativeManifest,
        rootProject.file("../pairing-rate-limit/Cargo.lock"), rootProject.file("../simple-server.rev"),
        sharedLibrarySource.resolve("Cargo.toml"), fileTree(sharedLibrarySource.resolve("src")))
    outputs.dir(pairingHostOutput)
    commandLine("cargo", "build", "--release", "--locked", "--manifest-path",
        pairingNativeManifest.absolutePath, "--target-dir", pairingHostOutput.get().asFile.absolutePath)
}
tasks.withType<Test>().configureEach {
    dependsOn(buildPairingHostNative)
    systemProperty("java.library.path", pairingHostOutput.get().asFile.resolve("release").absolutePath)
}

dependencies {
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    implementation("com.networknt:json-schema-validator:1.0.87")
    implementation(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.lelloman.androidoscopy"
                artifactId = "sdk"
                version = project.version.toString()
            }
        }
    }
}
