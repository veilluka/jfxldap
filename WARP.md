# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

jfxLDAP is a JavaFX-based LDAP client application that provides:
- Real-time LDAP tree comparison between instances or files
- Deep text search in LDAP entries (including base64-encoded content)
- Entry synchronization and bulk operations
- LDIF file import/export and editing
- Secure credential storage with master password protection

**Main Class:** `ch.vilki.jfxldap.Main`
**Package Structure:** `ch.vilki.jfxldap`

## Build System

This project uses **Gradle with Kotlin DSL** (build.gradle.kts) and requires **Java 21**.

### Common Commands

#### Building and Running
```powershell
# Build the project
.\gradlew.bat build

# Run the application directly
.\gradlew.bat run

# Create a fat JAR with all dependencies
.\gradlew.bat fatJar

# Run the fat JAR (from build/libs directory)
java -jar jfxLDAP-<version>-all.jar

# Or use the provided launcher scripts
.\jfxldap.ps1  # PowerShell (recommended on Windows)
.\jfxldap.bat  # Batch file
```

#### Distribution Tasks
```powershell
# Install distribution (creates build/install/jfxldap)
.\gradlew.bat installDist

# Install to local development directory (defined in gradle.properties)
.\gradlew.bat install_local

# Create Windows executables
.\gradlew.bat createWindowsExe           # App image
.\gradlew.bat createWindowsInstaller     # Full installer
.\gradlew.bat createStandaloneExe        # Standalone with bundled JRE
.\gradlew.bat createStandaloneInstaller  # Installer with bundled JRE

# Create distributable zip
.\gradlew.bat zipWindowsApplication
```

#### jlink Packaging
```powershell
# Create optimized runtime image with jlink
.\gradlew.bat jlink

# Create jpackage installer
.\gradlew.bat jpackage
```

### Version Management
The `createVersionFile` task generates `ApplicationVersion.kt` from `gradle.properties`.

## Architecture

### Core Components

#### Backend (`ch.vilki.jfxldap.backend`)
Business logic and LDAP operations:

- **Config.kt** - Configuration management with secured storage (master password protected)
- **Connection.java** - LDAP connection abstraction supporting both live connections and LDIF file mode
- **LDAPReader.kt** - LDAP query execution with attribute filtering (operational/user/all)
- **CompareTree.java** - Tree comparison algorithm for LDAP instances
- **LdifHandler.java** - LDIF file parsing and generation
- **CollectionsProject.java** - Project management for saved LDAP entry collections
- **JNDIReader.java** - Alternative JNDI-based LDAP reader

#### GUI (`ch.vilki.jfxldap.gui`)
JavaFX UI components:

- **ControllerManager.kt** - Central controller initialization and lifecycle management
- **LdapExploreController** - Main LDAP tree navigation (source/target modes)
- **LdapCompareController** - Side-by-side comparison view
- **SearchResultController** - Text search results display
- **EntryView.java** - Attribute editor for individual LDAP entries
- **CollectionsController** - Collection project management UI
- **SettingsController** - Application settings and connection management
- **LdifEditorController** - LDIF editor window

#### Root Package (`ch.vilki.jfxldap`)
- **Main.java** - Application entry point and JavaFX initialization
- **Controller.java** - Main window controller with menu and toolbar
- **DetachableTabPane.java** - Custom tab system supporting window detachment

### Key Design Patterns

#### Dual-Mode Architecture
The application operates in two modes:
- **Live LDAP Mode**: Direct connection to LDAP servers via UnboundID SDK
- **File Mode**: LDIF file browsing (`Connection.is_fileMode()`)

Both modes share the same tree navigation and comparison logic.

#### Controller Management
All FXML controllers are initialized through `ControllerManager` which:
- Loads FXML files from `/ch/vilki/jfxldap/fxml/`
- Injects Main reference via `ILoader.setMain()`
- Manages controller lifecycle and interdependencies

Example:
```kotlin
_ldapSourceExploreCtrl = initController("LdapExplore.fxml") as LdapExploreController
```

#### Detachable UI
The main window uses `DetachableTabPane` allowing users to:
- Drag tabs to separate windows
- Re-dock windows as tabs
- Support multiple concurrent views (Explorer, Target, Compare, Search Results)

#### Secure Storage
Configuration uses `secured-properties` library (`ch.vilki.secured.SecStorage`):
- Master password protection (optional)
- Windows DPAPI integration
- Encrypted connection credentials

### FXML Resources
All UI layouts are in `src/main/resources/ch/vilki/jfxldap/fxml/`

Key FXML files:
- MainWindow.fxml - Primary application window
- LdapExplore.fxml - LDAP tree explorer (reused for source/target)
- LdapCompareWindow.fxml - Comparison results
- SearchResult.fxml - Text search results
- Settings.fxml - Connection and application settings

## Dependencies

### Core Libraries
- **UnboundID LDAP SDK** - Primary LDAP client library
- **JavaFX 21** - UI framework
- **JMetro** - Metro-style theme for JavaFX
- **secured-properties** - Encrypted configuration storage (local JAR in lib/)

### Utility Libraries
- Apache POI - Excel file handling for collection exports
- Apache Commons (lang3, csv, beanutils)
- Bouncy Castle - Additional cryptography support
- windpapi4j - Windows DPAPI integration

## Command-Line Interface

The application supports CLI mode for automation:

```powershell
java -jar jfxLDAP-<version>-all.jar -op export_collection -projectFile <path> -env <envName>
```

CLI arguments are processed by `CmdLine` class before GUI launch.

## Configuration

### User Configuration Location
- Windows: `%USERPROFILE%/.jfxldap/config.properties` (encrypted)
- Contains LDAP connections, keystore settings, last used directories

### First Run
On first startup:
1. User must define a master password (minimum 8 characters)
2. Note: Password length constraints depend on Java crypto policy settings
3. For unlimited strength: Uncomment `crypto.policy=unlimited` in `JAVA_HOME/lib/security/java.security`

### Gradle Configuration
Edit `gradle.properties` for:
- Version number
- JavaFX version
- Local installation directory (`local_installation`)
- Java home path

## Development Patterns

### Adding a New Controller
1. Create FXML file in `src/main/resources/ch/vilki/jfxldap/fxml/`
2. Create controller class implementing `ILoader` interface
3. Register in `ControllerManager.initControllers()`
4. Access via `Main._ctManager._yourController`

### LDAP Operations
Use `Connection` class for all LDAP interactions:
```java
Connection conn = Main._configuration._connections.get(connectionName);
LDAPConnection ldapConn = conn.get_ldapConnection();
SearchResult result = ldapConn.search(baseDN, scope, filter);
```

### File Mode Operations
```java
connection.set_fileMode(true);
TreeMap<String, Entry> entries = connection.get_fileEntries();
```

## Special Notes

### Mixed Language Codebase
This project uses both Java and Kotlin:
- **Kotlin**: Backend logic, data classes, configuration (`.kt` files in `src/main/kotlin/`)
- **Java**: GUI controllers, main application, LDAP operations (`.java` files in `src/main/java/`)
- Both compile to the same output and interoperate seamlessly

### Logging
Uses Log4j 2 with SLF4J facade. Logger instances: `LogManager.getLogger(ClassName.class)`

### Threading
GUI operations must run on JavaFX Application Thread. Use `Platform.runLater()` for updates from background threads.

### Custom Libraries
Two local JARs in `lib/` directory:
- `secured-properties-4.2.jar` - Configuration encryption
- `cncKCommon.jar` - Common utilities

These are not in Maven Central and must remain in the lib/ folder.
