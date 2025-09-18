plugins {
    id("org.jetbrains.intellij") version "1.17.4"
    kotlin("jvm") version "1.9.0"
}

group = "com.example"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:3.25.5")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellij {
    version.set("2024.1")
    instrumentCode.set(false)
    plugins.set(listOf("java", "Kotlin"))
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    buildPlugin {
        dependsOn("patchPluginXml")
    }
    patchPluginXml {
        version.set(project.version.toString())
//        sinceBuild.set("241") // 로컬에서 테스트를 할 때만 사용
        sinceBuild.set("242")
        untilBuild.set("251.*")
    }
}