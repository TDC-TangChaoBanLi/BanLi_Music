#Requires -Version 5.1
<#
.SYNOPSIS
  Build BanLiMusic Windows desktop packages on Windows.

.DESCRIPTION
  Builds app-image, zip, exe, msi, no-jre zip and no-jre MSI from the current
  desktopApp release jars. Run this on Windows; Linux packages use
  scripts/package-linux.sh on Linux.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 -Jlink

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 `
      -Targets zip,exe,nojre-msi -Jlink -JdkHome C:\JDK\jdk-25 -WixDir C:\wix314

.EXAMPLE
  $env:BANLI_SIGN_PASSWORD = "<PFX_PASSWORD>"
  powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 `
      -Targets zip,exe,msi -Sign -CertificatePath C:\keys\banli.pfx
#>
param(
    [string]$Version = "",
    [string]$Targets = "all-windows",
    [string]$JdkHome = "",
    [string]$WixDir = "",
    [string]$Dist = "",
    [string]$ClasspathFile = "",
    [string]$WorkDir = "",
    [string]$Icon = "",
    [string]$JlinkModules = "java.base,java.desktop,java.instrument,java.logging,java.prefs,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.httpserver,jdk.zipfs",
    [switch]$Jlink,
    [switch]$SkipBuild,
    [switch]$KeepTemp,
    [switch]$Sign,
    [string]$SignTool = "",
    [string]$CertificatePath = "",
    [string]$CertificateThumbprint = "",
    [string]$TimestampUrl = "http://timestamp.digicert.com"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)

$Root = Split-Path -Parent $PSScriptRoot
if (-not $Dist) { $Dist = Join-Path $Root "dist" }
$BuildDir = Join-Path $Root "build\desktop-package"
if (-not $WorkDir) { $WorkDir = Join-Path $env:TEMP "banli-package" }
$InputDir = Join-Path $WorkDir "input"
$UpgradeUuid = "96341748-fd12-47ca-a45f-db1d1c6a8358"
$NoJreUpgradeCode = "26e51776-c54b-45ed-8989-a94a5f55bba3"
$CertificatePassword = $env:BANLI_SIGN_PASSWORD
$BuiltFiles = @()

function Fail([string]$Message) {
    throw "Packaging failed: $Message"
}

function Note([string]$Message) {
    Write-Host "==> $Message"
}

function Resolve-SignTool {
    if ($SignTool) {
        if (Test-Path $SignTool) { return (Resolve-Path $SignTool).Path }
        $command = Get-Command $SignTool -ErrorAction SilentlyContinue
        if ($command) { return $command.Source }
        Fail "signtool not found: $SignTool"
    }
    $command = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $roots = @()
    if (${env:ProgramFiles(x86)}) { $roots += (Join-Path ${env:ProgramFiles(x86)} "Windows Kits\10\bin") }
    if ($env:ProgramFiles) { $roots += (Join-Path $env:ProgramFiles "Windows Kits\10\bin") }
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        $candidate = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName "x64\signtool.exe" } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($candidate) { return $candidate }
    }
    Fail "signtool.exe not found; install Windows SDK or pass -SignTool"
}

function Invoke-SignFile([string]$File) {
    if (-not $Sign) { return }
    if (-not (Test-Path $File)) { Fail "signing target not found: $File" }
    $signArgs = @(
        "sign",
        "/fd", "SHA256",
        "/td", "SHA256",
        "/tr", $TimestampUrl,
        "/d", "BanLiMusic"
    )
    if ($CertificatePath) {
        $signArgs += @("/f", (Resolve-Path $CertificatePath).Path)
        if ($CertificatePassword) { $signArgs += @("/p", $CertificatePassword) }
    } else {
        $signArgs += @("/sha1", $CertificateThumbprint)
    }
    $signArgs += $File
    Note "Signing $(Split-Path -Leaf $File)"
    & $script:SignToolPath @signArgs | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "signtool sign failed: $File" }
    & $script:SignToolPath verify /pa /v $File | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "signtool verify failed: $File" }
}

