# Build dist/Crewcast-windows-x64.msi with bundled Java 21 (x64 Windows only).
$ErrorActionPreference = "Stop"

function Die($msg) {
    Write-Error $msg
    exit 1
}

$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path "$Root/pom.xml")) {
    Die "Could not locate repo root from $PSScriptRoot"
}

function Get-MavenVersion {
    Push-Location $Root
    $v = & mvn -q -DforceStdout help:evaluate -Dexpression=project.version
    Pop-Location
    return ($v -replace "-SNAPSHOT$", "")
}

function Get-ResolvedVersion {
    $fallback = Get-MavenVersion
    $envVal = $env:CREWCAST_RELEASE_VERSION
    if ([string]::IsNullOrWhiteSpace($envVal)) {
        Write-Warning "No valid CREWCAST_RELEASE_VERSION; using Maven fallback $fallback."
        return $fallback
    }
    $raw = $envVal.TrimStart("v")
    if ($raw -match "-") {
        $raw = $raw.Substring(0, $raw.IndexOf("-"))
    }
    if ($raw -match "^\d+(\.\d+){0,2}$") {
        return $raw
    }
    Write-Warning "CREWCAST_RELEASE_VERSION='$envVal' is not a valid numeric version; using Maven fallback $fallback."
    return $fallback
}

function Get-TripleVersion([string]$raw) {
    if ($raw -match "^(\d+)\.") {
        $major = [int]$Matches[1]
        if ($major -lt 1) { return "1.0.0" }
    }
    if ($raw -match "^\d+\.\d+\.\d+$") { return $raw }
    if ($raw -match "^\d+\.\d+$") { return "$raw.0" }
    if ($raw -match "^\d+$") { return "$raw.0.0" }
    return "1.0.0"
}

function Get-MacosAppVersion([string]$raw) {
    if ($raw -match "^(\d+)") {
        $major = [int]$Matches[1]
        if ($major -lt 1) { return "1.0.0" }
    }
    return $raw
}

# Fixed upgrade code for in-place MSI upgrades (do not change between releases).
$WinUpgradeUuid = "8f3e2a1b-5c4d-4e9f-a2b1-6d7e8f9a0bcd"

$javaVer = (& java -version 2>&1 | Select-Object -First 1)
if ($javaVer -notmatch 'version "(\d+)') { Die "JDK 21+ required." }
if ([int]$Matches[1] -lt 21) { Die "JDK 21+ required (found $($Matches[1]))." }

foreach ($cmd in @("java", "jdeps", "jlink", "jpackage", "jar", "mvn", "python")) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) { Die "Missing command: $cmd" }
}

$rawVersion = Get-ResolvedVersion
$appVersion = Get-TripleVersion $rawVersion
Write-Host "==> Version: raw=$rawVersion jpackage=$appVersion"

Write-Host "==> Maven build"
Push-Location $Root
& mvn -pl desktop -am package -DskipTests
if ($LASTEXITCODE -ne 0) { Die "Maven build failed" }
$mvnVersion = & mvn -q -DforceStdout help:evaluate -Dexpression=project.version
$jar = Join-Path $Root "desktop/target/crewcast-desktop-$mvnVersion.jar"
Pop-Location
if (-not (Test-Path $jar)) { Die "Jar not found: $jar" }

& python "$Root/packaging/tools/generate_icons.py"
$icon = Join-Path $Root "packaging/windows/Crewcast.ico"
if (-not (Test-Path $icon)) { Die "Missing icon: $icon" }

$work = Join-Path $Root "packaging/windows/build"
$inputDir = Join-Path $work "input"
$runtime = Join-Path $work "runtime"
Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $inputDir, (Join-Path $Root "dist") | Out-Null
Copy-Item $jar $inputDir

Write-Host "==> jdeps + jlink"
$tmp = New-TemporaryFile | ForEach-Object { Remove-Item $_; New-Item -ItemType Directory -Path $_.FullName }
Push-Location $tmp
& jar xf $jar
Pop-Location
$cp = Join-Path $tmp "BOOT-INF/classes"
$libs = Join-Path $tmp "BOOT-INF/lib/*"
$jdepsOut = & jdeps --ignore-missing-deps --multi-release 21 --recursive --print-module-deps `
    --class-path "$cp;$libs" $cp 2>$null
Remove-Item -Recurse -Force $tmp
$mandatory = "java.desktop,java.logging,java.naming,java.management,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec"
$allModules = ($jdepsOut + "," + $mandatory).Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique
$moduleList = $allModules -join ","
Write-Host "    modules: $moduleList"

Remove-Item -Recurse -Force $runtime -ErrorAction SilentlyContinue
& jlink --output $runtime --add-modules $moduleList --strip-debug --no-header-files --no-man-pages --compress=2
if ($LASTEXITCODE -ne 0) { Die "jlink failed" }

$out = Join-Path $Root "dist/Crewcast-windows-x64.msi"
Remove-Item -Force $out -ErrorAction SilentlyContinue

Write-Host "==> jpackage (msi)"
& jpackage `
    --type msi `
    --dest (Join-Path $Root "dist") `
    --name Crewcast `
    --app-version $appVersion `
    --vendor Solocrew `
    --copyright "Copyright (c) 2026 Solocrew" `
    --icon $icon `
    --input $inputDir `
    --main-jar (Split-Path -Leaf $jar) `
    --main-class org.springframework.boot.loader.launch.JarLauncher `
    --runtime-image $runtime `
    --win-menu `
    --win-shortcut `
    --win-dir-chooser `
    --win-upgrade-uuid $WinUpgradeUuid

if ($LASTEXITCODE -ne 0) { Die "jpackage failed" }

$candidates = @(
    (Join-Path $Root "dist/Crewcast-$appVersion.msi"),
    (Join-Path $Root "dist/Crewcast.msi")
)
foreach ($c in $candidates) {
    if ((Test-Path $c) -and $c -ne $out) {
        Move-Item -Force $c $out
        break
    }
}
Get-ChildItem (Join-Path $Root "dist") -Filter "*.msi" | ForEach-Object {
    if ($_.FullName -ne $out -and $_.Name -like "Crewcast*") {
        Move-Item -Force $_.FullName $out
    }
}

if (-not (Test-Path $out)) { Die "Expected $out" }
$bytes = (Get-Item $out).Length
$mb = [math]::Round($bytes / 1MB, 1)
Write-Host "Done: $out ($mb MiB)"
