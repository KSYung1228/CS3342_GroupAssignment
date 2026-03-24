@echo off
setlocal
set MAVEN_WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar
if "%JAVA_HOME%"=="" (
    set JAVA_CMD=java
) else (
    set JAVA_CMD=%JAVA_HOME%\bin\java
)
%JAVA_CMD% -jar %MAVEN_WRAPPER_JAR% %*