if (-not $Version) {
    $moduleFile = Join-Path $Root "androidApp\module.yaml"
    if (Test-Path $moduleFile) {
        $moduleText = Get-Content $moduleFile -Raw
        if ($moduleText -match 'versionName:\s*"([^"]+)"') { $Version = $Matches[1] }
    }
}
if (-not $Version) { Fail "cannot read versionName; pass -Version" }

$targetList = @($Targets -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($targetList.Count -eq 0) { Fail "no targets specified" }
$targetList = @($targetList | Select-Object -Unique)

if (($targetList -contains "all") -or ($targetList -contains "all-windows")) {
    $targetList = @("app-image", "zip", "exe", "msi", "nojre-zip", "nojre-msi")
}

foreach ($target in $targetList) {
    if ($target -notin @("app-image", "zip", "exe", "msi", "nojre-zip", "nojre-msi")) {
        Fail "unknown target: $target"
    }
}

if (-not $JdkHome) {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\jpackage.exe"))) {
        $JdkHome = $env:JAVA_HOME
    } else {
        $jpackageCommand = Get-Command jpackage.exe -ErrorAction SilentlyContinue
        if ($jpackageCommand) {
            $JdkHome = Split-Path -Parent (Split-Path -Parent $jpackageCommand.Source)
        }
    }
}
if (-not $JdkHome) { Fail "jpackage.exe not found; pass -JdkHome" }
$jpackage = Join-Path $JdkHome "bin\jpackage.exe"
$jlinkExe = Join-Path $JdkHome "bin\jlink.exe"
$jarExe = Join-Path $JdkHome "bin\jar.exe"
if (-not (Test-Path $jpackage)) { Fail "jpackage.exe not found in $JdkHome" }

$needsWix = ($targetList -contains "exe") -or ($targetList -contains "msi") -or ($targetList -contains "nojre-msi")
if ($needsWix) {
    if ($WixDir) { $env:PATH = "$WixDir;$env:PATH" }
    foreach ($tool in @("candle.exe", "light.exe")) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            Fail "$tool not found; add WiX 3.x bin to PATH or pass -WixDir"
        }
    }
    if (($targetList -contains "nojre-msi") -and (-not (Get-Command "heat.exe" -ErrorAction SilentlyContinue))) {
        Fail "heat.exe not found; nojre-msi requires WiX"
    }
}

$script:SignToolPath = $null
if ($Sign) {
    if (-not $CertificatePath -and -not $CertificateThumbprint) {
        Fail "signing requires -CertificatePath or -CertificateThumbprint"
    }
    if ($CertificatePath -and -not (Test-Path $CertificatePath)) {
        Fail "certificate not found: $CertificatePath"
    }
    $script:SignToolPath = Resolve-SignTool
}

New-Item -ItemType Directory -Force -Path $Dist, $BuildDir | Out-Null
if (Test-Path $InputDir) { Remove-Item -Recurse -Force $InputDir }

if (-not $Icon) {
    $png = Join-Path $Root "shared\composeResources\drawable\banli_logo.png"
    if (Test-Path $png) {
        $Icon = Join-Path $WorkDir "banli.ico"
        New-Item -ItemType Directory -Force (Split-Path -Parent $Icon) | Out-Null
        $pngBytes = [IO.File]::ReadAllBytes($png)
        $stream = New-Object IO.MemoryStream
        $writer = New-Object IO.BinaryWriter($stream)
        $writer.Write([UInt16]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]1)
        $writer.Write([Byte]0)
        $writer.Write([Byte]0)
        $writer.Write([Byte]0)
        $writer.Write([Byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$pngBytes.Length)
        $writer.Write([UInt32]22)
        $writer.Write($pngBytes)
        $writer.Flush()
        [IO.File]::WriteAllBytes($Icon, $stream.ToArray())
        $writer.Dispose()
        $stream.Dispose()
    }
}
if ($Icon -and (-not (Test-Path $Icon))) { Fail "icon not found: $Icon" }

