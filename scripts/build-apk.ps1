$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$localProperties = Join-Path $projectRoot "local.properties"

function Get-AndroidSdkPath {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        [Environment]::GetEnvironmentVariable("ANDROID_HOME", "User"),
        [Environment]::GetEnvironmentVariable("ANDROID_SDK_ROOT", "User")
    )
    if (Test-Path $localProperties) {
        $line = Get-Content $localProperties | Where-Object { $_ -match "^sdk\.dir=" } | Select-Object -First 1
        if ($line) {
            $candidates += (($line -replace "^sdk\.dir=", "") -replace "\\\\", "\")
        }
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return $candidate
        }
    }
    return $null
}

$sdkPath = Get-AndroidSdkPath
if (-not $sdkPath -or -not (Test-Path $sdkPath)) {
    throw "Android SDK not found. Set ANDROID_HOME or create android-apk\local.properties from local.properties.example."
}

$wrapper = Join-Path $projectRoot "gradlew.bat"
$gradleHome = if ($env:GRADLE_HOME) { $env:GRADLE_HOME } else { [Environment]::GetEnvironmentVariable("GRADLE_HOME", "User") }
$fallbackGradleHome = "F:\Gradle\gradle-9.6.0"
if ((-not $gradleHome) -and (Test-Path $fallbackGradleHome)) {
    $gradleHome = $fallbackGradleHome
}
$gradleFromHome = if ($gradleHome) { Join-Path $gradleHome "bin\gradle.bat" } else { $null }
$gradleCommand = Get-Command gradle -ErrorAction SilentlyContinue

Push-Location $projectRoot
try {
    if (Test-Path $wrapper) {
        & $wrapper assembleDebug
    } elseif ($gradleFromHome -and (Test-Path $gradleFromHome)) {
        & $gradleFromHome assembleDebug
    } elseif ($gradleCommand) {
        & $gradleCommand.Source assembleDebug
    } else {
        throw "Gradle not found. Install Gradle 8.x or add a Gradle wrapper inside android-apk."
    }
} finally {
    Pop-Location
}
