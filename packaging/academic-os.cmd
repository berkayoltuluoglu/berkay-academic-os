@echo off
"%~dp0runtime\bin\java.exe" -Dacademic.os.packaged=true -cp "%~dp0app\lib\*" org.example.AgentCli %*
exit /b %errorlevel%