if (-not $SkipBuild) {
    $kotlinBat = Join-Path $Root "kotlin.bat"
    if (-not (Test-Path $kotlinBat)) { Fail "kotlin.bat not found" }
    Note "Building desktopApp release jars"
    & $kotlinBat build -v release -m desktopApp
    if ($LASTEXITCODE -ne 0) { Fail "kotlin build failed with exit code $LASTEXITCODE" }
}

function Read-Classpath([string]$File) {
    $lines = Get-Content $File
    for ($i = 0; $i -lt $lines.Count - 1; $i++) {
        if ($lines[$i].Trim() -eq "-cp") { return $lines[$i + 1].Trim() }
    }
    return ""
}

function Test-Classpath([string]$Classpath) {
    if (-not $Classpath) { return $false }
    foreach ($path in $Classpath.Split(";")) {
        if (-not (Test-Path $path)) { return $false }
    }
    return (($Classpath -match "desktopApp-jvm\.jar") -and ($Classpath -match "shared-jvm\.jar"))
}

function Capture-Classpath {
    $marker = Get-Date
    $log = Join-Path $BuildDir "classpath-run.log"
    $logErr = Join-Path $BuildDir "classpath-run.err.log"
    New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
    Remove-Item $log, $logErr -Force -ErrorAction SilentlyContinue
    Note "Launching desktopApp once to capture its runtime classpath"
    $process = Start-Process -FilePath (Join-Path $Root "kotlin.bat") `
        -ArgumentList @("run", "-m", "desktopApp", "--no-compose-hot-reload") `
        -WorkingDirectory $Root -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $log -RedirectStandardError $logErr
    $argsFile = $null
    for ($i = 0; $i -lt 240; $i++) {
        $candidate = Get-ChildItem (Join-Path $Root "build\temp") -Filter "java-args-*.txt" -File -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTime -gt $marker } |
            Sort-Object LastWriteTime |
            Select-Object -Last 1
        if ($candidate) { $argsFile = $candidate.FullName; break }
        if ($process.HasExited) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $process.HasExited) {
        & taskkill.exe /T /F /PID $process.Id 2>$null | Out-Null
    }
    if (-not $argsFile) { Fail "cannot capture classpath; see $log" }
    return Read-Classpath $argsFile
}

$classpath = ""
if ($ClasspathFile) {
    if (-not (Test-Path $ClasspathFile)) { Fail "classpath file not found: $ClasspathFile" }
    $classpath = Read-Classpath $ClasspathFile
    if (-not $classpath) { $classpath = (Get-Content $ClasspathFile -TotalCount 1).Trim() }
    if (-not (Test-Classpath $classpath)) { Fail "invalid classpath in $ClasspathFile" }
} else {
    $latest = Get-ChildItem (Join-Path $Root "build\temp") -Filter "java-args-*.txt" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime |
        Select-Object -Last 1
    if ($latest) { $classpath = Read-Classpath $latest.FullName }
    if (-not (Test-Classpath $classpath)) { $classpath = Capture-Classpath }
    if (-not (Test-Classpath $classpath)) { Fail "invalid classpath" }
}

Note "Preparing Windows input jars"
New-Item -ItemType Directory -Force -Path $InputDir | Out-Null
$seen = @{}
foreach ($jar in $classpath.Split(";")) {
    $base = Split-Path -Leaf $jar
    $dest = Join-Path $InputDir $base
    if ($seen.ContainsKey($base)) {
        if ((Test-Path $dest) -and ((Get-FileHash $jar).Hash -eq (Get-FileHash $dest).Hash)) {
            continue
        }
        $stem = [IO.Path]::GetFileNameWithoutExtension($base)
        $ext = [IO.Path]::GetExtension($base)
        $n = 2
        while (Test-Path (Join-Path $InputDir "$stem-dup$n$ext")) { $n++ }
        $base = "$stem-dup$n$ext"
        $dest = Join-Path $InputDir $base
        Note "Duplicate jar renamed to $base"
    }
    Copy-Item -Path $jar -Destination $dest -Force
    $seen[$base] = $true
}
Get-ChildItem $InputDir -Filter "*linux-x64*.jar" -File | Remove-Item -Force
if (-not (Test-Path (Join-Path $InputDir "desktopApp-jvm.jar"))) { Fail "desktopApp-jvm.jar missing" }
if (-not (Get-ChildItem $InputDir -Filter "*skiko-awt-runtime-windows-x64*.jar" -File)) {
    Fail "Windows Skiko runtime jar missing"
}

