# PowerShell script to create a GitHub release for jfxLDAP
# Usage: .\create-github-release.ps1

$ErrorActionPreference = "Stop"

# Configuration
$owner = "veilluka"
$repo = "jfxldap"
$version = "5.0.0"
$tag = "v$version"
$releaseName = "jfxLDAP $version"
$zipFile = "build\jfxLDAP-$version-windows-portable.zip"

# Check if zip file exists
if (-not (Test-Path $zipFile)) {
    Write-Error "Zip file not found: $zipFile"
    Write-Host "Please run: .\gradlew.bat buildWindowsExe first"
    exit 1
}

# Get file size for display
$fileSize = [math]::Round((Get-Item $zipFile).Length / 1MB, 2)
Write-Host "Found portable zip: $fileSize MB" -ForegroundColor Green

# Release notes
$body = @"
# jfxLDAP v$version - Windows Portable Release

## What's New
- Enhanced build system for easy Windows executable creation
- Comprehensive build documentation
- Improved distribution workflow

## Download

### 📦 Windows Portable Edition (Recommended)
- **File:** ``jfxLDAP-$version-windows-portable.zip`` ($fileSize MB)
- **Requirements:** None - Java Runtime is included
- **Installation:** 
  1. Download and extract the zip file
  2. Run ``jfxLDAP.exe``
  3. That's it! No installation needed.

### Features
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
- Quick Start: See [README.adoc](README.adoc)
- Build Instructions: See [BUILD_WINDOWS.md](BUILD_WINDOWS.md)
- Developer Guide: See [WARP.md](WARP.md)

## Support
For issues or questions, please open an issue on GitHub.

---
**Note:** This is a portable release with bundled Java Runtime Environment. The application can be run directly without any installation.
"@

Write-Host "`nRelease Details:" -ForegroundColor Cyan
Write-Host "  Tag: $tag"
Write-Host "  Name: $releaseName"
Write-Host "  Repository: $owner/$repo"
Write-Host "  Asset: $zipFile ($fileSize MB)"
Write-Host ""

# Check for GitHub CLI
$ghInstalled = Get-Command gh -ErrorAction SilentlyContinue
if ($ghInstalled) {
    Write-Host "GitHub CLI detected. Creating release..." -ForegroundColor Green
    
    # Create the release with GitHub CLI
    gh release create $tag $zipFile `
        --repo "$owner/$repo" `
        --title "$releaseName" `
        --notes $body
    
    Write-Host "`n✓ Release created successfully!" -ForegroundColor Green
    Write-Host "  View at: https://github.com/$owner/$repo/releases/tag/$tag"
} else {
    Write-Host "GitHub CLI (gh) is not installed." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "To create the release manually:" -ForegroundColor Cyan
    Write-Host "  1. Go to: https://github.com/$owner/$repo/releases/new" -ForegroundColor White
    Write-Host "  2. Tag: $tag" -ForegroundColor White
    Write-Host "  3. Release title: $releaseName" -ForegroundColor White
    Write-Host "  4. Copy the release notes from: release-notes.md (created below)" -ForegroundColor White
    Write-Host "  5. Upload: $zipFile" -ForegroundColor White
    Write-Host "  6. Click 'Publish release'" -ForegroundColor White
    Write-Host ""
    
    # Save release notes to file for manual use
    $body | Out-File -FilePath "release-notes.md" -Encoding UTF8
    Write-Host "✓ Release notes saved to: release-notes.md" -ForegroundColor Green
    
    Write-Host "`nTo install GitHub CLI:" -ForegroundColor Cyan
    Write-Host "  winget install GitHub.cli" -ForegroundColor White
    Write-Host "  OR download from: https://cli.github.com/" -ForegroundColor White
}
