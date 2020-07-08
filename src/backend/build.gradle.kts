import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin("jvm") version "1.3.72"
    kotlin("plugin.spring") version "1.3.72" apply false
    id("org.springframework.boot") version "2.3.1.RELEASE" apply false
    id("io.spring.dependency-management") version "1.0.9.RELEASE"
    id("maven-publish")
}

buildscript {
    repositories {
        mavenLocal()
        maven(url = property("publicMavenRepoUrl")?.toString().orEmpty())
        maven(url = "https://repo.spring.io/libs-milestone")
        mavenCentral()
        jcenter()
    }
}

allprojects {
    group = "com.tencent.bkrepo"
    version = "0.7.1"

    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(from = rootProject.file("gradle/ktlint.gradle.kts"))
    apply(from = rootProject.file("gradle/publish.gradle.kts"))

    repositories {
        val publicMavenRepoUrl: String by project
        val privateMavenRepoUrl: String by project

        mavenLocal()
        maven(url = publicMavenRepoUrl)
        maven(url = privateMavenRepoUrl)
        maven(url = "https://repo.spring.io/libs-milestone")
        mavenCentral()
        jcenter()
    }

    // fix issue https://github.com/spring-projects/spring-boot/issues/21934
    extra["reactor-bom.version"] = "Dysprosium-SR9"

    dependencyManagement {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:Hoxton.SR5")
        }
        dependencies {
            dependency("io.swagger:swagger-annotations:1.5.22")
            dependency("io.swagger:swagger-models:1.5.22")
            dependency("io.springfox:springfox-swagger2:2.9.2")
            dependency("com.github.ulisesbocchio:jasypt-spring-boot-starter:2.1.2")
            dependency("com.amazonaws:aws-java-sdk-s3:1.11.700")
            dependency("com.tencent:innercos-java-sdk:5.6.7")
            dependency("com.google.guava:guava:29.0-jre")
            dependency("org.apache.commons:commons-compress:1.18")
            dependency("org.apache.skywalking:apm-toolkit-logback-1.x:6.6.0")
            dependency("org.apache.skywalking:apm-toolkit-trace:6.6.0")
            dependency("org.apache.hadoop:hadoop-hdfs:2.6.0")
            dependency("org.apache.hadoop:hadoop-common:2.6.0")
            dependency("commons-io:commons-io:2.6")
            dependency("net.javacrumbs.shedlock:shedlock-spring:4.12.0")
            dependency("net.javacrumbs.shedlock:shedlock-provider-mongo:4.12.0")
            dependency("io.jsonwebtoken:jjwt-api:0.11.1")
            dependency("io.jsonwebtoken:jjwt-impl:0.11.1")
        }
    }

    configurations.all {
        exclude(group = "log4j", module = "log4j")
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "commons-logging", module = "commons-logging")
    }

    tasks {
        compileKotlin {
            kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
        compileTestKotlin {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
        test {
            useJUnitPlatform()
        }
    }
}

subprojects {
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation(kotlin("reflect"))
        testImplementation("org.springframework.boot:spring-boot-starter-test") {
            exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        }
    }

    val jar: Jar by tasks
    val bootJar: BootJar by tasks
    val bootRun: BootRun by tasks

    val isBootProject = project.name.startsWith("boot-")

    bootJar.enabled = isBootProject
    bootRun.enabled = isBootProject
    jar.enabled = !isBootProject
}

tasks.register<DefaultTask>("publishApiJar") {
    val projectList = listOf(
        ":common:common-api",
        ":common:common-artifact:artifact-api",
        ":common:common-query:query-api",
        ":common:common-storage:storage-api",
        ":generic:api-generic",
        ":repository:api-repository"
    )
    projectList.forEach { dependsOn(project(it).tasks.publish) }
}
