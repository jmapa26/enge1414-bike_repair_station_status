$ErrorActionPreference = "Stop"

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectDir

$buildDir = Join-Path $projectDir "build"
$jarDir = Join-Path $buildDir "jars"
$classesDir = Join-Path $buildDir "classes"
$installerDir = Join-Path $projectDir "dist"
$appName = "Bike Repair Station Status"
$jarName = "bike-repair-status.jar"

New-Item -ItemType Directory -Force -Path $classesDir, $jarDir, $installerDir | Out-Null
Remove-Item -Recurse -Force -Path $classesDir\* -ErrorAction SilentlyContinue

Write-Host "Compiling..."
javac -encoding UTF-8 -d $classesDir src\App.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Creating application JAR..."
jar --create --file (Join-Path $jarDir $jarName) --main-class App -C $classesDir .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Creating native Windows executable..."
jpackage `
    --type exe `
    --name $appName `
    --app-version "1.0.0" `
    --input $jarDir `
    --main-jar $jarName `
    --main-class App `
    --dest $installerDir `
    --win-menu `
    --win-shortcut `
    --vendor "Bike Repair Station"

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Windows installer created in $installerDir"
