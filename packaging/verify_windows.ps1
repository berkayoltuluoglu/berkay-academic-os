param([Parameter(Mandatory=$true)][string]$Installer, [string]$VerificationDirectory)
$ErrorActionPreference = 'Stop'
if (!$VerificationDirectory) {
    $VerificationDirectory = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { $env:TEMP }
}
if (Test-Path 'HKCU:\Software\BerkayAcademicOS') {
    $ExistingInstall = (Get-ItemProperty 'HKCU:\Software\BerkayAcademicOS' -ErrorAction SilentlyContinue).InstallDir
    if ($ExistingInstall) { throw 'Run installer verification in a clean Windows test account; an existing installation is registered.' }
}
$InstallDirectory = Join-Path $VerificationDirectory 'AcademicOS-installed'
$SmokeDirectory = Join-Path $VerificationDirectory ('AcademicOS-smoke-profile-' + [guid]::NewGuid().ToString('N'))
$ProfileDirectory = Join-Path $env:LOCALAPPDATA 'BerkayAcademicOS'
New-Item -ItemType Directory -Path (Join-Path $ProfileDirectory 'data') -Force | Out-Null
$Marker = Join-Path $ProfileDirectory 'data/installer-test-marker.txt'
Set-Content -Path $Marker -Value 'keep-user-data'

$InstallProcess = Start-Process -FilePath $Installer -ArgumentList @('/S', "/D=$InstallDirectory") -Wait -PassThru
if ($InstallProcess.ExitCode -ne 0) { throw "Installer failed: $($InstallProcess.ExitCode)" }
$Java = Join-Path $InstallDirectory 'runtime/bin/java.exe'
& $Java '-Dacademic.os.packaged=true' '-cp' "$InstallDirectory/app/lib/*" 'org.example.AppLauncher' '--self-check'
if ($LASTEXITCODE -ne 0) { throw 'Bundled runtime package check failed.' }

# Exercise the installed EXE, bundled JavaFX native libraries and actual application window.
Start-Process -FilePath (Join-Path $InstallDirectory 'BerkayAcademicOS.exe') -ArgumentList @('--smoke-test', "`"$SmokeDirectory`"") -Wait
$Deadline = (Get-Date).AddSeconds(40)
while (!(Test-Path (Join-Path $SmokeDirectory 'package-smoke.ok')) -and (Get-Date) -lt $Deadline) { Start-Sleep -Milliseconds 250 }
if (!(Test-Path (Join-Path $SmokeDirectory 'package-smoke.ok'))) { throw 'The installed launcher did not render the JavaFX shell.' }

# An upgrade and uninstall must leave the user's separate profile untouched.
$ApplicationJar = Get-ChildItem (Join-Path $InstallDirectory 'app/lib/app-*.jar') | Select-Object -First 1
$ObsoleteJar = Join-Path $InstallDirectory 'app/lib/obsolete-release.jar'
Copy-Item $ApplicationJar.FullName $ObsoleteJar
$Upgrade = Start-Process -FilePath $Installer -ArgumentList @('/S', "/D=$InstallDirectory") -Wait -PassThru
if ($Upgrade.ExitCode -ne 0) { throw 'Upgrade installation failed.' }
if (Test-Path $ObsoleteJar) { throw 'Upgrade left obsolete application libraries on the classpath.' }
& $Java '-Dacademic.os.packaged=true' '-cp' "$InstallDirectory/app/lib/*" 'org.example.AppLauncher' '--self-check'
if ($LASTEXITCODE -ne 0) { throw 'Upgraded package check failed.' }
if ((Get-Content $Marker).Trim() -ne 'keep-user-data') { throw 'Upgrade changed user data.' }
$Uninstall = Start-Process -FilePath (Join-Path $InstallDirectory 'Uninstall.exe') -ArgumentList '/S' -Wait -PassThru
if ($Uninstall.ExitCode -ne 0) { throw 'Uninstall failed.' }
if ((Get-Content $Marker).Trim() -ne 'keep-user-data') { throw 'Uninstall changed user data.' }
Write-Output 'Windows installation, launcher, upgrade and user-data preservation checks passed.'
Write-Output "Application screenshot: $SmokeDirectory/package-smoke.png"
