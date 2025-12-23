# Building Windows Executables for jfxLDAP

This guide explains how to build Windows executables for the jfxLDAP application at any time.

## Quick Start

### Build Standalone Executable (Recommended)
```powershell
.\gradlew.bat buildWindowsExe
```

**Output:** `build/standalone-exe/jfxLDAP/jfxLDAP.exe`

This creates a self-contained application with bundled JRE. Users can run it without installing Java.

### Build Windows Installer
```powershell
.\gradlew.bat buildWindowsInstaller
```

**Output:** `build/standalone-installer/jfxLDAP-5.0.0.exe`

This creates a Windows installer that guides users through installation with Start Menu shortcuts.

---

## Prerequisites

- **Java JDK 21** must be installed
- Windows operating system
- PowerShell or Command Prompt

The project is already configured in `gradle.properties`:
```properties
version = 5.0.0
org.gradle.java.home=C:/data/software/openlogic-openjdk-21.0.9+10-windows-x64
```

---

## Available Build Options

### 1. Standalone Executable (Recommended) ⭐
**Command:** `.\gradlew.bat buildWindowsExe`

**What it creates:**
- Application folder with bundled JRE
- Native Windows .exe launcher
- No Java installation required on target machine
- Portable - can be copied to any Windows PC

**Output location:** `build/standalone-exe/jfxLDAP/`

**Distribution:**
- Zip the entire `jfxLDAP` folder
- Users extract and run `jfxLDAP.exe`

---

### 2. Windows Installer
**Command:** `.\gradlew.bat buildWindowsInstaller`

**What it creates:**
- Single .exe installer file
- Automated installation wizard
- Start Menu shortcuts
- Add/Remove Programs entry
- Bundled JRE

**Output location:** `build/standalone-installer/jfxLDAP-5.0.0.exe`

**Distribution:**
- Share the single .exe file
- Users double-click to install

---

### 3. Fat JAR (Requires Java)
**Command:** `.\gradlew.bat fatJar`

**What it creates:**
- Single JAR file with all dependencies
- Requires Java 21 on target machine
- Smallest file size

**Output location:** `build/libs/jfxLDAP-5.0.0-all.jar`

**Usage:**
```powershell
java -jar jfxLDAP-5.0.0-all.jar
```

---

### 4. Distribution Zip
**Command:** `.\gradlew.bat zipWindowsApplication`

**What it creates:**
- Zip file with complete application
- Includes batch file launcher
- Requires Java 21 on target machine

**Output location:** `build/distributions/LdapExplorer-5.0.0-windows.zip`

---

## Build Process Details

### What Happens During Build

1. **Compile** - All Kotlin and Java sources are compiled
2. **Package** - Dependencies are bundled into a fat JAR
3. **jpackage** - Java's jpackage tool creates native executable
4. **Bundle JRE** - Runtime environment is included
5. **Create Launcher** - Native Windows .exe is generated

### Build Time
- First build: ~2-5 minutes (downloads dependencies)
- Subsequent builds: ~30-60 seconds

---

## Customization

### Change Application Version
Edit `gradle.properties`:
```properties
version = 5.1.0
```

### Change Application Icon
Replace or update the icon path in `build.gradle.kts`:
```kotlin
"--icon", "${projectDir}/src/main/resources/ch/vilki/jfxldap/icons/ldapTree.png"
```

Note: jpackage will convert PNG to ICO format automatically.

### Modify JVM Options
In `build.gradle.kts`, find the `createStandaloneExe` task and modify:
```kotlin
"--java-options", "-Djava.library.path=\"\$APPDIR/app\" -Xmx2g"
```

### Remove Console Window
Remove the `--win-console` option from jpackage commands if you don't want a console window.

---

## Troubleshooting

### "jpackage: command not found"
**Solution:** Ensure you're using JDK 21 (not JRE). jpackage is included in JDK 14+.

Check your Java version:
```powershell
java -version
```

### "The system cannot find the path specified"
**Solution:** Clean the build directory:
```powershell
.\gradlew.bat clean
.\gradlew.bat buildWindowsExe
```

### Build Fails with "Access Denied"
**Solution:** Close the application if it's running, then rebuild:
```powershell
# Kill any running instances
taskkill /F /IM jfxLDAP.exe

# Clean and rebuild
.\gradlew.bat clean buildWindowsExe
```

### "Module not found" Errors
**Solution:** The project uses non-modular approach. Ensure `module-info.java` is NOT present in src/main/java.

---

## Testing Your Build

### Test Standalone Executable
```powershell
cd build\standalone-exe\jfxLDAP
.\jfxLDAP.exe
```

### Test Installer
1. Double-click `build\standalone-installer\jfxLDAP-5.0.0.exe`
2. Follow installation wizard
3. Launch from Start Menu or desktop shortcut

---

## Continuous Integration

### Automated Builds
Add to your CI/CD pipeline:
```yaml
# GitHub Actions example
- name: Build Windows Executable
  run: .\gradlew.bat buildWindowsExe
  
- name: Upload Artifact
  uses: actions/upload-artifact@v3
  with:
    name: jfxLDAP-windows
    path: build/standalone-exe/jfxLDAP/
```

---

## File Sizes (Approximate)

| Build Type | Size | Description |
|------------|------|-------------|
| Fat JAR | ~80 MB | JAR only, no JRE |
| Standalone Exe | ~200 MB | With bundled JRE |
| Installer | ~220 MB | Installer + bundled JRE |
| Zip Distribution | ~80 MB | Requires Java |

---

## Advanced: Custom Build Tasks

The project includes several other build tasks for specific needs:

```powershell
# Create app image (no installer)
.\gradlew.bat createStandaloneExe

# Create installer only
.\gradlew.bat createStandaloneInstaller

# Create distributable zip (requires Java)
.\gradlew.bat zipWindowsApplication

# List all available tasks
.\gradlew.bat tasks --all
```

---

## Distribution Checklist

Before distributing your build:

- [ ] Test the executable on a clean Windows machine (no Java installed)
- [ ] Verify all dependencies are bundled
- [ ] Check that configuration files are writable
- [ ] Test master password creation on first launch
- [ ] Verify LDAP connections work correctly
- [ ] Test LDIF file import/export
- [ ] Check application version is correct

---

## Updating WARP.md

The project's `WARP.md` has been updated with build information. For the latest build instructions, always refer to this document (BUILD_WINDOWS.md).

---

## Support

For build issues, check:
1. This document's Troubleshooting section
2. Project WARP.md for developer context
3. Gradle console output for detailed errors

---

**Last Updated:** December 2025  
**Version:** 5.0.0  
**Maintainer:** Project Team
