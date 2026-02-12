# Spécifications du Systéme de Scoring SCI v4.5

Automatisation de I'Upgrade et Verrous de Conformité

Direction Stratégique & Technique FAN-Capital

Février 2026

# L'Algorithme de Scoring SCI v4.5

Le Score de Confiance Institutionnelle (SCI) est le moteur décisionnel de la plateforme. Il est calculé par le backend Spring Boot et les preuves d'état sont injectées sur Hyperledger Besu tous les 10 000 blocs.

# Formule du Score

$$
S C I = (A U M _ {9 0 j} \times 0. 5) + (K Y C _ {d e p t h} \times 0. 2) + (B e h a v i o r \times 0. 1 5) + (R i s k \times 0. 1 5)
$$

- AUM (5o%): Volume moyen des actifs. Favorise le réinvestissement via le Credit Wallet.   
- KYC Depth (20%): Atribue des points fixes selon le niveau de validation (10 pts pour le Niveau 1, 20 pts pour le Niveau 2).   
- Behavior (15%): Valorise la récurrence transactionnelle (Bonus de fidélité).   
- Risk(15%) : Surveillance du ratio d'endettement lié a 'Avance sur Titres (AST).

# Matrice des Tiers et Avantages

L'acces aux services financiers est strictement segmenté par le score obtenu.

Table 1: Segmentation des Tiers et Conditions de Passage v4.5   
![](images/1addacd3a40b1bea2a5a6728562cbed4521fd8f87512be0656d4b33ca56a4e0e.jpg)

# Processus d'Upgrade et "Nudge" IA

Le passage d'un niveau a l'autre est assisté par un moteur d'IA intégré a l'interface Angular :

1. Analyse prédictive : LIA identifie un utilisateur proche d'un seuil (ex: Score 34/35).   
2. Notification contextuelle : Incitation a fournir le document manquant (KYC 2) pour ré- duire les taux d'intérét.   
3. Validation HSM : Apres dépót du document, la clé ONBOARDING du HSM signe la transaction de mise a jour du statut sur la blockchain.

Pour garantir la conformité LBA/FT devant le régulateur, le niveau effectif d'un utilisateur est régi par la loi du minimum :

$$
\operatorname {L e v e l} _ {\text {E f f e c t i v e}} = \min  \left(\operatorname {L e v e l} _ {\text {S c o r e}}, \operatorname {L e v e l} _ {\text {K Y C}}\right) \tag {1}
$$

Exemple : Un utilisateur avec un score Platinum (60) mais n'ayant validé que le KYC Niveau 1(CIN) sera techniquement restreint au Tier Bronze jusqu’a la validation de son justificatif de domicile.

# Modéle Freemium et Engagement

Le modele Freemium v4.1 s'nterface avec le SCI de la maniere suivante :

- Frais d'ouverture : Le paiement initial déclenche le passage au Tier Bronze.   
- Abonnement Premium : Indispensable pour débloquer les services Platinum/Diamond, quel que soit le score SCI.
