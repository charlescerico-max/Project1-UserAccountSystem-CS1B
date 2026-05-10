Open Command Prompt or PowerShell
Navigate to the project: cd c:\xampp\htdocs\Project1-UserAccountSystem-CS1B
Compile: javac -cp src\main\java\mysql-connector.jar src\main\java\Application.java
Run: java -cp "src\main\java\mysql-connector.jar;src\main\java" Application

If error
taskkill /PID 10656 /F


mvn exec:java -Dexec.mainClass="Application"




What you should do
Compile (after any Java changes):
mvn compile

Start the API (pick one):

Double‑click run-api.bat, or
In the project folder: mvn exec:java
Keep that process running and use the site from Live Server (e.g. http://127.0.0.1:5500/security.html).

If you see Address already in use, something else is already on port 8080 (often an old java). Close that console or run:

netstat -ano | findstr ":8080"
taskkill /PID <pid> /F
Then start run-api.bat again.