@echo off
rem Recompila todo y genera EstudioActivo.jar
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac -encoding UTF-8 -d out src\estudio\*.java || (pause & exit /b 1)
jar cfe EstudioActivo.jar estudio.App -C out . || (pause & exit /b 1)
echo Listo: EstudioActivo.jar
