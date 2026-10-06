@echo off
rem Build and start the server (TCP :23363 + web http://localhost:8080/).
rem   run.bat [server options]   e.g. run.bat --host 0.0.0.0
rem   run.bat test               run the self-test
cd /d "%~dp0"
if exist server\out rmdir /s /q server\out
javac -encoding UTF-8 -d server\out server\src\utrs\*.java server\test\utrs\*.java || exit /b 1
if "%~1"=="test" (
  java -cp server\out utrs.SelfTest
) else (
  java -cp server\out utrs.App %*
)
