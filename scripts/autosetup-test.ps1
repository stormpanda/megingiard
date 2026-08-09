param(
    [Parameter(Position = 0)]
    [ValidateSet('state', 'record', 'collect')]
    [string]$Action = 'state',

    [switch]$Reset,
    [string]$Label = '',
    [string]$Package = 'com.stormpanda.megingiard.debug'
)

$ErrorActionPreference = 'Stop'

$Adb     = 'C:\Users\Guset\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$OutDir  = Join-Path $PSScriptRoot '..\build\autosetup-runs'
$LogFile = Join-Path $OutDir 'current.log'
$PidFile = Join-Path $OutDir 'current.pid'

function Get-Setting($scope, $key) {
    (& $Adb shell settings get $scope $key 2>$null).Trim()
}

function Show-State {
    $dev = Get-Setting 'global' 'development_settings_enabled'
    $usb = Get-Setting 'global' 'adb_enabled'
    $wifi = Get-Setting 'global' 'adb_wifi_enabled'
    $acc = & $Adb shell settings get secure enabled_accessibility_services 2>$null

    Write-Host "`n=== Current State ===" -ForegroundColor Cyan
    Write-Host "Developer Options : $dev"
    Write-Host "USB Debugging     : $usb"
    Write-Host "Wireless Debugging: $wifi"
    Write-Host "Accessibility     : $acc"
}

function Clear-AppRecord {
    Write-Host "`nClearing $Package data..." -ForegroundColor Yellow
    & $Adb shell pm clear $Package | Out-Null
    & $Adb shell settings put secure enabled_accessibility_services "$Package/com.stormpanda.megingiard.services.MegingiardAccessibilityService" | Out-Null
    & $Adb shell settings put secure accessibility_enabled 1 | Out-Null
    Write-Host "Accessibility service granted." -ForegroundColor Green
}

function Start-Record {
    Show-State
    if ($Reset) {
        Clear-AppRecord
    }

    if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }
    Stop-Record -Quiet

    & $Adb logcat -c
    $proc = Start-Process -FilePath $Adb -ArgumentList 'logcat -v time' -RedirectStandardOutput $LogFile -NoNewWindow -PassThru
    $proc.Id | Out-File $PidFile -Encoding ascii

    Write-Host "`nRecording started -> $LogFile" -ForegroundColor Green
    Write-Host "Click Auto Setup on device, then run: .\scripts\autosetup-test.ps1 collect -Label <name>" -ForegroundColor Yellow
}

function Stop-Record([switch]$Quiet) {
    if (Test-Path $PidFile) {
        $pidToKill = (Get-Content $PidFile -ErrorAction SilentlyContinue).Trim()
        if ($pidToKill) {
            Stop-Process -Id $pidToKill -ErrorAction SilentlyContinue
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }
    if (-not $Quiet) { Write-Host "Recording stopped." -ForegroundColor Cyan }
}

function Collect-Result {
    Stop-Record
    if (-not (Test-Path $LogFile)) { throw "Logfile not found, run record first" }

    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $name  = if ($Label) { "$stamp-$($Label -replace '[^\w\-]', '_')" } else { $stamp }
    $saved = Join-Path $OutDir "$name.log"
    Move-Item $LogFile $saved -Force

    $lines = Select-String -Path $saved -Pattern 'Mgnrd' | ForEach-Object { $_.Line }

    Write-Host "`n=== Megingiard Output ($($lines.Count) lines) ===" -ForegroundColor Cyan
    $lines | ForEach-Object { Write-Host "  $_" }
    Write-Host "`nSaved log to: $saved" -ForegroundColor Green
}

switch ($Action) {
    'state'   { Show-State }
    'record'  { Start-Record }
    'collect' { Collect-Result }
}
