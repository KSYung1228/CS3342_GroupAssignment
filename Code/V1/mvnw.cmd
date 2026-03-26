@echo off
setlocal

set "WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar"

if "%JAVA_HOME%"=="" (
    set "JAVA_EXE=java"
) else (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%CD%" org.apache.maven.wrapper.MavenWrapperMain %*
