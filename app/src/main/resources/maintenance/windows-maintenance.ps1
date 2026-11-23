param([Parameter(Mandatory=$true)][string]$JobFile)
$ErrorActionPreference = 'Stop'
# Use .NET directly: Windows PowerShell may inherit a PSModulePath from PowerShell 7.
function Get-MaintenanceFileHash([string]$Path) {
    $HashAlgorithm = [System.Security.Cryptography.SHA256]::Create()
    $FileStream = $null
    try {
        $FileStream = [System.IO.File]::OpenRead($Path)
        return [System.BitConverter]::ToString($HashAlgorithm.ComputeHash($FileStream)).Replace('-', '').ToLowerInvariant()
    } finally {
        if ($FileStream) { $FileStream.Dispose() }
        $HashAlgorithm.Dispose()
    }
}
$MaintenanceJob = $null
try {
    $MaintenanceJob = Get-Content -LiteralPath $JobFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($MaintenanceJob.mode -notin @('update', 'uninstall')) { throw 'Bilinmeyen bakım işlemi.' }
    if (!(Test-Path -LiteralPath $MaintenanceJob.executable -PathType Leaf)) { throw 'Kurulum dosyası bulunamadı.' }
    if ((Get-MaintenanceFileHash $MaintenanceJob.executable) -ne $MaintenanceJob.sha256) {
        throw 'Kurulum dosyasının SHA-256 değeri değişmiş. İşlem durduruldu.'
    }
    Set-Content -LiteralPath $MaintenanceJob.ready -Value 'ready' -Encoding ASCII
    $MaintenanceDeadline = (Get-Date).AddSeconds(90)
    foreach ($AppProcessId in $MaintenanceJob.waitFor) {
        while (Get-Process -Id $AppProcessId -ErrorAction SilentlyContinue) {
            if ((Get-Date) -gt $MaintenanceDeadline) { throw 'Uygulama tamamen kapanmadı. Kurulum başlatılmadı.' }
            Start-Sleep -Milliseconds 150
        }
    }
    # Verify again after shutdown; the installer must still be the file the user approved.
    if ((Get-MaintenanceFileHash $MaintenanceJob.executable) -ne $MaintenanceJob.sha256) {
        throw 'Kurulum dosyası kapanış sırasında değişmiş. İşlem durduruldu.'
    }
    $MaintenanceArguments = '/S'
    if ($MaintenanceJob.mode -eq 'update') { $MaintenanceArguments += ' /D=' + $MaintenanceJob.installRoot }
    $MaintenanceProcess = Start-Process -FilePath $MaintenanceJob.executable -ArgumentList $MaintenanceArguments -Wait -PassThru
    if ($MaintenanceProcess.ExitCode -ne 0) { throw "Kurulum tamamlanamadı (kod $($MaintenanceProcess.ExitCode))." }
    @{ status = 'success'; mode = $MaintenanceJob.mode; time = (Get-Date).ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath $MaintenanceJob.result -Encoding UTF8
    if ($MaintenanceJob.mode -eq 'update' -and $MaintenanceJob.restart -eq $true) {
        Start-Process -FilePath (Join-Path $MaintenanceJob.installRoot 'BerkayAcademicOS.exe') -WorkingDirectory $MaintenanceJob.installRoot
    }
} catch {
    $MaintenanceError = $_.Exception.Message
    if ($MaintenanceJob) {
        @{ status = 'failed'; mode = $MaintenanceJob.mode; message = $MaintenanceError; time = (Get-Date).ToString('o') } |
            ConvertTo-Json | Set-Content -LiteralPath $MaintenanceJob.result -Encoding UTF8
    }
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($MaintenanceError, 'Academic OS güncelleme / kaldırma') | Out-Null
    exit 1
}
