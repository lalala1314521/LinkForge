@echo off
set GRADLE_USER_HOME=F:\gradle-qa-home
if exist F:\gradle-qa-home\native (
  move F:\gradle-qa-home\native F:\gradle-qa-home\native_old_%RANDOM% >nul 2>&1
)
call F:\Gradle\gradle-9.4.1\bin\gradle.bat %* > qa_out.log 2>&1
echo GRADLE_EXIT=%ERRORLEVEL%
