@file:Suppress("UnstableApiUsage")

plugins {
	id("com.android.application") apply true
	kotlin("android") apply true
}

base {
	archivesName.set("gbcc")
}

android {
	compileSdk = 33
	buildToolsVersion = "33.0.0"
	defaultConfig {
		applicationId = "com.philj56.gbcc"
		minSdk = 21
		targetSdk = 33
		versionCode = 40
		versionName = "beta40"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		externalNativeBuild {
			cmake {
				targets.add("gbcc")
			}
		}
		ndk {
			debugSymbolLevel = "FULL"
		}
		splits {
			abi {
				isEnable = true
				isUniversalApk = true
			}
		}
	}
	buildTypes {
		release {
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			isMinifyEnabled = true
			isShrinkResources = true
		}
		debug {
			isMinifyEnabled = false
			applicationIdSuffix = ".debug"
		}
	}
	buildFeatures {
		viewBinding = true
		buildConfig = true
	}
	externalNativeBuild {
		cmake {
			path("src/main/cpp/CMakeLists.txt")
		}
	}
	
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = JavaVersion.VERSION_17.toString()
	}

	ndkVersion = "25.2.9519653"
	namespace = "com.philj56.gbcc"
}

dependencies {
	implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
	implementation("androidx.camera:camera-core:1.2.2")
	implementation("androidx.camera:camera-camera2:1.2.2")
	implementation("androidx.camera:camera-lifecycle:1.2.2")
	implementation("androidx.core:core-ktx:1.10.0")
	implementation("androidx.constraintlayout:constraintlayout:2.1.4")
	implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
	implementation("androidx.preference:preference-ktx:1.2.0")
	implementation("androidx.recyclerview:recyclerview:1.3.0")
	implementation("com.google.android.material:material:1.8.0")

	// Needed temporarily to work around dependency issues
	constraints {
		implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1") {
			because("Two dependencies have conflicting versions of this.")
		}
		implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.1") {
			because("Get your shit together Google.")
		}
	}
	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test:runner:1.5.2")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

tasks.register<Copy>("copyAssets") {
	from("libs/gbcc/tileset.png")
	from("libs/gbcc/camera.png")
	into("src/main/assets")
}

tasks.register<Copy>("copyResources") {
	from("libs/gbcc/print.wav")
	into("src/main/res/raw")
}

tasks["preBuild"].dependsOn("copyAssets")
tasks["preBuild"].dependsOn("copyResources")
