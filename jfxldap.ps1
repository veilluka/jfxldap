# PowerShell script to run jfxLDAP application
# Checks for java.properties file and allows GUI selection of Java installation

# Get the directory where this script is located
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Path to the properties file
$propertiesFile = Join-Path -Path $scriptDir -ChildPath "java.properties"

# Function to show a folder selection dialog
function Get-FolderSelection {
    [CmdletBinding()]
    param()
    
    Add-Type -AssemblyName System.Windows.Forms
    $folderBrowser = New-Object System.Windows.Forms.FolderBrowserDialog
    $folderBrowser.Description = "Select the Java installation directory (e.g., C:\Program Files\Java\jdk-21)"
    $folderBrowser.ShowNewFolderButton = $false
    
    $result = $folderBrowser.ShowDialog()
    
    if ($result -eq [System.Windows.Forms.DialogResult]::OK) {
        return $folderBrowser.SelectedPath
    }
    
    return $null
}

# Function to read the Java path from properties file
function Get-JavaPath {
    if (Test-Path $propertiesFile) {
        $content = Get-Content $propertiesFile
        foreach ($line in $content) {
            if ($line -match "^java=(.+)$") {
                $javaPath = $matches[1].Trim()
                if (Test-Path (Join-Path -Path $javaPath -ChildPath "bin\java.exe")) {
                    return $javaPath
                }
            }
        }
    }
    return $null
}

# Function to save Java path to properties file
function Save-JavaPath {
    param (
        [string]$javaPath
    )
    
    "java=$javaPath" | Set-Content -Path $propertiesFile
    Write-Host "Java path saved to $propertiesFile"
}

# Main script logic
$javaPath = Get-JavaPath

if (-not $javaPath) {
    Write-Host "No valid Java installation found in properties file."
    $javaPath = Get-FolderSelection
    
    if (-not $javaPath) {
        Write-Host "No Java installation selected. Exiting."
        exit 1
    }
    
    # Verify the selected directory contains java.exe
    $javaExe = Join-Path -Path $javaPath -ChildPath "bin\java.exe"
    if (-not (Test-Path $javaExe)) {
        Write-Host "Error: Selected directory does not contain a valid Java installation."
        Write-Host "Please select a directory that contains 'bin\java.exe'"
        exit 1
    }
    
    Save-JavaPath -javaPath $javaPath
}

# Find the JAR file
$jarFile = Get-ChildItem -Path $scriptDir -Filter "jfxLDAP-*-all.jar" | Select-Object -First 1

if (-not $jarFile) {
    Write-Host "Error: Could not find jfxLDAP JAR file in $scriptDir"
    exit 1
}

# Run the application
$javaExe = Join-Path -Path $javaPath -ChildPath "bin\java.exe"
Write-Host "Starting LDAP Explorer using Java from: $javaPath"
Write-Host "JAR file: $($jarFile.Name)"

& "$javaExe" -jar "$($jarFile.FullName)"
