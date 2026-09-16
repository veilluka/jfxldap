<#
.SYNOPSIS
    Builds jfxLDAP for Windows and publishes it as a GitHub release.

.DESCRIPTION
    This script replaces the old "upload whatever is in build\" behaviour with a
    real build-and-publish pipeline:

      * Requires -Version on the command line; synchronises it into gradle.properties,
        AppVersion.java and the docs.
      * If a release with that version already exists it is overwritten automatically.
      * ALWAYS rebuilds the standalone Windows executable (unless -SkipBuild).
      * ALWAYS regenerates the portable zip (no more publishing a stale artifact).
      * Verifies gh is installed AND authenticated before doing any work.
      * Verifies the fresh zip actually contains the expected versioned jar.
      * Optionally commits the version bump and pushes (-CommitAndPush).

.PARAMETER Version
    (Required) Version to release, e.g. 5.3.0. Applied everywhere (gradle.properties,
    AppVersion.java, docs). If a release with this version already exists it is overwritten.

.PARAMETER SkipBuild
    Do not run gradlew buildWindowsExe; reuse the existing build\standalone-exe folder.

.PARAMETER SkipVersionSync
    Do not update AppVersion.java / gradle.properties to match the version.

.PARAMETER CommitAndPush
    git add the version files, commit "Release v<version>" and git push before publishing.

.PARAMETER Draft
    Create the release as a draft.

.PARAMETER Prerelease
    Mark the release as a pre-release.

.PARAMETER DryRun
    Run the whole pipeline (version sync, build, package, verify) but do not publish.

.PARAMETER NotesFile
    Path to a file whose contents replace the generated release notes.

.EXAMPLE
    .\create-github-release.ps1 -Version 5.3.0

.EXAMPLE
    .\create-github-release.ps1 -Version 5.3.0 -CommitAndPush

.EXAMPLE
    .\create-github-release.ps1 -Version 5.2.1 -SkipBuild   # republish the current build
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Version,
    [switch] $SkipBuild,
    [switch] $SkipVersionSync,
    [switch] $CommitAndPush,
    [switch] $Draft,
    [switch] $Prerelease,
    [switch] $DryRun,
    [string] $NotesFile
)

$ErrorActionPreference = "Stop"

function Info { param([string]$m) Write-Host $m -ForegroundColor Cyan }
function Ok   { param([string]$m) Write-Host "  OK  $m" -ForegroundColor Green }
function Warn { param([string]$m) Write-Host " WARN $m" -ForegroundColor Yellow }
function Step { param([string]$m) Write-Host "`n==> $m" -ForegroundColor Magenta }
function Fail { param([string]$m) Write-Host "`nERROR: $m" -ForegroundColor Red; exit 1 }

function Set-FileContent {
    param([string]$Path, [string]$Content)
    for ($i = 1; $i -le 5; $i++) {
        try {
            [System.IO.File]::WriteAllText($Path, $Content)
            return
        } catch {
            if ($i -eq 5) {
                Fail "Cannot write $Path ($($_.Exception.Message)).`nClose the editor / stop the Gradle daemon holding the file and retry."
            }
            Start-Sleep -Milliseconds 500
        }
    }
}

# ---- Configuration ---------------------------------------------------------
$owner = "veilluka"
$repo  = "jfxldap"

