@echo off
setlocal enabledelayedexpansion

REM Check if JAVA_HOME is set
if not defined JAVA_HOME (
    echo JAVA_HOME is not set.
    echo Please enter the path to your Java installation (e.g., C:\Program Files\Java\jdk-21):
    set /p JAVA_HOME=JAVA_HOME=
    
    if not defined JAVA_HOME (
        echo No JAVA_HOME specified. Exiting.
        exit /b 1
    )
)

echo Using JAVA_HOME: %JAVA_HOME%

REM Check if the "all" JAR file exists in the build\libs directory
set "JAR_DIR=..\build\libs"
set "FOUND_JAR="

for /f "delims=" %%i in ('dir /b /a-d "%JAR_DIR%\jfxLDAP-*-all.jar" 2^>nul') do (
    set "FOUND_JAR=%%i"
    goto :found
)

:found
if not defined FOUND_JAR (
    echo Error: Could not find jfxLDAP-*-all.jar in %JAR_DIR%
    echo Please run the build first.
    exit /b 1
)

echo Starting LDAP Explorer with JAR: %JAR_DIR%\%FOUND_JAR%

REM Run the application using the fat JAR
"%JAVA_HOME%\bin\java" -jar "%JAR_DIR%\%FOUND_JAR%"

endlocal
