import org.gradle.internal.impldep.org.eclipse.jgit.util.Paths
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
    //id ("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
   

}

val compileJava: JavaCompile by tasks
val appVersion: String = property("version") as String


application {
    //mainModule.set("ch.vilki.jfxldap")
    mainClass.set("ch.vilki.jfxldap.Main")
    /* 
    applicationDefaultJvmArgs = listOf(
        "--add-modules", "ALL-MODULE-PATH",
        "--add-reads", "ch.vilki.jfxldap=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.net=ALL-UNNAMED"
    )
    */ 
}

// Configure Java and Kotlin toolchains
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

//modularity{
//    moduleVersion(appVersion)
//}


repositories {
    mavenCentral()
    /*
    flatDir{
        dir("lib")
    }

     */
}

dependencies {
    implementation( "com.unboundid:unboundid-ldapsdk:5.1.4")
    implementation( "commons-beanutils:commons-beanutils:1.9.4")
    implementation( "org.apache.poi:poi:5.2.3")
    implementation( "org.apache.poi:poi-ooxml:5.2.5")
    implementation( "net.sourceforge.argparse4j:argparse4j:0.9.0")
    implementation( "com.github.peter-gergely-horvath:windpapi4j:1.1.0")
    implementation( "net.java.dev.jna:jna:5.13.0")
    implementation( "com.google.code.gson:gson:2.10.1")
    implementation( "com.google.guava:guava:33.1.0-jre")
    implementation( "org.apache.commons:commons-csv:1.10.0")
    implementation( "org.bouncycastle:bcprov-jdk16:1.46")
    implementation( "org.apache.commons:commons-lang3:3.12.0")
    implementation("org.jfxtras:jmetro:11.6.16")
    implementation( "org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.23.1")
    implementation("commons-cli:commons-cli:1.5.0")
    implementation(files("lib/secured-properties-4.2.jar"))
  	implementation(kotlin("stdlib"))
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
    imageZip.set(project.file("${layout.buildDirectory.get()}/image-zip/jfxldap-image.zip"))
    jpackage {
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            installerOptions.addAll(listOf("--win-per-user-install", "--win-dir-chooser", "--win-menu", "--win-shortcut"))
            imageOptions.add("--win-console")
        }
    }
}

tasks.register("createVersionFile") {
    doLast {
       val versionFilePath = "$projectDir/src/main/java/ch/vilki/jfxldap/ApplicationVersion.kt"
       var content = "package ch.vilki.jfxldap\n\n"
       content+= """object ApplicationVersion {
    const val VERSION = "${properties.getProperty("version")}"
}"""
       File(versionFilePath).writeText(content)
    }
}

tasks.register<Copy>("install_local") {
    dependsOn("installDist")

    val targetInstallationDir = properties.get("local_installation") as String
    from("${layout.buildDirectory.get()}/install/jfxldap")
    into(targetInstallationDir)
    doFirst {
        println("Install in $targetInstallationDir")
        val targetDir = File(targetInstallationDir)
        if (targetDir.exists()) {
            File("${targetDir.absolutePath}/lib").deleteRecursively()
           File("${targetDir.absolutePath}/bin").deleteRecursively()

        }
    }
}