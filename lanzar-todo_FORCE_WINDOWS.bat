@echo off
setlocal ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

rem ====== FIJAR CARPETA DEL SCRIPT ======
cd /d "%~dp0"

rem (Opcional) Forzar UTF-8 para acentos en la consola
chcp 65001 >nul

rem ====== RUTA AL .PS1 QUE VAMOS A INVOCAR ======
set "PS1=run-invernadero_FORCE_WINDOWS.ps1"

echo =============================================
echo     SISTEMA DE INVERNADERO DISTRIBUIDO 2025
echo =============================================
echo.
echo Iniciando ejecucion completa...
echo (1) Servidor de Exclusion Mutua
echo (2) Controlador
echo (3) Sensores Globales
echo (4) Sensores de Humedad
echo (5) Electrovalvulas
echo =============================================
echo.

if not exist "%PS1%" (
  echo [ERROR] No se encuentra el script PowerShell:
  echo         "%CD%\%PS1%"
  echo Asegurate de guardar este BAT y el PS1 en la MISMA carpeta.
  echo.
  goto :END_FAIL
)

set "PSEXE="
where powershell >nul 2>&1 && set "PSEXE=powershell"
if not defined PSEXE where pwsh >nul 2>&1 && set "PSEXE=pwsh"

if not defined PSEXE (
  echo [ERROR] No se encontro PowerShell ni pwsh en PATH.
  echo Instala PowerShell o agrega su ruta al PATH del sistema.
  echo.
  goto :END_FAIL
)

"%PSEXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%CD%\%PS1%"
if errorlevel 1 (
  echo.
  echo =============================================
  echo Hubo un ERROR durante la ejecucion de PowerShell.
  echo Revisar mensajes arriba.
  echo =============================================
  echo.
  goto :END_FAIL
)

echo.
echo =============================================
echo Todo el sistema ha sido lanzado correctamente.
echo Cierre las ventanas para detenerlo.
echo =============================================
echo.
goto :END_OK

:END_FAIL
echo Presione una tecla para continuar . . .
pause >nul
exit /b 1

:END_OK
echo Presione una tecla para salir . . .
pause >nul
exit /b 0
