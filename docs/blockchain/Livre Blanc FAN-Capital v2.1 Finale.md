# Rapport Technique & Livre Blanc Architecture de Gestion en Circuit Fermé

Infrastructure de Tokenisation d'Actifs Réels (CPEF)

Conformité Réglementaire CMF & Audit Immuable

FAN-CAPITAL

Département de l'Ingénierie Financière & Conformité

Date: Février 2026

Statut : Document de Conformité Finale

Cible: Conseil du Marché Financier (CMF) / BVMT

# 1 Souveraineté et Architecture en Circuit Fermé

La plateforme FAN-Capital est conçue comme un écosystème financier clos. L'objet est déliminer tout risque de fuite de capitaux et d'assurer une tracabilité totale des flux conformément à la loi anti-blanchiment tunisienne.

L'accès à l'infrastructure est exclusivement généré par une interface Web-Native, supprimant l'utilisation de portefeuilles externes non contrôleurs (MetaMask, Ledger) pour les investisseurs de détaill.

# 2 Onboarding KYC et Gestion des Portefeuilles

Le Wallet-as-a-Service (WaaS) de FAN-Capital est le moteur de la simplicité et de la sécurité.

# 2.1 Déclenchement Conditionnel (Une Identité = Un Wallet)

Conformément au principe de précaution, la génération d'une adresse blockchain n'est pas automatique à l'inscription.

- Niveau KYC 1: Validation des documents d'identité (CIN ou Passreport). L'approbation humaine ou automatisée déclenché la création du Native Wallet via le backend Spring Boot.   
- Niveau KYC 2 : Validation du justificatif de domicile et origine des fonds. Ce niveau est requis pour les souscriptions excédant les seuils réglementaires.

Chaque portefeuille est chiffré via l'algorithmé AES-GCM et stocké dans une zone de données isolée, garantissant que l'investisseur n'a aucune "Seed Phrase" à gérer.

# 3 Mécanisme de Création des Jetons (Minting)

L'émission des titres Atlas et Didon est une opération miroir de l'acquisition d'actifs réels.

# 3.1 Équilibre Actif/Passif

Le nombre de jetsons à émettre (Mint) est strictement corrélé aux avis d'opérés fournis par l'intermédiaire en bourse :

$$
N _ {\text {t o k e n s}} = \frac {V _ {\text {p o r t e f e u i l l e}}}{P _ {\text {t o k e n}}} \tag {1}
$$

Toute opération de Minting est enregistrée en corrélation avec une preuve de virement bancaire ou une confirmation d'achat de sous-jacents.

# 4 Registre d'Audit Immuable & Preuve de Confiance

Pour satisfaire aux exigences d'inspection du CMF, FAN-Capital intégre des mécanismes de vérification avances.

# 4.1 Optimisation par Checkpoints d'Audit

Afin de garantir des performances optimales lors des inspections réglementaires, le service AuditProofService génére des Checkpoints tous les 10 000 blocs.

- La preuve mathématique est calculée de manière incrémentale à partir du dernier Snap-shot de solde validé.   
- Cette méthode permet au régulateur de vérifier l'intégrité des données sans devoir recal-culer l'historique complet depuis le bloc zéro.

# 4.2 Non-Répudiation et BusinessContextId

Toute transaction sur le registre partagé est signée par la Clé de Service de la plateforme (immuable et auditée).

- Chaque transaction contient un BusinessContextId.   
- Cet identifient permet de remonter directement de l'adresse blockchain jusqu'à la piece comptable archivée dans la base de données MariaDB de la plateforme.

# 5 Persistence et Intégrité Technique

Le registre off-chain, gérant la hash-chain d'audit et les métadonnées KYC, repose sur une base de données MariaDB. Ce choix garantit la persistance à long terme et la conformité avec les exigences de conservation des logs d'audit sur plusieurs années, contrairement aux solutions de stockage volatiles.

# 6 Conclusion

L'architecture de FAN-Capital v2.1 réconcilie l'innovation DLT avec les réalisés institutionnelles tunisiennes. En automatisant la preuve mathématique et en subordonnant la technologie à l'identité juridique (KYC), nous offrons un cadre de confiance absolue pour la tokenisation de l'économie réelle.
