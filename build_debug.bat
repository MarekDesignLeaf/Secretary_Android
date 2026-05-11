@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat assembleDebug > build_out3.txt 2>&1
if %ERRORLEVEL% equ 0 (
    echo BUILD_SUCCESS
) else (
    echo BUILD_FAILED
)