$runtimeImage = $null
if ($Jlink) {
    if (-not (Test-Path $jlinkExe)) { Fail "jlink.exe not found in $JdkHome" }
    $jlinkHelp = (& $jlinkExe --help 2>&1 | Out-String)
    $compress = "2"
    if ($jlinkHelp -match "zip-") { $compress = "zip-6" }
    $runtimeImage = Join-Path $WorkDir "runtime-win"
    if (Test-Path $runtimeImage) { Remove-Item -Recurse -Force $runtimeImage }
    $jlinkArgs = @(
        "--add-modules", $JlinkModules,
        "--strip-debug", "--no-header-files", "--no-man-pages",
        "--compress=$compress",
        "--output", $runtimeImage
    )
    $jmods = Join-Path $JdkHome "jmods"
    if (Test-Path $jmods) { $jlinkArgs = @("--module-path", $jmods) + $jlinkArgs }
    Note "Creating trimmed Windows runtime with jlink"
    & $jlinkExe @jlinkArgs | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "jlink failed with exit code $LASTEXITCODE" }
    if (-not (Test-Path (Join-Path $runtimeImage "bin\java.exe"))) { Fail "jlink did not create java.exe" }
}

$commonArgs = @(
    "--name", "BanLiMusic",
    "--app-version", $Version,
    "--input", $InputDir,
    "--main-jar", "desktopApp-jvm.jar",
    "--main-class", "com.banlimusic.desktop.MainKt",
    "--java-options", "-Dbanlimusic.version=$Version"
)
if ($runtimeImage) { $commonArgs += @("--runtime-image", $runtimeImage) }
if ($Icon) { $commonArgs += @("--icon", $Icon) }

function Get-PackageArgs([string]$AppImage) {
    $packageArgs = @(
        "--name", "BanLiMusic",
        "--app-version", $Version,
        "--app-image", $AppImage
    )
    if ($Icon) { $packageArgs += @("--icon", $Icon) }
    return $packageArgs
}

function Invoke-JPackage([string[]]$Arguments) {
    & $jpackage @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "jpackage failed with exit code $LASTEXITCODE" }
}

function Trim-IconJar([string]$JarPath) {
    if (-not (Test-Path $jarExe)) {
        Note "jar.exe not found; keeping the full material-icons-extended jar"
        return
    }
    $sources = @(
        (Join-Path $Root "shared\src"),
        (Join-Path $Root "androidApp\src"),
        (Join-Path $Root "desktopApp\src")
    )
    $usedIcons = @(
        $sources | ForEach-Object {
            if (Test-Path $_) {
                Get-ChildItem $_ -Recurse -File -Filter "*.kt" |
                    Select-String -Pattern 'Icons(?:\.AutoMirrored)?\.Rounded\.([A-Za-z0-9_]+)' -AllMatches |
                    ForEach-Object { $_.Matches } |
                    ForEach-Object { $_.Groups[1].Value }
            }
        } | Sort-Object -Unique
    )
    if ($usedIcons.Count -eq 0) {
        Note "No icon references found; keeping the full icon jar"
        return
    }

    $work = Join-Path $WorkDir "icon-trim"
    if (Test-Path $work) { Remove-Item -Recurse -Force $work }
    $extract = Join-Path $work "extract"
    New-Item -ItemType Directory -Force $extract | Out-Null
    & $jarExe --extract --file $JarPath --dir $extract | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Note "Unable to extract icon jar; keeping the full jar"
        Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
        return
    }

    $iconsRoot = Join-Path $extract "androidx\compose\material\icons"
    if (-not (Test-Path $iconsRoot)) {
        Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
        return
    }
    Get-ChildItem $iconsRoot -Directory |
        Where-Object { $_.Name -notin @("rounded", "automirrored") } |
        Remove-Item -Recurse -Force
    $autoRoot = Join-Path $iconsRoot "automirrored"
    if (Test-Path $autoRoot) {
        Get-ChildItem $autoRoot -Directory |
            Where-Object { $_.Name -ne "rounded" } |
            Remove-Item -Recurse -Force
    }
    foreach ($styleDir in @(
        (Join-Path $iconsRoot "rounded"),
        (Join-Path $autoRoot "rounded")
    )) {
        if (-not (Test-Path $styleDir)) { continue }
        Get-ChildItem $styleDir -File -Filter "*Kt.class" |
            Where-Object { ($_.BaseName -replace 'Kt$', '') -notin $usedIcons } |
            Remove-Item -Force
    }

    $trimmed = Join-Path $work "trimmed.jar"
    & $jarExe --create --file $trimmed --no-manifest -C $extract . | Out-Host
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $trimmed)) {
        Note "Unable to rebuild icon jar; keeping the full jar"
        Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
        return
    }
    $before = (Get-Item $JarPath).Length
    Move-Item -Force $trimmed $JarPath
    $after = (Get-Item $JarPath).Length
    Note "material-icons-extended: $before -> $after bytes"
    Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
}

