**Dossier de Spécifications Techniques v2.0**

Architecture Smart Contract & Logique Financière CPEF

Écosystème Blockchain FAN-Capital

Département R&D Blockchain

Décembre 2025

1  **Architecture Globale et Standards![](66dd6822-91fa-4dbd-851e-ec305f854d02_00000.001.png)**

Le système repose sur une implémentation avancée du standard **ERC-1404** (Security Token avec restrictions). Le contrat est déployé sur une DLT permissionnée assurant une finalité immédiate (Consensus IBFT 2.0).

2  **Gestion du Double Portefeuille (Internal Balances)![](66dd6822-91fa-4dbd-851e-ec305f854d02_00000.001.png)**

Le contrat gère nativement deux types de soldes pour chaque utilisateur afin de fluidifier les échanges :

- **Token Balance :** Quantité de certificats CPEF détenus (sujets à la VNI).
- **Liquidity Balance (Cash Wallet) :** Solde en TND *Net de fiscalité* stocké dans le contrat, permettant le réinvestissement immédiat ou le retrait bancaire.
3  **Mécanisme de Pricing et Commissions Freemium![](66dd6822-91fa-4dbd-851e-ec305f854d02_00000.001.png)**

Le prix d’exécution est calculé dynamiquement par le contrat de la Piscine de Liquidité.

1. **Équation du Spread Dynamique**

Le prix client *Pclient* est ajusté selon la réserve disponible :

*Pclient* = *Pref* × (1 ± *Spreaddyn*) (1) +*α*(*σ*)+*β*( 1 ). Le spread est réduit pour les utilisateurs des niveaux supérieurs

Où *Spreaddyn* = *Sbase R*

(Silver à Platinum).

2. **Commissions par Niveaux (Mapping)**

Le contrat interroge le niveau de l’utilisateur avant chaque transaction :



|**Niveau d’Utilisateur C**|**ommission Achat Com**|**mission Rachat**|
| - | - | - |
|**BRONZE SILVER GOLD PLATINUM**|<p>1\.00 %</p><p>0\.95 %</p><p>0\.90 %</p><p>0\.80 %</p>|<p>1\.00 %</p><p>0\.95 %</p><p>0\.90 %</p><p>0\.80 %</p>|

4  **Logique de Rachat et Fiscalité Automatisée![](66dd6822-91fa-4dbd-851e-ec305f854d02_00000.002.png)**
1. **Suppression des Pénalités**

Conformément à la stratégie d’inclusion financière, la pénalité de sortie anticipée de 2% (prévue initialement pour les détentions < 180 jours) est **supprimée**. La liquidité est totale dès le jour 1.

2. **Retenue à la Source (RAS) et PRM**

Le contrat agit comme agent fiduciaire :

1. **Calcul du PRM :** Mis à jour à chaque fonction mint() ou transfer().
1. **Moment du prélèvement :** La RAS est déduite lors de la fonction burn().
1. **Base imposable :** *Gain* = (*V NIsortie PRM*) × *Quantit*é.

−

4. **Destination :** La taxe est envoyée vers un Tax\_Vault interne avant reversement au fisc.
5  **Sécurité et Gouvernance![](66dd6822-91fa-4dbd-851e-ec305f854d02_00000.001.png)**
- **Circuit Breaker :** Suspension automatique du rachat via piscine si le ratio de réserve *R <* 20%. Le marché secondaire P2P reste actif.
- **Oracle Guard :** Rejet de toute mise à jour de VNI présentant un écart *>* 10% par rapport à la valeur précédente sans validation Multi-Sig.
- **Plafonds KYC :** Restrictions de solde selon le niveau de validation (Green List vs White List).
2
