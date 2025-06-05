plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
    signing
}

android {
    namespace = "io.github.birdywood.birdydrop"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        aarMetadata {
            minCompileSdk = 24

        }
    }

    buildTypes {
        release {
            version = "1.0.0"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true // Active Jetpack Compose
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // Vérifie la dernière version compatible avec ton Kotlin/AGP
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.accompanist.permissions)
    implementation(libs.gson)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "io.github.birdywood"
            artifactId = "birdydrop"
            version = "1.0"

            pom {
                packaging = "aar"
                name.set("Birdydrop")
                description.set("Birdydrop: Library for sharing data networkless")
                url.set("https://github.com/birdywood/birdydrop")
                inceptionYear.set("2024")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("BirdyWood")
                        name.set("BirdyWood")
                        email.set("support@birdywood.fr")
                    }
                }
                scm {
                    connection.set("scm:git@github.com:birdywood/birdydrop")
                    developerConnection.set("scm:git@github.com:birdywood/birdydrop.git")
                    url.set("https://github.com/birdywood/birdydrop.git")
                }
            }


            afterEvaluate {
                from(components["release"])
            }
        }

    }
    repositories {
        maven {
            name = "myrepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    sign(publishing.publications["release"])
}



tasks.register<Zip>("generateRepo") {
    val publishTask = tasks.named(
        "publishReleasePublicationToMyrepoRepository",
        PublishToMavenRepository::class.java)
    from(publishTask.map { it.repository.url })
    //into("mylibrary")
    archiveFileName.set("birdydrop.zip")
}