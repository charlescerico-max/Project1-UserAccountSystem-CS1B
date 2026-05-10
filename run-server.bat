@echo off
set "MYSQL_JAR=src\main\java\mysql-connector.jar"
echo Compiling Application.java...
javac -cp "%MYSQL_JAR%" src\main\java\Application.java
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)
echo Starting server...
java -cp "%MYSQL_JAR%;c:\xampp\htdocs\Project1-UserAccountSystem-CS1B\src\main\java" Application
pause