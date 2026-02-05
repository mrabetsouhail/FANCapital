# Script de démarrage pour FAN-Capital
# Lance les trois services : Blockchain, Backend et Frontend

Write-Host "=== Démarrage FAN-Capital ===" -ForegroundColor Cyan
Write-Host ""

# 1. Blockchain (Hardhat Node)
Write-Host "[1/3] Démarrage de la blockchain (Hardhat)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd blockchain; npm run node" -WindowStyle Minimized

Start-Sleep -Seconds 2

# 2. Backend (Spring Boot)
Write-Host "[2/3] Démarrage du backend (Spring Boot)..." -ForegroundColor Yellow
if (Test-Path "backend\mvnw.cmd") {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd backend; .\mvnw.cmd spring-boot:run" -WindowStyle Minimized
} else {
    Write-Host "  Maven wrapper non trouvé. Utilisez: mvn spring-boot:run" -ForegroundColor Red
}

Start-Sleep -Seconds 2

# 3. Frontend (Angular)
Write-Host "[3/3] Démarrage du frontend (Angular)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd frontend; npm start" -WindowStyle Minimized

Write-Host ""
Write-Host "=== Services démarrés ===" -ForegroundColor Green
Write-Host ""
Write-Host "Blockchain (Hardhat): http://localhost:8545" -ForegroundColor Cyan
Write-Host "Backend (Spring Boot): http://localhost:8081" -ForegroundColor Cyan
Write-Host "Frontend (Angular):    http://localhost:52113" -ForegroundColor Cyan
Write-Host ""
Write-Host "Les fenêtres PowerShell sont ouvertes en arrière-plan." -ForegroundColor Yellow
Write-Host "Attendez quelques secondes que les services démarrent complètement." -ForegroundColor Yellow
