**LIVRE BLANC TECHNIQUE**

Architecture de Gestion Intégrée en Circuit Fermé

Résumé Exécutif![](3201304f-1f95-4f52-b350-2ccc01037808_00000.001.png)

Ce document détaille l’infrastructure souveraine de FAN-Capital, conçue pour la toke- nisation d’actifs boursiers en Tunisie. L’architecture repose sur un modèle *Closed-Loop*, une abstraction totale de la blockchain pour l’utilisateur, et une segmentation cryptogra-

phique stricte des privilèges.

**Direction Technique - FAN-Capital**

Février 2026 | Tunis, Tunisie

*FAN-Capital : Architecture Technique & Sécurité* v3.0 — Février 2026![](3201304f-1f95-4f52-b350-2ccc01037808_00000.002.png)

1  **Introduction et Philosophie du Système**

Pour garantir une conformité stricte avec le Conseil du Marché Financier (CMF), FAN-Capital a éliminéladépendanceauxportefeuillestiers(MetaMask,Ledger)auprofitd’unegestioninterne souveraine. Ce choix technique permet de maintenir les capitaux au sein d’un périmètre KYC vérifié, empêchant toute fuite d’actifs vers des adresses non identifiées.

2  **Gestion des Portefeuilles (WaaS)**

La plateforme intègre un service de **Wallet-as-a-Service (WaaS)** géré par le Backend Spring Boot.

- **Création Native :** Chaque utilisateur reçoit une adresse blockchain lors de la validation de son KYC.
- **Sécurité des Clés :** Les clés privées des utilisateurs sont chiffrées en **AES-256-GCM**et sto- ckées dans un module de sécurité isolé.
- **Abstraction UX :** L’utilisateur interagit via une interface web classique (Web2) ; les signa- tures transactionnelles sont gérées programmatiquement en arrière-plan.
3  **Mécanisme de Création (Minting) et Équilibre Actif/Passif**

L’émission des jetons (*Atlas* et *Didon*) est strictement corrélée aux actifs réels acquis en bourse.

1. **Modélisation Mathématique**

Lenombredejetonsàémettre(Ntokens )estcalculéselonlavaleurnetteduportefeuille(Vportefeuille) et la Valeur Nominale ou Liquidative (V NI) :

N = Vportefeuille tokens V NI

2. **Flux de Distribution**
   1. **Réception :** Réception des dinars (TND) sur le compte bancaire de FAN-Capital.
   1. **Cashing :** Émission de jetons de trésorerie interne (Cash Tokens) sur le compte utilisateur.
   1. **Swap :** Échange atomique entre le *Stock Wallet* et l’utilisateur via une pool de liquidité contrôlée.
4  **Matrice de Sécurité Multi-Clés (Architecture à 7 Clés)**

Pour isoler les risques, les permissions du Smart Contract sont fragmentées entre sept rôles distincts.

Page 2 sur 2
*FAN-Capital : Architecture Technique & Sécurité* v3.0 — Février 2026![](3201304f-1f95-4f52-b350-2ccc01037808_00000.002.png)



|**Clé / Rôle**||**Fonctionnalité Spécifique**|**Ty**|**pede Stockage**|
| - | :- | - | - | - |
|**Governance**||Modification des paramètres vitaux|Mul|ti-sig3/5|
|**Mint Key**||Autorise la création de nouveaux titres|HS|MIsolé|
|**Burn Key**||Détruit les jetons lors des rachats|HS|MIsolé|
|**Oracle Key**||Met à jour les cours en temps réel (BVMT)|AP|IBackend|
|**Compliance**||Gestion du Whitelisting (KYC LBA/FT)||Database/ Auth|
|**Panic Key**||**Arrêt immédiat de toutes les transactions**||Cold Storage|
|**Audit Key**||Accès aux registres chiffrés pour régulateur|Read-|OnlyKey|

Table 1: Segmentation des privilèges cryptographiques

5  **Sécurité Opérationnelle et Plans de Secours**
1. **Le ”Bouton Panique” (Circuit Breaker)**

En cas d’intrusion ou de faille de sécurité détectée, la **Panic Key** permet d’invoquer la fonction pause() du contrat. Cela fige instantanément tous les soldes, empêchant tout mouvement de fonds pendant que l’équipe technique procède à l’audit.

2. **Preuve de Réserve (Proof of Reserve)**

Le système permet une réconciliation en temps réel entre :

- Le solde des jetons sur la blockchain.
- Les relevés de positions fournis par l’intermédiaire en bourse.
6  **Conclusion**

Cette architecture garantit que FAN-Capital demeure le seul tiers de confiance responsable de- vant le régulateur. Elle offre une sécurité maximale en empêchant toute fuite de capitaux hors du système KYC tout en assurant une transparence totale via le registre immuable de la block- chain.
Page 2 sur 2
