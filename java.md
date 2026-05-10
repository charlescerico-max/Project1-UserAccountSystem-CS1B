Open Command Prompt or PowerShell
Navigate to the project: cd c:\xampp\htdocs\Project1-UserAccountSystem-CS1B
Compile: javac -cp src\main\java\mysql-connector.jar src\main\java\Application.java
Run: java -cp "src\main\java\mysql-connector.jar;src\main\java" Application

If error
taskkill /PID 10656 /F