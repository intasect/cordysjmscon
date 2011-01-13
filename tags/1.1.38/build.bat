@echo off

REM =====================================================================
REM RTF Build script
REM =====================================================================
REM Launches ant with the proper build file and proper classpath
REM Pass specific targets as arguments to this build file if needed
REM =====================================================================

setlocal

set RETVAL=1

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
if not exist "%JAVA_HOME%\lib\tools.jar" (
	echo Warning: JAVA_HOME environment variable does not point to a JDK folder. You are not able to compile Java classes.
)

if defined BF_SVN_VERSION (
	set BF_SVN_LIB_DIR=svn-%BF_SVN_VERSION%
) else (
	set BF_SVN_LIB_DIR=svn
)
set BF_SVN_HOME=%BF_SDK_HOME%\lib\%BF_SVN_LIB_DIR%

if exist "%BF_PLATFORM_HOME%\bin" (
    set PATH=%BF_PLATFORM_HOME%\bin
) else (
    set PATH=
)
set PATH=%PATH%;%BF_SVN_HOME%
set CLASSPATH=

set ANT_CP=%BF_SDK_HOME%\lib\ant\ant-launcher.jar
set ANT_CP=%ANT_CP%;%JAVA_HOME%\lib\tools.jar

set LIBS=%BF_SDK_HOME%\lib\ant
set LIBS=%LIBS%;%BF_SVN_HOME%
set LIBS=%LIBS%;%BF_SDK_HOME%\lib
set LIBS=%LIBS%;%BF_SDK_HOME%\lib\commons
set LIBS=%LIBS%;%BF_SDK_HOME%\lib\libs-coboc2
set LIBS=%LIBS%;%BF_PLATFORM_HOME%\int
set LIBS=%LIBS%;%BF_PLATFORM_HOME%\ext
set LIBS=%LIBS%;%BF_PLATFORM_HOME%\orc

"%JAVA_HOME%\bin\java.exe" -Xmx512M -Dfile.encoding=UTF-8 "-Dsdk.real.dir=%BF_SDK_HOME%" "-Dplatform.real.dir=%BF_PLATFORM_HOME%" "-Dsvn.lib.home=%BF_SVN_LIB_DIR%" -cp "%ANT_CP%" org.apache.tools.ant.launch.Launcher -lib "%LIBS%" %*
set RETVAL=%ERRORLEVEL%

goto :end

:NoJavaFound
echo JAVA_HOME environment variable is not set. Set it pointing to the Java installation root directory.
goto :end

:end
rem Clean up the BF_HOME vars.
SET BF_SDK_HOME=
SET BF_PLATFORM_HOME=

endlocal & set SCRIPT_RETVAL=%RETVAL%
exit /B %SCRIPT_RETVAL%
