param(
    [Parameter(Mandatory)][string]$Title,
    [Parameter(Mandatory)][string]$WorkDir,
    [Parameter(Mandatory)][string]$Command,
    [string[]]$Args = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path $WorkDir)) {
    throw "[run_one_SAFEOUT] No existe WorkDir: $WorkDir"
}

# Resolver Maven si es necesario
if ($Command -ieq 'mvn' -or $Command -ieq 'mvn.cmd') {
    $mvnCmd = (Get-Command mvn -ErrorAction SilentlyContinue)?.Source
    if (-not $mvnCmd) { $mvnCmd = (Get-Command mvn.cmd -ErrorAction SilentlyContinue)?.Source }
    if ($mvnCmd) { $Command = $mvnCmd }
}

# Armar línea para cmd.exe /k (deja ventana abierta)
$cmdLine = '"' + $Command + '" ' + ($Args -join ' ')

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = 'cmd.exe'
$psi.Arguments = "/k title $Title && cd /d `"$WorkDir`" && echo Ejecutando: $cmdLine && $cmdLine"
$psi.WorkingDirectory = $WorkDir
$psi.UseShellExecute = $true
$psi.WindowStyle = 'Normal'

[System.Diagnostics.Process]::Start($psi) | Out-Null
