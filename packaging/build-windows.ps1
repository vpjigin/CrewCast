# Build dist/Crewcast-windows-x64.msi with bundled Java 21 (x64 Windows only).
$ErrorActionPreference = "Stop"

function Die($msg) {
    Write-Error $msg
    exit 1
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Command
    )
    $exe = $Command[0]
    $args = @()
    if ($Command.Length -gt 1) {
        $args = $Command[1..($Command.Length - 1)]
    }
    & $exe @args
    if ($LASTEXITCODE -ne 0) {
        Die ("Command failed ({0}): {1}" -f $LASTEXITCODE, ($Command -join " "))
    }
}

$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path "$Root/pom.xml")) {
    Die "Could not locate repo root from $PSScriptRoot"
}

function Get-MavenVersionFromPom {
    [xml]$pom = Get-Content -Path (Join-Path $Root "pom.xml") -Raw
    $ver = $pom.project.version
    if ([string]::IsNullOrWhiteSpace($ver) -and $null -ne $pom.project.parent) {
        $ver = $pom.project.parent.version
    }
    if ([string]::IsNullOrWhiteSpace($ver)) {
        Die "Could not read project.version from pom.xml"
    }
    $ver = [string]$ver
    $ver = $ver -replace "-SNAPSHOT$", ""
    $ver = $ver.Trim()
    if ($ver -notmatch "^\d+(\.\d+){0,2}$") {
        $trunc = $ver
        if ($trunc.Length -gt 80) { $trunc = $trunc.Substring(0, 80) }
        Die "Invalid Maven version from pom.xml: '$trunc'"
    }
    return $ver
}

function Get-MavenVersion {
    return Get-MavenVersionFromPom
}

function Get-ResolvedVersion {
    $fallback = Get-MavenVersionFromPom
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

function Find-DesktopJar {
    $dir = Join-Path $Root "desktop/target"
    $candidates = Get-ChildItem -Path $dir -Filter "crewcast-desktop-*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -notmatch '\.original$' -and
            $_.Name -notmatch '-sources\.jar$' -and
            $_.Name -notmatch '-javadoc\.jar$'
        }
    if ($candidates.Count -ne 1) {
        $names = @($candidates | ForEach-Object { $_.FullName })
        if ($names.Count -eq 0) { $names = @("(none)") }
        Die ("Expected exactly one crewcast-desktop jar in {0}; found: {1}" -f $dir, ($names -join ", "))
    }
    return $candidates[0].FullName
}

# Fixed upgrade code for in-place MSI upgrades (do not change between releases).
$WinUpgradeUuid = "8f3e2a1b-5c4d-4e9f-a2b1-6d7e8f9a0bcd"

$javaVerLines = & java -version 2>&1
$javaVer = ($javaVerLines | Select-Object -First 1) -join " "
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
try {
    Invoke-Native @("mvn", "-pl", "desktop", "-am", "package", "-DskipTests")
} finally {
    Pop-Location
}

$jar = Find-DesktopJar
Write-Host "==> Desktop jar: $jar"

Invoke-Native @("python", (Join-Path $Root "packaging/tools/generate_icons.py"))
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
try {
    Push-Location $tmp
    Invoke-Native @("jar", "xf", $jar)
    Pop-Location
    $cp = Join-Path $tmp "BOOT-INF/classes"
    $libs = Join-Path $tmp "BOOT-INF/lib/*"
    $jdepsFile = Join-Path $tmp "jdeps-out.txt"
    & jdeps --ignore-missing-deps --multi-release 21 --recursive --print-module-deps `
        --class-path "$cp;$libs" $cp 2>$null | Out-File -FilePath $jdepsFile -Encoding ascii
    if ($LASTEXITCODE -ne 0) { Die "jdeps failed ($LASTEXITCODE)" }
    $jdepsOut = (Get-Content -Path $jdepsFile -Raw).Trim()
} finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
$mandatory = "java.desktop,java.logging,java.naming,java.management,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.crypto.mscapi"
$allModules = ($jdepsOut + "," + $mandatory).Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique
$moduleList = $allModules -join ","
Write-Host "    modules: $moduleList"

Remove-Item -Recurse -Force $runtime -ErrorAction SilentlyContinue
Invoke-Native @("jlink", "--output", $runtime, "--add-modules", $moduleList, "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2")

$out = Join-Path $Root "dist/Crewcast-windows-x64.msi"
Remove-Item -Force $out -ErrorAction SilentlyContinue

Write-Host "==> jpackage (msi) app-version=$appVersion main-jar=$((Split-Path -Leaf $jar))"
Invoke-Native @(
    "jpackage",
    "--type", "msi",
    "--dest", (Join-Path $Root "dist"),
    "--name", "Crewcast",
    "--app-version", $appVersion,
    "--vendor", "Solocrew",
    "--copyright", "Copyright (c) 2026 Solocrew",
    "--icon", $icon,
    "--input", $inputDir,
    "--main-jar", (Split-Path -Leaf $jar),
    "--main-class", "org.springframework.boot.loader.launch.JarLauncher",
    "--runtime-image", $runtime,
    "--win-menu",
    "--win-shortcut",
    "--win-dir-chooser",
    "--win-upgrade-uuid", $WinUpgradeUuid
)

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
