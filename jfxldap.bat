@echo off
setlocal enabledelayedexpansion

REM Check if JAVA_HOME is set
if not defined JAVA_HOME (
    echo JAVA_HOME is not set.
    echo Please enter the path to your Java installation (e.g., C:\Program Files\Java\jdk-21):
    set /p JAVA_HOME=Enter JAVA_HOME: 
    if not defined JAVA_HOME (
        echo No JAVA_HOME specified. Exiting.
        exit /b 1
    )
)

echo Using JAVA_HOME: !JAVA_HOME!

REM Look for the JAR file in the current directory
set "FOUND_JAR="

for /f "delims=" %%i in ('dir /b /a-d "jfxLDAP-*-all.jar" 2^>nul') do (
    set "FOUND_JAR=%%i"
    goto found_jar
)

:found_jar
if not defined FOUND_JAR (
    echo Error: Could not find jfxLDAP-*-all.jar in the current directory
    echo Please make sure you're running this from the build\libs directory
    exit /b 1
)

echo Starting LDAP Explorer with JAR: !FOUND_JAR!

REM Run the application using the fat JAR
"!JAVA_HOME!\bin\java" -jar "!FOUND_JAR!"

endlocal
