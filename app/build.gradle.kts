import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Ключ подписи и пароли к нему живут вне репозитория. Файла нет — собирается только
// debug, и это нормально: release нужен ровно одному человеку с этим ключом.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "io.github.bigilittle.blackout"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.bigilittle.blackout"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Обфусцировать нечего: приложение целиком в двух классах, а R8 без правил
            // только мешает читать стектрейсы.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // В тестах трогаются только константы android.jar, но заглушки по умолчанию
        // кидают исключение на любом обращении — снимаем это.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // В самом приложении зависимостей нет и не планируется: всё на голом SDK.
    testImplementation("junit:junit:4.13.2")
}
