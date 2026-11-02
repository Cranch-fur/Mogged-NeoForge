@echo off
rem Builds the mod for every Minecraft version in versions\ (one jar each, all in build\libs\).
rem A failed version doesn't stop the others; the failed ones are listed at the end.
rem Extra arguments go to every Gradle run, e.g.: build-all.bat --no-configuration-cache
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "FAILED="
for %%f in (versions\*.properties) do (
    echo.
    echo ===== Minecraft %%~nf =====
    call gradlew.bat build -Pmc=%%~nf %*
    if errorlevel 1 (
        echo Build for %%~nf failed.
        set "FAILED=!FAILED! %%~nf"
    )
)
echo.
if defined FAILED (
    echo Failed versions:!FAILED!
    echo Jars of the other versions: %~dp0build\libs
    exit /b 1
)
echo All versions built: %~dp0build\libs
