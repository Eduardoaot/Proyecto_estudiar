@echo off
rem Pruebas del módulo de problemas: compila, ejecuta las pruebas en Java y compara contra generadores.js (si hay Node).
cd /d "%~dp0"
if exist out-pruebas rmdir /s /q out-pruebas
mkdir out-pruebas
javac -encoding UTF-8 -d out-pruebas src\estudio\*.java pruebas\estudio\Pruebas.java || (pause & exit /b 1)
java -cp out-pruebas estudio.Pruebas volcar out-pruebas\volcado.jsonl || (pause & exit /b 1)
where node >nul 2>nul && node referencia\comparar.mjs out-pruebas\volcado.jsonl
rmdir /s /q out-pruebas
pause