$Root = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
Push-Location $Root
try {

# ---- Resolve version (must be supplied on the command line) ----------------
Step "Resolving version"
$gradleProps = Join-Path $Root "gradle.properties"
if (-not (Test-Path $gradleProps)) { Fail "gradle.properties not found at $gradleProps" }
if ($Version -notmatch '^\d+\.\d+\.\d+') { Fail "Invalid version '$Version' (expected something like 5.2.0)" }
Ok "Version : $Version"

$tag         = "v$Version"
$releaseName = "jfxLDAP $Version"
$zipName     = "jfxLDAP-$Version-windows-portable.zip"
$zipFile     = Join-Path $Root "build\$zipName"
$standaloneDir = Join-Path $Root "build\standalone-exe\jfxLDAP"
$appVersionFile = Join-Path $Root "src\main\java\ch\vilki\jfxldap\gui\AppVersion.java"
$expectedJar    = "jfxLDAP-$Version-all.jar"

Ok "Version      : $Version"
Ok "Tag          : $tag"
Ok "Repository   : $owner/$repo"

# ---- Synchronise version into the sources that matter ----------------------
if (-not $SkipVersionSync) {
    Step "Synchronising version into sources"

    # gradle.properties -> drives jar names, jpackage --app-version, project.version
    # The pattern stops before the line ending so CRLF is preserved; if the value
    # already matches, the content is unchanged and the file is not touched at all.
    $gc = Get-Content $gradleProps -Raw
    $gc2 = [regex]::Replace($gc, '(?m)^([ \t]*)version([ \t]*=[ \t]*)[^\r\n]*', ('${1}version${2}' + $Version))
    if ($gc2 -ne $gc) {
        Set-FileContent -Path $gradleProps -Content $gc2
        Ok "gradle.properties -> version = $Version"
    } else {
        Ok "gradle.properties already at $Version"
    }

    # AppVersion.java -> the string shown in the GUI window title
    if (Test-Path $appVersionFile) {
        $ac = Get-Content $appVersionFile -Raw
        $ac2 = [regex]::Replace($ac, '([ \t]*)version([ \t]*=[ \t]*)"[^"]*"', ('${1}version${2}"' + $Version + '"'))
        if ($ac2 -ne $ac) {
            Set-FileContent -Path $appVersionFile -Content $ac2
            Ok "AppVersion.java    -> version = `"$Version`""
        } else {
            Ok "AppVersion.java already at $Version"
        }
    } else {
        Warn "AppVersion.java not found at $appVersionFile; GUI version not synchronised"
    }

    # Documentation -> keep the documented version in step (targeted patterns only)
    $docFiles = @(
        (Join-Path $Root "BUILD_WINDOWS.md"),
        (Join-Path $Root "BUILD_QUICK_REFERENCE.txt")
    )
    foreach ($doc in $docFiles) {
        if (-not (Test-Path $doc)) { continue }
        $dc = Get-Content $doc -Raw
        $dc2 = $dc
        $dc2 = [regex]::Replace($dc2, 'jfxLDAP-\d+\.\d+\.\d+', "jfxLDAP-$Version")
        $dc2 = [regex]::Replace($dc2, 'LdapExplorer-\d+\.\d+\.\d+', "LdapExplorer-$Version")
        $dc2 = [regex]::Replace($dc2, '(?m)^(version[ \t]*=[ \t]*)\d+\.\d+\.\d+', ('${1}' + $Version))
        $dc2 = [regex]::Replace($dc2, '(Version:\*\*[ \t]+)\d+\.\d+\.\d+', ('${1}' + $Version))
        $dc2 = [regex]::Replace($dc2, '(Version[ \t]+)\d+\.\d+\.\d+', ('${1}' + $Version))
        if ($dc2 -ne $dc) {
            Set-FileContent -Path $doc -Content $dc2
            Ok "$(Split-Path $doc -Leaf) -> $Version"
        } else {
            Ok "$(Split-Path $doc -Leaf) already at $Version"
        }
    }
} else {
    Warn "Skipping version synchronisation (-SkipVersionSync)"
}

# ---- Optional: commit + push the version bump ------------------------------
if ($CommitAndPush) {
    Step "Committing and pushing version bump"
    $filesToCommit = @(
        "gradle.properties",
        "src\main\java\ch\vilki\jfxldap\gui\AppVersion.java",
        "BUILD_WINDOWS.md",
        "BUILD_QUICK_REFERENCE.txt"
    ) | Where-Object { Test-Path (Join-Path $Root $_) }
    git add $filesToCommit
    git commit -m "Release v$Version"
    if ($LASTEXITCODE -ne 0) { Fail "git commit failed" }
    git push
    if ($LASTEXITCODE -ne 0) { Fail "git push failed" }
    Ok "Version bump committed and pushed"
}

# ---- Check GitHub CLI ------------------------------------------------------
Step "Checking GitHub CLI"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Warn "GitHub CLI (gh) is not installed."
    Warn "Install it with:  winget install GitHub.cli"
    Warn "Then re-run this script."
    $hasGh = $false
} else {
    $hasGh = $true
    gh auth status *> $null
    if ($LASTEXITCODE -ne 0) {
        Warn "gh is installed but not authenticated."
        Warn "Run:  gh auth login     (choose GitHub.com -> HTTPS or SSH -> browser)"
        $hasGh = $false
    } else {
        Ok "gh installed and authenticated"
    }
}

# ---- Build -----------------------------------------------------------------
if (-not $SkipBuild) {
    Step "Building standalone Windows executable (this may take a few minutes)"
    $gradlew = Join-Path $Root "gradlew.bat"
    if (-not (Test-Path $gradlew)) { Fail "gradlew.bat not found at $gradlew" }
    & $gradlew buildWindowsExe
    if ($LASTEXITCODE -ne 0) { Fail "Gradle build failed (exit code $LASTEXITCODE)" }
    Ok "Build finished"
} else {
    Warn "Skipping build (-SkipBuild); reusing build\standalone-exe"
}

# ---- Guard: refuse to publish a stale build --------------------------------
Step "Verifying build output"
if (-not (Test-Path $standaloneDir)) {
    Fail "Build output not found: $standaloneDir`nRun:  .\gradlew.bat buildWindowsExe"
}
$appDir = Join-Path $standaloneDir "app"
if (-not (Test-Path $appDir)) { Fail "App folder not found: $appDir" }

$expectedJarPath = Join-Path $appDir $expectedJar
if (-not (Test-Path $expectedJarPath)) {
    Warn "Expected $expectedJar was NOT found in $appDir. Contents:"
    Get-ChildItem $appDir | Select-Object -ExpandProperty Name | ForEach-Object { Write-Host "    $_" }
    Fail "The standalone build does not contain the $Version jar. Refusing to publish a stale artifact."
}
Ok "Found $expectedJar in the standalone build"

# ---- Always regenerate the portable zip ------------------------------------
Step "Packaging portable zip"
if (Test-Path $zipFile) {
    Remove-Item $zipFile -Force
    Ok "Removed pre-existing $zipName (would otherwise be published unchanged)"
}
Compress-Archive -Path (Join-Path $standaloneDir '*') -DestinationPath $zipFile -CompressionLevel Optimal -Force
Ok "Created $zipName"

$fileSize = [math]::Round((Get-Item $zipFile).Length / 1MB, 2)
Ok "Size: $fileSize MB"

# ---- Verify the zip really contains the new jar ----------------------------
Step "Verifying zip contents"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipFile)
try {
    $jarEntries = @($zip.Entries | Where-Object { $_.FullName -match '\.jar$' } | Select-Object -ExpandProperty FullName)
} finally {
    $zip.Dispose()
}
if (-not ($jarEntries | Where-Object { $_ -like "*$expectedJar" })) {
    Fail "Zip does not contain $expectedJar. Found: $($jarEntries -join ', ')"
}
Ok "Zip verified: contains $expectedJar"

# ---- Release notes ---------------------------------------------------------
$body = if ($NotesFile) {
    if (-not (Test-Path $NotesFile)) { Fail "Notes file not found: $NotesFile" }
    Get-Content $NotesFile -Raw
} else {
@"
# jfxLDAP $Version - Windows Portable Release

## Download

### Windows Portable Edition (Recommended)
- **File:** ``$zipName`` ($fileSize MB)
- **Requirements:** None - Java Runtime is included
- **Installation:**
  1. Download and extract the zip file
  2. Run ``jfxLDAP.exe``
  3. That's it! No installation needed.

## Features
- **LDAP Tree Comparison** - Compare LDAP instances or LDIF files in real-time
- **Deep Text Search** - Search through LDAP entries including base64-encoded content
- **Entry Synchronization** - Sync entries between LDAP servers
- **Secure Credentials** - Master password protection with Windows DPAPI integration
- **LDIF Support** - Import, export, and edit LDIF files

## System Requirements
- Windows 10 or later (64-bit)
- No Java installation required (JRE is bundled)

## First Run
On first launch, you'll be prompted to create a master password for secure credential storage.

## Documentation
- Quick Start: See README.adoc
- Build Instructions: See BUILD_WINDOWS.md

---
**Note:** This is a portable release with a bundled Java Runtime Environment. The application can be run directly without any installation.
"@
}

Write-Host "`nRelease details:" -ForegroundColor Cyan
Write-Host "  Tag        : $tag"
Write-Host "  Name       : $releaseName"
Write-Host "  Repository : $owner/$repo"
Write-Host "  Asset      : $zipName ($fileSize MB)"

# ---- Publish (or fall back to manual instructions) -------------------------
if (-not $hasGh) {
    Step "GitHub CLI unavailable - saving notes for a manual release"
    $body | Out-File -FilePath (Join-Path $Root "release-notes.md") -Encoding UTF8
    Ok "Release notes saved to release-notes.md"
    Info "Manual steps:"
    Info "  1. Go to: https://github.com/$owner/$repo/releases/new"
    Info "  2. Tag: $tag"
    Info "  3. Title: $releaseName"
    Info "  4. Paste the notes from release-notes.md"
    Info "  5. Upload: $zipFile"
    exit 0
}

Step "Publishing release"
gh release view $tag --repo "$owner/$repo" *> $null
$releaseExists = ($LASTEXITCODE -eq 0)

if ($DryRun) {
    Step "Dry run - nothing will be published"
    if ($releaseExists) {
        Warn "Release $tag already exists and would be OVERWRITTEN"
    } else {
        Ok "Tag $tag is free"
    }
    Info "Would run: gh release create $tag `"$zipFile`" --repo $owner/$repo --title `"$releaseName`" --notes <notes>"
    Ok "Dry run complete. Re-run without -DryRun to publish."
    exit 0
}

if ($releaseExists) {
    Warn "Release $tag already exists - overwriting it"
    gh release delete $tag --repo "$owner/$repo" --yes --cleanup-tag
    if ($LASTEXITCODE -ne 0) { Fail "Failed to delete existing release $tag" }
    Ok "Deleted existing release and tag $tag"
}

$ghArgs = @(
    "release", "create", $tag, $zipFile,
    "--repo", "$owner/$repo",
    "--title", $releaseName,
    "--notes", $body
)
if ($Draft)      { $ghArgs += "--draft" }
if ($Prerelease) { $ghArgs += "--prerelease" }

& gh @ghArgs
if ($LASTEXITCODE -ne 0) { Fail "gh release create failed (exit code $LASTEXITCODE)" }

Write-Host ""
Ok "Release created successfully"
Info "View at: https://github.com/$owner/$repo/releases/tag/$tag"

}
finally {
    Pop-Location
}
