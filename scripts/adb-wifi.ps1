<#
.SYNOPSIS
  Connects adb to the tablet over Wi-Fi (Android "Wireless debugging").

.DESCRIPTION
  Wireless debugging listens on a random port that changes every time it is switched on (and it is
  switched off by Android at every reboot). This script finds the current port via mDNS
  (`adb mdns services`) and runs `adb connect`. The PC must already be paired with the tablet
  (one-time: Developer options > Wireless debugging > Pair device with pairing code, then `adb pair`).

.PARAMETER Ip
  Only use the service advertised by this IP (useful with several devices). Default: the first one found.

.EXAMPLE
  .\scripts\adb-wifi.ps1
  .\scripts\adb-wifi.ps1 -Ip 192.168.1.50
#>
param([string]$Ip = "")

$adb = if ($env:ANDROID_HOME -and (Test-Path "$env:ANDROID_HOME\platform-tools\adb.exe")) {
    "$env:ANDROID_HOME\platform-tools\adb.exe"
} else {
    "adb"
}

# mDNS answers can take a moment after wireless debugging is switched on.
$target = $null
foreach ($attempt in 1..5) {
    $services = & $adb mdns services | Select-String "_adb-tls-connect._tcp"
    if ($Ip) { $services = $services | Where-Object { $_.Line -match [regex]::Escape("$Ip`:") } }
    $first = $services | Select-Object -First 1
    if ($first) {
        $target = ($first.Line.Trim() -split "\s+")[-1]
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $target) {
    Write-Host "No wireless-debugging service found. Is 'Wireless debugging' ON on the tablet and is it on the same network?" -ForegroundColor Yellow
    Write-Host "After a reboot Android switches it off: turn it on via the Quick Settings tile or Developer options."
    exit 1
}

& $adb connect $target
& $adb devices -l