$iconJar = Get-ChildItem $InputDir -Filter "material-icons-extended*.jar" -File |
    Select-Object -First 1
if ($iconJar) {
    Note "Trimming material-icons-extended icon jar"
    Trim-IconJar $iconJar.FullName
}

$script:AppImagePath = $null
function Get-AppImage {
    if ($script:AppImagePath) { return $script:AppImagePath }
    $dest = Join-Path $WorkDir "app-image"
    if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
    New-Item -ItemType Directory -Force $dest | Out-Null
    Note "Creating Windows app-image"
    Invoke-JPackage ($commonArgs + @("--type", "app-image", "--dest", $dest))
    $app = Join-Path $dest "BanLiMusic"
    if (-not (Test-Path (Join-Path $app "BanLiMusic.exe"))) { Fail "BanLiMusic.exe missing from app-image" }
    Invoke-SignFile (Join-Path $app "BanLiMusic.exe")
    $script:AppImagePath = $app
    return $app
}

function New-NoJreApp([string]$Dest) {
    if (Test-Path $Dest) { Remove-Item -Recurse -Force $Dest }
    New-Item -ItemType Directory -Force $Dest | Out-Null
    $libs = Join-Path $Dest "libs"
    New-Item -ItemType Directory -Force $libs | Out-Null
    Get-ChildItem $InputDir -Filter "*.jar" -File | ForEach-Object {
        Copy-Item $_.FullName $libs
    }

    $bat = @'
@echo off
setlocal
set "APP_HOME=%~dp0"
if defined JAVA_HOME (
  set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_BIN=java"
)
"%JAVA_BIN%" -version >nul 2>&1
if errorlevel 1 (
  echo Java 17 or newer is required.
  exit /b 1
)
"%JAVA_BIN%" -Dbanlimusic.version="__BANLI_VERSION__" -cp "%APP_HOME%libs\*" com.banlimusic.desktop.MainKt %*
'@
    $bat = $bat.Replace("__BANLI_VERSION__", $Version)
    $bat = ($bat -replace "`r`n", "`n") -replace "`n", "`r`n"
    [IO.File]::WriteAllText((Join-Path $Dest "BanLiMusic.bat"), $bat, (New-Object Text.UTF8Encoding($false)))

    $readme = @"
BanLiMusic Desktop $Version (Windows x64, no JRE)

Start:
  Double-click BanLiMusic.bat

This folder does not include a Java runtime. Install Java 17 or newer
(Temurin/OpenJDK recommended) and set JAVA_HOME or PATH.

Data:
- Settings/library/history/downloads: %APPDATA%\BanLiMusic
- Artwork and lyrics cache: %LOCALAPPDATA%\BanLiMusic\cache
- Default download folder: %USERPROFILE%\Music\BanLiMusic

Music data comes from the GD Music source and is for learning/reference only.
"@
    $readme = ($readme -replace "`r`n", "`n") -replace "`n", "`r`n"
    [IO.File]::WriteAllText((Join-Path $Dest "README.txt"), $readme, (New-Object Text.UTF8Encoding($false)))
}

