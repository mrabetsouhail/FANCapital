-- Script de création de la base de données FAN-Capital
-- À exécuter dans phpMyAdmin ou via MySQL/MariaDB CLI

-- Créer la base de données avec encodage UTF-8
CREATE DATABASE IF NOT EXISTS fancapital 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

-- Créer un utilisateur dédié (optionnel, pour la production)
-- CREATE USER IF NOT EXISTS 'fancapital'@'localhost' IDENTIFIED BY 'votre_mot_de_passe_securise';

-- Accorder les privilèges (si vous créez un utilisateur dédié)
-- GRANT ALL PRIVILEGES ON fancapital.* TO 'fancapital'@'localhost';
-- FLUSH PRIVILEGES;

-- Afficher un message de confirmation
SELECT 'Base de données fancapital créée avec succès!' AS message;
