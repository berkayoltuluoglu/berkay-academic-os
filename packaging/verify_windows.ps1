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
$Manifest = Get-Content (Join-Path $InstallDirectory 'package-manifest.json') -Raw | ConvertFrom-Json
foreach ($Entry in $Manifest.files.PSObject.Properties) {
    $File = Join-Path $InstallDirectory $Entry.Name
    if (!(Test-Path $File) -or (Get-FileHash $File -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Entry.Value) {
        throw "Installed payload checksum failed: $($Entry.Name)"
    }
}
& $Java '-Dacademic.os.packaged=true' '-cp' "$InstallDirectory/app/lib/*" 'org.example.AppLauncher' '--self-check'
if ($LASTEXITCODE -ne 0) { throw 'Bundled runtime package check failed.' }

# Exercise the installed batch CLI with a separate Unicode/space-containing profile.
$Cli = Join-Path $InstallDirectory 'academic-os.cmd'
$AgentProfile = Join-Path $SmokeDirectory 'agent profile Türkçe'
$PlanToday = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, 'Turkey Standard Time').Date
$PlanWeek = $PlanToday.AddDays(-(([int]$PlanToday.DayOfWeek + 6) % 7)).ToString('yyyy-MM-dd')
$SnapshotText = & $Cli --profile $AgentProfile snapshot --week $PlanWeek | Out-String
if ($LASTEXITCODE -ne 0) { throw 'Installed CLI snapshot failed.' }
$Snapshot = $SnapshotText | ConvertFrom-Json
if ($Snapshot.errors.Count -ne 0) { throw 'A clean CLI profile reported source errors.' }
& $Cli --profile $AgentProfile pool --week $PlanWeek | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Installed CLI pool failed.' }
& $Cli --profile $AgentProfile free-slots --week $PlanWeek --min 30 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Installed CLI free-slots failed.' }
& $Cli --profile $AgentProfile schema | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Installed CLI schema failed.' }
if (Test-Path $AgentProfile) { throw 'Read-only CLI commands created profile files.' }
$ProposalFile = Join-Path $SmokeDirectory 'agent-proposal.json'
$Proposal = @{
    schemaVersion = 1; id = 'windows-acceptance'; agent = 'Windows acceptance'; note = 'Geçici profilde öneri testi'
    week = $Snapshot.week; fingerprint = $Snapshot.fingerprint
    commands = @(@{ type = 'schedule'; title = 'Tekrar'; date = $PlanToday.ToString('yyyy-MM-dd'); start = '10:00'; minutes = 60; reason = 'Boş aralıkta tekrar' })
}
$Proposal | ConvertTo-Json -Depth 8 | Set-Content -Path $ProposalFile -Encoding utf8
& $Cli --profile $AgentProfile validate $ProposalFile | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Installed CLI validation failed.' }
& $Cli --profile $AgentProfile propose $ProposalFile | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Installed CLI proposal submission failed.' }
$Feedback = & $Cli --profile $AgentProfile proposal-status windows-acceptance | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $Feedback.status -ne 'pending') { throw 'Installed CLI proposal feedback failed.' }
if (Test-Path (Join-Path $AgentProfile 'data/study/sessions.json')) { throw 'A pending proposal changed the actual plan.' }

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
