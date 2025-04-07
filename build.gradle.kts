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
    id("edu.sc.seis.launch4j") version "2.5.4"
   

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

// Custom task to create a Windows executable
tasks.register<Exec>("createWindowsExe") {
    dependsOn("installDist")
    
    workingDir = projectDir
    
    val jpackagePath = "${System.getProperty("java.home")}/bin/jpackage"
    val appVersion = project.version.toString()
    val appName = "LDAP Explorer"
    val inputDir = "${layout.buildDirectory.get()}/install/jfxldap"
    val outputDir = "${layout.buildDirectory.get()}/windows-exe"
    val mainJar = "lib/jfxldap.jar"
    val mainClass = "ch.vilki.jfxldap.Main"
    val iconPath = "${projectDir}/src/main/resources/ch/vilki/jfxldap/icons/ldapTree.png"
    
    commandLine = listOf(
        jpackagePath,
        "--type", "app-image",
        "--name", appName,
        "--app-version", appVersion,
        "--input", inputDir,
        "--dest", outputDir,
        "--main-jar", mainJar,
        "--main-class", mainClass,
        "--icon", iconPath,
        "--win-console"
    )
    
    doFirst {
        mkdir(outputDir)
        println("Creating Windows executable in $outputDir")
    }
}

// Task to create a Windows installer
tasks.register<Exec>("createWindowsInstaller") {
    dependsOn("installDist")
    
    workingDir = projectDir
    
    val jpackagePath = "${System.getProperty("java.home")}/bin/jpackage"
    val appVersion = project.version.toString()
    val appName = "LDAP Explorer"
    val inputDir = "${layout.buildDirectory.get()}/install/jfxldap"
    val outputDir = "${layout.buildDirectory.get()}/windows-installer"
    val mainJar = "lib/jfxldap.jar"
    val mainClass = "ch.vilki.jfxldap.Main"
    val iconPath = "${projectDir}/src/main/resources/ch/vilki/jfxldap/icons/ldapTree.png"
    
    commandLine = listOf(
        jpackagePath,
        "--type", "exe",
        "--name", appName,
        "--app-version", appVersion,
        "--input", inputDir,
        "--dest", outputDir,
        "--main-jar", mainJar,
        "--main-class", mainClass,
        "--icon", iconPath,
        "--win-console",
        "--win-shortcut",
        "--win-menu",
        "--win-dir-chooser"
    )
    
    doFirst {
        mkdir(outputDir)
        println("Creating Windows installer in $outputDir")
    }
}

// Task to create a self-contained Windows executable
tasks.register<Copy>("createWindowsExecutable") {
    dependsOn("installDist")
    
    val outputDir = "${layout.buildDirectory.get()}/windows-app"
    val installDir = "${layout.buildDirectory.get()}/install/jfxldap"
    
    from(installDir)
    into(outputDir)
    
    doFirst {
        mkdir(outputDir)
        println("Creating Windows executable in $outputDir")
    }
    
    doLast {
        // Create a Windows batch file launcher that will work as an executable
        val launcherContent = """
            @echo off
            setlocal
            set "APP_HOME=%~dp0"
            set "JAVA_OPTS=-Djava.library.path="%APP_HOME%\\lib""
            "%APP_HOME%\\bin\\jfxldap.bat" %*
        """.trimIndent()
        
        val launcherFile = File("$outputDir/LdapExplorer.bat")
        launcherFile.writeText(launcherContent)
        
        println("Created Windows launcher: ${launcherFile.absolutePath}")
        println("You can now distribute the folder: $outputDir")
        println("Users can run the application by double-clicking on LdapExplorer.bat")
    }
}

// Task to create a zip distribution of the Windows application
tasks.register<Zip>("zipWindowsApplication") {
    dependsOn("createWindowsExecutable")
    
    from("${layout.buildDirectory.get()}/windows-app")
    archiveFileName.set("LdapExplorer-${project.version}-windows.zip")
    destinationDirectory.set(file("${layout.buildDirectory.get()}/distributions"))
    
    doLast {
        println("Created Windows application zip: ${archiveFile.get().asFile.absolutePath}")
    }
}

// Launch4j configuration for creating Windows executable
launch4j {
    mainClassName = "ch.vilki.jfxldap.Main"
    outputDir = "${layout.buildDirectory.get()}/launch4j"
    icon = "${projectDir}/src/main/resources/ch/vilki/jfxldap/icons/ldapTree.png"  // Using existing PNG icon
    jreMinVersion = "21"
    jar = "${layout.buildDirectory.get()}/install/jfxldap/lib/jfxldap.jar"
    dontWrapJar = true
    headerType = "console"
    jvmOptions = setOf("-Djava.library.path=./lib")
    bundledJre64Bit = true
    bundledJrePath = "%JAVA_HOME%"
    windowTitle = "LDAP Explorer"
}

// Task to create a fat JAR (executable JAR with all dependencies)
tasks.register<Jar>("fatJar") {
    manifest {
        attributes(
            "Main-Class" to "ch.vilki.jfxldap.Main",
            "Implementation-Title" to "LDAP Explorer",
            "Implementation-Version" to project.version
        )
    }
    
    archiveBaseName.set("jfxLDAP")
    archiveClassifier.set("all")
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    // Include main source set output (compiled classes)
    from(sourceSets.main.get().output)
    
    // Include all resources from the main source set
    from(sourceSets.main.get().resources)
    
    // Include all runtime dependencies
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    
    // Exclude META-INF files that could cause conflicts
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    
    doLast {
        println("Fat JAR created: ${archiveFile.get().asFile.absolutePath}")
        println("You can run it with: java -jar ${archiveFile.get().asFile.name}")
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