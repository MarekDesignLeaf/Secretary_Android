@echo off
set GIT="C:\Program Files\Git\bin\git.exe"
cd /d C:\Users\hutra\AndroidStudioProjects\secretary\server
%GIT% push origin main > C:\Users\hutra\AndroidStudioProjects\secretary\push2.txt 2>&1
if %ERRORLEVEL% equ 0 (echo PUSH_OK >> C:\Users\hutra\AndroidStudioProjects\secretary\push2.txt) else (echo PUSH_FAIL >> C:\Users\hutra\AndroidStudioProjects\secretary\push2.txt)
