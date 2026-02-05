# Script d'arrêt pour FAN-Capital
# Arrête tous les processus Node.js, Java et Angular

Write-Host "=== Arrêt des services FAN-Capital ===" -ForegroundColor Cyan
Write-Host ""

# Arrêter les processus Node.js (Hardhat et Angular)
Write-Host "Arrêt des processus Node.js..." -ForegroundColor Yellow
Get-Process -Name node -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

# Arrêter les processus Java (Spring Boot)
Write-Host "Arrêt des processus Java..." -ForegroundColor Yellow
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*spring-boot*" } | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "=== Services arrêtés ===" -ForegroundColor Green
