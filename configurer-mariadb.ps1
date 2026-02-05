# Script de configuration pour utiliser MariaDB au lieu de H2
# Exécutez ce script AVANT de démarrer le backend

Write-Host "=== Configuration MariaDB pour FAN-Capital ===" -ForegroundColor Cyan
Write-Host ""

# Demander le mot de passe root MariaDB
$password = Read-Host "Entrez le mot de passe root de MariaDB" -AsSecureString
$passwordPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
)

# Configurer les variables d'environnement
Write-Host "`nConfiguration des variables d'environnement..." -ForegroundColor Yellow

$env:DB_URL = "jdbc:mariadb://127.0.0.1:3306/fancapital"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = $passwordPlain
$env:SPRING_PROFILES_ACTIVE = "mariadb"

Write-Host "✓ DB_URL: $env:DB_URL" -ForegroundColor Green
Write-Host "✓ DB_USERNAME: $env:DB_USERNAME" -ForegroundColor Green
Write-Host "✓ SPRING_PROFILES_ACTIVE: $env:SPRING_PROFILES_ACTIVE" -ForegroundColor Green
Write-Host ""

# Vérifier que la base existe
Write-Host "Vérification de la base de données..." -ForegroundColor Yellow
try {
    # Essayer de se connecter (nécessite mysql dans le PATH)
    Write-Host "⚠ Note: Assurez-vous que la base 'fancapital' existe dans phpMyAdmin" -ForegroundColor Yellow
    Write-Host "   Si ce n'est pas le cas, créez-la avec le script SQL fourni" -ForegroundColor Yellow
} catch {
    Write-Host "⚠ Impossible de vérifier automatiquement" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Configuration terminée ===" -ForegroundColor Green
Write-Host ""
Write-Host "Pour démarrer le backend avec MariaDB:" -ForegroundColor Cyan
Write-Host "  cd backend" -ForegroundColor White
Write-Host "  .\mvnw.cmd spring-boot:run" -ForegroundColor White
Write-Host ""
Write-Host "Ou dans cette session PowerShell:" -ForegroundColor Cyan
Write-Host "  cd backend; .\mvnw.cmd spring-boot:run" -ForegroundColor White
Write-Host ""
