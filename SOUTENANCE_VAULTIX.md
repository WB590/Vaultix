# Soutenance Vaultix — Script Prêt à Présenter

Durée cible: 10 à 15 minutes

---

## Slide 1 — Titre

**Vaultix — Secure Your Digital Life**

### À dire
Bonjour, nous présentons Vaultix, une application Android de gestion sécurisée de mots de passe.
L’objectif est de proposer une solution simple à utiliser, avec chiffrement côté client et authentification Firebase.

---

## Slide 2 — Problématique

### Contenu slide
- Trop de comptes à gérer
- Risque de réutilisation de mots de passe
- Besoin de centralisation sécurisée

### À dire
Le problème principal est la mauvaise gestion des mots de passe au quotidien. Beaucoup d’utilisateurs réutilisent des mots de passe faibles.
Nous avons donc développé Vaultix pour stocker les identifiants de façon chiffrée avec une couche Master Password.

---

## Slide 3 — Objectifs du mini-projet

### Contenu slide
- Authentification utilisateur
- Master Password à chaque session
- Chiffrement AES-GCM
- Stockage Firestore sécurisé
- UI simple avec Drawer + FAB

### À dire
Nos objectifs étaient techniques et UX: sécurité forte, architecture claire, et interface simple.
Chaque session impose un Master Password avant accès aux données.

---

## Slide 4 — Architecture générale

### Contenu slide
- Frontend Android: Kotlin + Fragments + XML
- Backend: Firebase Auth + Firestore
- Couche sécurité: PBKDF2 + AES-GCM
- Host: `MainActivity`

### À dire
Nous avons utilisé une architecture Single-Activity avec plusieurs Fragments.
`MainActivity` gère la navigation globale, et `FirebaseRepository` centralise les appels backend.

---

## Slide 5 — Flux utilisateur complet

### Contenu slide
1. Welcome/Loading
2. Login/Register (email ou Google)
3. Master Password
4. Home (liste)
5. Add password (+)
6. Drawer / Logout

### À dire
L’utilisateur arrive d’abord sur un écran d’accueil, puis selon l’état de session il est redirigé vers login ou master.
Une fois déverrouillé, il accède à sa liste de mots de passe et peut en ajouter.

---

## Slide 6 — Sécurité implémentée

### Contenu slide
- Salt unique par utilisateur
- `PBKDF2WithHmacSHA256` (120 000 itérations)
- Clé AES 256 bits
- `AES/GCM/NoPadding`
- Aucune donnée sensible en clair

### À dire
Le Master Password n’est jamais stocké.
On dérive une clé via PBKDF2 avec un salt utilisateur, puis on chiffre en AES-GCM.
Même en cas de fuite DB, les mots de passe restent illisibles sans clé.

---

## Slide 7 — Validation du Master Password (point fort)

### Contenu slide
- Verifier chiffré stocké: `masterVerifierCipher` + `masterVerifierIv`
- Si mauvais Master Password -> déchiffrement invalide -> accès refusé
- 1 Master Password effectif par utilisateur

### À dire
Pour garantir la cohérence du master password, nous avons ajouté un verifier chiffré.
Au premier déverrouillage, le verifier est créé; aux sessions suivantes, il doit être déchiffré correctement.

---

## Slide 8 — Base de données Firestore

### Contenu slide
- `users/{uid}`
  - `salt`
  - `masterVerifierCipher`
  - `masterVerifierIv`
- `users/{uid}/passwords/{docId}`
  - `site`
  - `password` (chiffré)
  - `iv`

### À dire
Les données sont isolées par UID.
Chaque entrée contient le site, le mot de passe chiffré et son IV.
Le déchiffrement se fait uniquement côté client après unlock.

---

## Slide 9 — Fonctionnalités UI réalisées

### Contenu slide
- Welcome/Loading avec logo
- Login/Register + Google Sign-In
- Home avec liste + Show/Hide
- Copy to clipboard
- Génération password fort (A-Za-z0-9)
- Drawer (Profile, Settings, Logout, Other)

### À dire
Nous avons privilégié une interface claire avec actions directes.
Des ajouts pratiques ont été intégrés: générateur de mot de passe et copie presse-papiers.

---

## Slide 10 — Travail en binôme

### Contenu slide
- Binôme 1: Backend + Sécurité
- Binôme 2: UI + Navigation
- Revue croisée et intégration

### À dire
La répartition des tâches a accéléré le développement et réduit les conflits.
Nous avons organisé le travail par modules avec validation croisée.

---

## Slide 11 — Difficultés rencontrées et solutions

### Contenu slide
- `CONFIGURATION_NOT_FOUND` -> Auth Firebase non initialisée
- `PERMISSION_DENIED` -> règles Firestore
- Google login sans finalisation -> SHA-1 / OAuth / `google-services.json`

### À dire
Nous avons eu plusieurs problèmes d’intégration Firebase, résolus en configurant correctement Auth, règles Firestore et empreintes SHA.

---

## Slide 12 — Démonstration live (script)

### Étapes à montrer
1. Ouvrir app -> Welcome
2. Login (email ou Google)
3. Entrer Master Password
4. Afficher Home
5. Ajouter entrée avec password généré
6. Show/Hide + Copy
7. Logout via Drawer

### À dire
Ici on voit le flow complet sécurisé, du login jusqu’au stockage chiffré et à la récupération.

---

## Slide 13 — Bilan et limites

### Contenu slide
Points forts:
- Sécurité client-side réelle
- Architecture propre et lisible
- UX fluide

Limites actuelles:
- Pas de biométrie
- Pas d’édition/suppression complète
- Pas de cache offline avancé

### À dire
Le MVP est fonctionnel et sécurisé pour un projet académique.
Des améliorations sont possibles pour une version production.

---

## Slide 14 — Perspectives

### Contenu slide
- Biometric unlock
- Générateur avancé (symboles, longueur custom)
- Audit et monitoring
- Android Keystore avancé

### À dire
La prochaine version visera plus de confort et une sécurité matérielle renforcée.

---

## Questions fréquentes (préparation jury)

### Q1: Pourquoi AES-GCM ?
Réponse: parce qu’il fournit chiffrement + intégrité (détecte modification des données).

### Q2: Pourquoi PBKDF2 ?
Réponse: pour dériver une clé forte depuis un mot de passe utilisateur avec coût calculatoire élevé.

### Q3: Le Master Password est-il stocké ?
Réponse: non, jamais. On stocke un verifier chiffré, pas le secret.

### Q4: Comment gérez-vous plusieurs utilisateurs ?
Réponse: isolation stricte par `uid` dans Firestore (`users/{uid}`).

---

## Script de clôture (30 secondes)

Vaultix répond au besoin concret de gestion sécurisée des mots de passe avec une architecture Android moderne et une sécurité côté client.
Le projet nous a permis d’appliquer Firebase, navigation par fragments, cryptographie et gestion de session de manière cohérente.
C’est une base solide pour évoluer vers une solution plus complète en production.
