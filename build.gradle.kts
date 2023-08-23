import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

val properties = Properties()
properties.load(project.file("gradle.properties").inputStream())

val version: String by extra
val versionJavafx: String by extra
val kotlinjvm:String by extra

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.0.14"
    id("org.beryx.jlink") version "2.22.1"
    id ("org.javamodularity.moduleplugin") version "1.8.12"
    kotlin("jvm") version "1.8.21"

}

val compileJava: JavaCompile by tasks

application {
    mainModule.set("ch.vilki.jfxldap")
    mainClass.set("ch.vilki.jfxldap.Main")
}

repositories {
    mavenCentral()
    flatDir{
        dir("lib")
    }
}

dependencies {
    implementation( "com.unboundid:unboundid-ldapsdk:5.1.4")
    implementation( "commons-beanutils:commons-beanutils:1.9.3")
    implementation( "org.apache.poi:poi:5.2.3")
    implementation( "org.apache.poi:poi-ooxml:5.2.3")
    implementation( "net.sourceforge.argparse4j:argparse4j:0.9.0")
    implementation( "com.github.peter-gergely-horvath:windpapi4j:1.1.0")
    implementation( "net.java.dev.jna:jna:5.13.0")
    implementation( "com.google.code.gson:gson:2.10.1")
    implementation( "com.google.guava:guava:31.1-jre")
    implementation( "org.apache.commons:commons-csv:1.10.0")
    implementation( "org.bouncycastle:bcprov-jdk16:1.45")
    implementation( "org.apache.commons:commons-lang3:3.12.0")
    implementation("org.jfxtras:jmetro:11.6.16")
    implementation( "org.apache.logging.log4j:log4j-core:2.11.1")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("commons-cli:commons-cli:1.5.0")
    implementation(files("lib/secured-properties-3.0.jar"))
  	implementation(kotlin("stdlib-jdk8"))
    implementation("com.panemu:tiwulfx:3.4")


}

tasks.register("testME"){
    var l: ConfigurableFileTree = fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar")))
    l.forEach { f->
        println("name $f")
    }
    println("test me done")

}

javafx {
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web","javafx.base","javafx.swing")
    version = versionJavafx
}

jlink{
    addOptions("--strip-debug","--strip-debug","--compress","2","--no-header-files","--no-man-pages")
    launcher {
        name = "jfxldap"
    }
    forceMerge("log4j-api")
    addExtraDependencies("javafx","log4j")
    imageZip.set(project.file("${project.buildDir}/image-zip/jfxldap-image.zip"))
    jpackage {
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            installerOptions.addAll(listOf("--win-per-user-install", "--win-dir-chooser", "--win-menu", "--win-shortcut"))
            imageOptions.add("--win-console")
        }
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "17"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "17"
}