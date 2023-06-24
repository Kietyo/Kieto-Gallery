
plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-android-extensions")
    id("kotlin-kapt")
}


val keystorePropertiesFile = rootProject.file("keystore.properties")
//val keystoreProperties = Properties()
//if (keystorePropertiesFile.exists()) {
//    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
//}

android {
    compileSdkVersion(33)

    defaultConfig {
        applicationId = "com.simplemobiletools.gallery.pro"
        minSdkVersion(23)
        targetSdkVersion(33)
        versionCode = 386
        versionName = "6.26.5"
        setProperty("archivesBaseName", "gallery-$versionCode")
        vectorDrawables.useSupportLibrary = true
    }

//    signingConfigs {
//        if (keystorePropertiesFile.exists()) {
//            release {
//                keyAlias keystoreProperties['keyAlias']
//                keyPassword keystoreProperties['keyPassword']
//                storeFile file(keystoreProperties['storeFile'])
//                storePassword keystoreProperties['storePassword']
//            }
//        }
//    }

    buildTypes {
        debug {
            // we cannot change the original package name, else PhotoEditorSDK won't work
            //applicationIdSuffix ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true

//            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
//            if (keystorePropertiesFile.exists()) {
//                signingConfig signingConfigs.release
//            }
        }
    }

//    sourceSets {
//        main
//        main.java.srcDirs += 'src/main/kotlin'
//        if (is_proprietary) {
//            main.java.srcDirs += 'src/proprietary/kotlin'
//        }
//    }

    flavorDimensions.add("licensing")
//    flavorDimensions = "licensing"
    productFlavors {

//        proprietary {}
//        foss {}
//        prepaid {}
    }

    lintOptions {
        isCheckReleaseBuilds = false
        isAbortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        exclude("META-INF/library_release.kotlin_module")
    }
}

dependencies {
//    implementation 'com.github.SimpleMobileTools:Simple-Commons:2794ea914a'
    implementation("com.theartofdev.edmodo:android-image-cropper:2.8.0")
    implementation("it.sephiroth.android.exif:library:1.0.1")
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.24")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.exoplayer:exoplayer-core:2.9.6")
    implementation("com.google.vr:sdk-panowidget:1.180.0")
    implementation("com.google.vr:sdk-videowidget:1.180.0")
    implementation("org.apache.sanselan:sanselan:0.97-incubator")
    implementation("info.androidhive:imagefilters:1.0.7")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.github.tibbi:gestureviews:a8e8fa8d27")
    implementation("com.github.tibbi:subsampling-scale-image-view:80efdaa570")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.penfeizhou.android.animation:awebp:2.17.0")
    implementation("com.github.penfeizhou.android.animation:apng:2.17.0")
    implementation("com.squareup.okio:okio:3.0.0")
    implementation("com.squareup.picasso:picasso:2.71828") {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
//        exclude group: 'com.squareup.okhttp3', module: 'okhttp'
    }
    compileOnly("com.squareup.okhttp3:okhttp:4.9.0")

    kapt("com.github.bumptech.glide:compiler:4.13.2")

    kapt("androidx.room:room-compiler:2.4.3")
    implementation("androidx.room:room-runtime:2.4.3")
    annotationProcessor("androidx.room:room-compiler:2.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.9")

    implementation(project(":commons"))
}

// Apply the PESDKPlugin
//if (is_proprietary) {
//    apply("ly.img.android.sdk")
////    apply plugin: 'ly.img.android.sdk'
//
//    imglyConfig {
//        vesdk {
//            enabled = true
//            licensePath = "vesdk_android_license"
//        }
//
//        pesdk {
//            enabled = true
//            licensePath = "pesdk_android_license"
//        }
//
//        modules {
//            include("ui:video-trim")
//            include("ui:core")
//            include("ui:text")
//            include("ui:focus")
//            include("ui:brush")
//            include("ui:filter")
//            include("ui:sticker")
//            include("ui:overlay")
//            include("ui:transform")
//            include("ui:adjustment")
//            include("ui:video-composition")
//
//            include("backend:serializer")
//            include("backend:sticker-smart")
//            include("backend:sticker-animated")
//
//            include("assets:font-basic")
//            include("assets:filter-basic")
//            include("assets:overlay-basic")
//            include("assets:sticker-shapes")
//            include("assets:sticker-emoticons")
//            include("assets:sticker-animated")
//        }
//    }
//}
