@ECHO OFF
SETLOCAL
SET APP_HOME=%~dp0
SET DIST_ROOT=%APP_HOME%.gradle-dist
SET INSTALL_DIR=%DIST_ROOT%\gradle-7.6.4
IF NOT EXIST "%INSTALL_DIR%\bin\gradle.bat" (
  ECHO Please run this project with ./gradlew on a Unix-like shell to bootstrap Gradle.
  EXIT /B 1
)
CALL "%INSTALL_DIR%\bin\gradle.bat" --no-daemon %*
