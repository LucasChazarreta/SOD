#Requires -Version 5
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Carpeta donde está este .ps1
$root = Split-Path -Parent $PSCommandPath

# Helper
$helper = Join-Path $root 'run_one_SAFEOUT.ps1'
if (-not (Test-Path $helper)) {
    Write-Error "Falta el helper: $helper"
}

function Start-Step {
    param(
        [Parameter(Mandatory)][string]$Title,
        [Parameter(Mandatory)][string]$WorkDir,
        [Parameter(Mandatory)][string]$Command,
        [string[]]$Args = @()
    )
    if (-not (Test-Path $WorkDir)) {
        throw "[Start-Step] No existe WorkDir: $WorkDir"
    }
    & $helper -Title $Title -WorkDir $WorkDir -Command $Command -Args $Args
}

Write-Host "============================================="
Write-Host "    SISTEMA DE INVERNADERO DISTRIBUIDO 2025"
Write-Host "============================================="
Write-Host ""

# === AJUSTÁ ESTAS RUTAS A TU ESTRUCTURA ===
# (Dejá este .ps1 al lado de las carpetas de cada módulo)
$dirEM   = Join-Path $root 'ServidorExclusionMutua'
$dirCTRL = Join-Path $root 'Controlador'
$dirGLOB = Join-Path $root 'SensoresGlobales'
$dirHUM  = Join-Path $root 'SensoresHumedad'
$dirEV   = Join-Path $root 'Electrovalvulas'

# === MAIN CLASS reales (ajustá a tus paquetes) ===
$mainEM   = 'com.sistdist.em.ServidorCentral'
$mainCTRL = 'com.sistdist.controlador.Controlador'
$mainGLOB = 'com.sistdist.sensoresglobales.Main'
$mainHUM  = 'com.sistdist.sensorhumedad.Main'
$mainEV   = 'com.sistdist.electrovalvula.Main'   # toma arg con el número de EV

# ===== ORDEN DE LANZAMIENTO =====
# 1) Servidor EM
Start-Step -Title 'Servidor EM' -WorkDir $dirEM -Command 'mvn' -Args @(
  '--no-transfer-progress','-q',
  'org.codehaus.mojo:exec-maven-plugin:3.1.0:exec',
  "-Dexec.mainClass=$mainEM"
)

# 2) Controlador
Start-Step -Title 'Controlador' -WorkDir $dirCTRL -Command 'mvn' -Args @(
  '--no-transfer-progress','-q',
  'org.codehaus.mojo:exec-maven-plugin:3.1.0:exec',
  "-Dexec.mainClass=$mainCTRL"
)

# 3) Sensores globales
Start-Step -Title 'Sensores Globales' -WorkDir $dirGLOB -Command 'mvn' -Args @(
  '--no-transfer-progress','-q',
  'org.codehaus.mojo:exec-maven-plugin:3.1.0:exec',
  "-Dexec.mainClass=$mainGLOB"
)

# 4) Sensores de humedad (P1..P5)
for ($i=1; $i -le 5; $i++) {
  Start-Step -Title ("SensorHumedad P$($i)") -WorkDir $dirHUM -Command 'mvn' -Args @(
    '--no-transfer-progress','-q',
    'org.codehaus.mojo:exec-maven-plugin:3.1.0:exec',
    "-Dexec.mainClass=$mainHUM",
    "-Dexec.args=$i"
  )
}

# 5) Electrovalvulas (EV1..EV7, EV7 suele ser Ferti)
for ($ev=1; $ev -le 7; $ev++) {
  $title = if ($ev -eq 7) { 'EV-FERTI (EV7)' } else { "EV$ev" }
  Start-Step -Title $title -WorkDir $dirEV -Command 'mvn' -Args @(
    '--no-transfer-progress','-q',
    'org.codehaus.mojo:exec-maven-plugin:3.1.0:exec',
    "-Dexec.mainClass=$mainEV",
    "-Dexec.args=$ev"
  )
}

Write-Host ""
Write-Host "Todos los procesos fueron lanzados (cada uno en su ventana)."
Write-Host "Cierre cada ventana para detener el proceso correspondiente."
