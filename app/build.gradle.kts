plugins {
	id("com.android.application") apply true
	kotlin("android") apply true
}

base {
	archivesName.set("gbcc")
}

android {
	compileSdk = 32
	buildToolsVersion = "32.0.0"
	defaultConfig {
		applicationId = "com.philj56.gbcc"
		minSdk = 21
		targetSdk = 32
		versionCode = 38
		versionName = "beta38"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		externalNativeBuild {
			cmake {
				targets.add("gbcc")
			}
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
			ndk {
				debugSymbolLevel = "FULL"
			}
		}
		debug {
			isMinifyEnabled = false
		}
	}
	buildFeatures {
		viewBinding = true
	}
	externalNativeBuild {
		cmake {
			path("src/main/cpp/CMakeLists.txt")
		}
	}
	
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}

	kotlinOptions {
		jvmTarget = JavaVersion.VERSION_11.toString()
	}

	ndkVersion = "24.0.8215888"
}

dependencies {
	implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
	implementation("androidx.camera:camera-core:1.1.0-beta03")
	implementation("androidx.camera:camera-camera2:1.1.0-beta03")
	implementation("androidx.camera:camera-lifecycle:1.1.0-beta03")
	implementation("androidx.core:core-ktx:1.7.0")
	implementation("androidx.constraintlayout:constraintlayout:2.1.3")
	implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
	implementation("androidx.preference:preference-ktx:1.2.0")
	implementation("androidx.recyclerview:recyclerview:1.2.1")
	implementation("com.google.android.material:material:1.6.0-rc01")
	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test:runner:1.4.0")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}

tasks.register<Copy>("copyAssets") {
	from("libs/gbcc/tileset.png")
	from("libs/gbcc/camera.png")
	from("libs/gbcc/print.wav")
	into("src/main/assets")
}

tasks["preBuild"].dependsOn("copyAssets")