param(
    [string]$InstallDirectory = "$env:USERPROFILE\.git-publish"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $Root "target\git-publish.jar"

if (-not (Test-Path $Jar)) {
    throw "Build the project first with: mvn clean package"
}

$BinDirectory = Join-Path $InstallDirectory "bin"
$LibDirectory = Join-Path $InstallDirectory "lib"
$ConfigDirectory = Join-Path $InstallDirectory "config"

New-Item -ItemType Directory -Force $BinDirectory, $LibDirectory, $ConfigDirectory | Out-Null

Copy-Item $Jar (Join-Path $LibDirectory "git-publish.jar") -Force
Copy-Item (Join-Path $Root "bin\git-publish.cmd") $BinDirectory -Force

$UserConfig = Join-Path $env:USERPROFILE ".git-publish\config.yml"
if (-not (Test-Path $UserConfig)) {
    Copy-Item (Join-Path $Root "config\config.example.yml") $UserConfig
    Write-Host "Created configuration: $UserConfig"
}

$CurrentUserPath = [Environment]::GetEnvironmentVariable("Path", "User")
if (($CurrentUserPath -split ";") -notcontains $BinDirectory) {
    $NewPath = if ([string]::IsNullOrWhiteSpace($CurrentUserPath)) {
        $BinDirectory
    } else {
        "$CurrentUserPath;$BinDirectory"
    }

    [Environment]::SetEnvironmentVariable("Path", $NewPath, "User")
    Write-Host "Added to the user PATH: $BinDirectory"
    Write-Host "Open a new terminal before running git publish."
}

Write-Host "Installed successfully."
Write-Host "Test with: git publish --help"
