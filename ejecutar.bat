@echo off
rem Compila (si hace falta) y abre Estudio Activo.
cd /d "%~dp0"
if not exist out\estudio\App.class (
    echo Compilando...
    if not exist out mkdir out
    javac -encoding UTF-8 -d out src\estudio\*.java
    if errorlevel 1 (
        echo Error al compilar. Se necesita Java 17 o superior.
        pause
        exit /b 1
    )
)
start "" javaw -cp out estudio.App
