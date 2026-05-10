$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$mysqlJar = "src/main/java/mysql-connector.jar"
$javaFile = "src/main/java/Application.java"
$classpath = "$mysqlJar;$projectRoot\src\main\java"

$serverProcess = $null

function Stop-Server {
    if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
        Write-Host "Stopping server PID $($serverProcess.Id)..."
        Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue
    }
    $script:serverProcess = $null
}

function Start-Server {
    Write-Host "Compiling Application.java..."
    & javac -cp $mysqlJar $javaFile
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Compilation failed. Waiting for next change..."
        return
    }

    Stop-Server
    Write-Host "Starting server..."
    $script:serverProcess = Start-Process -FilePath "java" -ArgumentList @("-cp", $classpath, "Application") -PassThru -NoNewWindow
    Start-Sleep -Milliseconds 500
    if (-not $serverProcess.HasExited) {
        Write-Host "Server running. PID: $($serverProcess.Id)"
    } else {
        Write-Host "Server exited immediately. Check your code/output."
    }
}

Write-Host "Java watch mode started."
Write-Host "Watching: $javaFile"
Write-Host "Press Ctrl+C to stop."

Start-Server

$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = Join-Path $projectRoot "src/main/java"
$watcher.Filter = "Application.java"
$watcher.IncludeSubdirectories = $false
$watcher.NotifyFilter = [System.IO.NotifyFilters]'LastWrite, FileName, Size'
$watcher.EnableRaisingEvents = $true

$onChange = {
    try {
        Start-Sleep -Milliseconds 200
        Write-Host ""
        Write-Host "Change detected. Rebuilding..."
        Start-Server
    } catch {
        Write-Host "Watcher error: $($_.Exception.Message)"
    }
}

$createdSub = Register-ObjectEvent $watcher Created -Action $onChange
$changedSub = Register-ObjectEvent $watcher Changed -Action $onChange
$renamedSub = Register-ObjectEvent $watcher Renamed -Action $onChange

try {
    while ($true) {
        Wait-Event -Timeout 1 | Out-Null
    }
} finally {
    Unregister-Event -SourceIdentifier $createdSub.Name -ErrorAction SilentlyContinue
    Unregister-Event -SourceIdentifier $changedSub.Name -ErrorAction SilentlyContinue
    Unregister-Event -SourceIdentifier $renamedSub.Name -ErrorAction SilentlyContinue
    $watcher.Dispose()
    Stop-Server
}
