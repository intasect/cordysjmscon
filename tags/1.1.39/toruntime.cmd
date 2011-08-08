@echo off
echo Batch-file publishing the content to the Cordys runtime environment.
if "%1"=="/?" goto displayUsage

if exist "%CD%\set-environment-vars.cmd" (
	call set-environment-vars.cmd
)

REM Check to make sure the env var BF_SDK_HOME and BF_PLATFORM_HOME are set.

if not defined BF_SDK_HOME goto :defineBF_SDK_HOME

:checkBF_PLATFORM_HOME

if not defined BF_PLATFORM_HOME goto :defineBF_PLATFORM_HOME
goto :doActualAction

:defineBF_SDK_HOME
REM Set the the BF_SDK_HOME
SET BF_SDK_HOME=%CD%\sdk
goto :checkBF_PLATFORM_HOME

:defineBF_PLATFORM_HOME
REM Set the the BF_PLATFORM_HOME
SET BF_PLATFORM_HOME=%CD%\platform
goto :doActualAction

:doActualAction

if not defined JAVA_HOME goto :NoJavaFound

REM Set the command line defaults
set BUILD_CMD=build.bat
set BUILD_PREFIX=toruntime
set CLASSPATH=

"%JAVA_HOME%\bin\java.exe" -cp "%BF_SDK_HOME%\lib\buildtasks.jar" com.cordys.coe.ant.bf.AntLauncher "%BUILD_CMD%" %BUILD_PREFIX% %*
goto endLocal

:NoJavaFound
echo JAVA_HOME environment variable is not set. Set it pointing to the Java installation root directory.
goto endLocal

:displayUsage

echo Usage: toruntime [contenttype] [-antoptions]
echo Types of content: 
echo     all
echo     flows
echo     xforms

:endLocal