function Build-NoJreMsi {
    $msiRoot = Join-Path $WorkDir "nojre-msi"
    if (Test-Path $msiRoot) { Remove-Item -Recurse -Force $msiRoot }
    $app = Join-Path $msiRoot "app"
    New-NoJreApp $app

    $filesWxs = Join-Path $msiRoot "files.wxs"
    Note "Harvesting no-jre MSI files"
    & (Get-Command heat.exe).Source dir $app -cg Files -dr INSTALLDIR -gg -g1 -sfrag -srd -var var.SourceDir -out $filesWxs | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "heat.exe failed with exit code $LASTEXITCODE" }

    $iconXml = ""
    $iconAttr = ""
    if ($Icon) {
        $iconXml = "<Icon Id=`"AppIcon`" SourceFile=`"$Icon`" />`r`n    <Property Id=`"ARPPRODUCTICON`" Value=`"AppIcon`" />"
        $iconAttr = ' Icon="AppIcon"'
    }

    $productWxs = Join-Path $msiRoot "product.wxs"
    $productXml = @"
<?xml version="1.0" encoding="utf-8"?>
<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi">
  <Product Id="*"
           Name="BanLiMusic (Java 17+)"
           Language="1033"
           Version="$Version"
           Manufacturer="BanLiMusic"
           UpgradeCode="$NoJreUpgradeCode"
           Codepage="1252">
    <Package InstallerVersion="500" Compressed="yes" InstallScope="perUser" Platform="x64" />
    <MajorUpgrade DowngradeErrorMessage="A newer version of BanLiMusic is already installed." />
    <MediaTemplate EmbedCab="yes" CompressionLevel="high" />
    <Property Id="ARPNOREPAIR" Value="1" />
    $iconXml

    <Directory Id="TARGETDIR" Name="SourceDir">
      <Directory Id="LocalAppDataFolder">
        <Directory Id="ProgramsFolder" Name="Programs">
          <Directory Id="INSTALLDIR" Name="BanLiMusic" />
        </Directory>
      </Directory>
      <Directory Id="ProgramMenuFolder">
        <Directory Id="AppMenuFolder" Name="BanLiMusic" />
      </Directory>
    </Directory>

    <DirectoryRef Id="AppMenuFolder">
      <Component Id="AppShortcut" Guid="*">
        <Shortcut Id="StartMenuShortcut"
                  Name="BanLiMusic (Java 17+)"
                  Description="BanLiMusic desktop client (requires Java 17+)"
                  Target="[INSTALLDIR]BanLiMusic.bat"
                  WorkingDirectory="INSTALLDIR"$iconAttr />
        <RemoveFolder Id="RemoveAppMenuFolder" On="uninstall" />
        <RegistryValue Root="HKCU" Key="Software\BanLiMusic\NoJre" Name="Installed" Type="integer" Value="1" KeyPath="yes" />
      </Component>
    </DirectoryRef>

    <Feature Id="MainFeature" Title="BanLiMusic" Level="1">
      <ComponentGroupRef Id="Files" />
      <ComponentRef Id="AppShortcut" />
    </Feature>
  </Product>
</Wix>
"@
    $productXml = ($productXml -replace "`r`n", "`n") -replace "`n", "`r`n"
    [IO.File]::WriteAllText($productWxs, $productXml, (New-Object Text.UTF8Encoding($false)))

    $obj = Join-Path $msiRoot "obj"
    New-Item -ItemType Directory -Force $obj | Out-Null
    Note "Linking no-jre MSI"
    & (Get-Command candle.exe).Source "-dSourceDir=$app" "-out" "$obj\" $productWxs $filesWxs | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "candle.exe failed with exit code $LASTEXITCODE" }
    $msi = Join-Path $Dist "BanLiMusic-$Version-nojre.msi"
    & (Get-Command light.exe).Source "-out" $msi "$obj\product.wixobj" "$obj\files.wixobj" | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "light.exe failed with exit code $LASTEXITCODE" }
    $pdb = [IO.Path]::ChangeExtension($msi, ".wixpdb")
    if (Test-Path $pdb) { Remove-Item -Force $pdb }
    Invoke-SignFile $msi
    return $msi
}

foreach ($target in $targetList) {
    switch ($target) {
        "app-image" {
            $app = Get-AppImage
            $dest = Join-Path $Dist "BanLiMusic-$Version-windows-x64"
            if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
            Copy-Item -Path $app -Destination $dest -Recurse -Force
        }
        "zip" {
            $app = Get-AppImage
            $zip = Join-Path $Dist "BanLiMusic-$Version-windows-x64.zip"
            if (Test-Path $zip) { Remove-Item -Force $zip }
            Note "Creating $zip"
            Compress-Archive -Path $app -DestinationPath $zip
            $BuiltFiles += $zip
        }
        "exe" {
            $packageArgs = $commonArgs
            if ($Sign) {
                $app = Get-AppImage
                $packageArgs = Get-PackageArgs $app
            }
            Note "Creating Windows exe installer"
            Invoke-JPackage ($packageArgs + @(
                "--type", "exe", "--dest", $Dist,
                "--win-shortcut", "--win-menu", "--win-dir-chooser",
                "--win-upgrade-uuid", $UpgradeUuid
            ))
            $installer = Join-Path $Dist "BanLiMusic-$Version.exe"
            Invoke-SignFile $installer
            $BuiltFiles += $installer
        }
        "msi" {
            $packageArgs = $commonArgs
            if ($Sign) {
                $app = Get-AppImage
                $packageArgs = Get-PackageArgs $app
            }
            Note "Creating Windows msi installer"
            Invoke-JPackage ($packageArgs + @(
                "--type", "msi", "--dest", $Dist,
                "--win-shortcut", "--win-menu", "--win-dir-chooser",
                "--win-upgrade-uuid", $UpgradeUuid
            ))
            $installer = Join-Path $Dist "BanLiMusic-$Version.msi"
            Invoke-SignFile $installer
            $BuiltFiles += $installer
        }
        "nojre-zip" {
            $app = Join-Path $WorkDir "nojre-zip\BanLiMusic"
            New-NoJreApp $app
            $zip = Join-Path $Dist "BanLiMusic-$Version-windows-x64-nojre.zip"
            if (Test-Path $zip) { Remove-Item -Force $zip }
            Note "Creating $zip"
            Compress-Archive -Path $app -DestinationPath $zip
            $BuiltFiles += $zip
        }
        "nojre-msi" {
            $BuiltFiles += (Build-NoJreMsi)
        }
    }
}

$BuiltFiles = @($BuiltFiles | Select-Object -Unique)
$existing = Get-ChildItem $Dist -File -ErrorAction SilentlyContinue |
    Where-Object { ($_.Name -like "*$Version*") -and ($_.Name -notlike "SHA256SUMS-*") -and ($_.Extension -ne ".wixpdb") }
$BuiltFiles += @($existing | ForEach-Object { $_.FullName })
$BuiltFiles = @($BuiltFiles | Select-Object -Unique)

if ($BuiltFiles.Count -gt 0) {
    Note "Writing SHA256SUMS-$Version.txt"
    $lines = foreach ($file in $BuiltFiles) {
        if ($file -and (Test-Path $file) -and -not (Test-Path $file -PathType Container)) {
            $hash = (Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  $(Split-Path -Leaf $file)"
        }
    }
    Set-Content -Path (Join-Path $Dist "SHA256SUMS-$Version.txt") -Value $lines -Encoding ASCII
}

if (-not $KeepTemp) {
    $resolvedWork = [IO.Path]::GetFullPath($WorkDir)
    $tempRoot = [IO.Path]::GetFullPath($env:TEMP)
    if ($resolvedWork.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and ($resolvedWork -ne $tempRoot)) {
        Remove-Item -Recurse -Force $WorkDir -ErrorAction SilentlyContinue
    }
}

Note "Done. Output: $Dist"
