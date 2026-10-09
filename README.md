# CashBank API

API REST de **portefeuille électronique** : création de compte, dépôts, retraits, transferts entre utilisateurs,
bénéficiaires, historique, plafonds, notifications asynchrones et journal d'audit.

**Stack** : Java 21 · Spring Boot 4.1 · Spring Security (JWT) · Spring Data JPA · PostgreSQL 17 · Flyway ·
Redis · RabbitMQ · Testcontainers · Docker

---

## Démarrage rapide

Prérequis : Java 21 et Docker.

```bash
# Option 1 : tout dans Docker (API + PostgreSQL + Redis + RabbitMQ)
docker compose up -d --build

# Option 2 : infrastructure dans Docker, API depuis l'IDE / le terminal
docker compose up -d postgres redis rabbitmq
./mvnw spring-boot:run
```

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui.html | Documentation interactive (Swagger UI) |
| http://localhost:8080/actuator/health | État de santé |
| http://localhost:15672 | Console RabbitMQ (guest / guest) |

Un compte administrateur est créé au démarrage : `admin@cashbank.local` / `Admin@12345`
(modifiable via `ADMIN_EMAIL` / `ADMIN_PASSWORD`).

### Essai rapide avec curl

```bash
# Inscription (crée aussi le portefeuille)
curl -X POST localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"firstName":"Koffi","lastName":"Agbo","email":"koffi@mail.com","phoneNumber":"+22997000001","password":"Secret123"}'

# Connexion
curl -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"koffi@mail.com","password":"Secret123"}'

# Dépôt (avec clé d'idempotence)
curl -X POST localhost:8080/api/v1/wallet/deposit -H "Authorization: Bearer <accessToken>" \
  -H "Idempotency-Key: 3f1c..." -H "Content-Type: application/json" -d '{"amount":50000}'
```

---

## Endpoints

| Méthode | Route | Rôle | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Créer un compte + portefeuille |
| POST | `/api/v1/auth/login` | public | Obtenir access token + refresh token |
| POST | `/api/v1/auth/refresh` | public | Rotation du refresh token |
| POST | `/api/v1/auth/logout` | USER | Révoquer les jetons |
| GET / PUT | `/api/v1/users/me` | USER | Profil (mis en cache Redis) |
| GET | `/api/v1/wallet` | USER | Solde et infos du portefeuille |
| GET | `/api/v1/wallet/limits` | USER | Plafonds et consommation du jour |
| POST | `/api/v1/wallet/deposit` | USER | Dépôt |
| POST | `/api/v1/wallet/withdraw` | USER | Retrait |
| POST | `/api/v1/wallet/transfer` | USER | Transfert (n° de portefeuille ou bénéficiaire) |
| GET | `/api/v1/wallet/transactions` | USER | Historique paginé (`type`, `from`, `to`, `page`, `size`, `sort`) |
| GET | `/api/v1/wallet/transactions/{ref}` | USER | Détail d'une transaction |
| GET / POST / DELETE | `/api/v1/beneficiaries` | USER | Gestion des bénéficiaires |
| GET / PATCH | `/api/v1/notifications` | USER | Notifications, compteur de non-lues, lecture |
| GET | `/api/v1/admin/users` | ADMIN | Liste des utilisateurs |
| PATCH | `/api/v1/admin/users/{id}/status` | ADMIN | Activer / désactiver un compte |
| PATCH | `/api/v1/admin/wallets/{n°}/status` | ADMIN | Geler / dégeler un portefeuille |
| GET | `/api/v1/admin/transactions` | ADMIN | Toutes les transactions |
| GET | `/api/v1/admin/audit-logs` | ADMIN | Journal d'audit filtrable |

---

## Architecture

Organisation **par fonctionnalité** (chaque package contient entité, repository, service, contrôleur, DTO) :

```
com.cashbank
├── auth/          inscription, login, refresh, logout
├── security/      config Spring Security, JWT, refresh tokens, blacklist, anti brute-force
├── user/          profil utilisateur, création du compte admin
├── wallet/        portefeuille, dépôts / retraits / transferts
├── transaction/   entité Transaction, historique, événement TransactionCompletedEvent
├── limit/         plafonds par transaction et journaliers
├── beneficiary/   bénéficiaires enregistrés
├── notification/  publication RabbitMQ, consommation, consultation
├── audit/         journal d'audit
├── admin/         back-office
└── common/        erreurs (Problem Details), pagination, logs corrélés, config
```

### Flux d'un transfert

```
POST /wallet/transfer
  └─ WalletService.transfer  ──────────── transaction SQL ─────────────┐
       ├─ contrôle du montant (min / max)                              │
       ├─ SELECT … FOR UPDATE sur les 2 portefeuilles (ordre par id)   │
       ├─ contrôle du plafond journalier                               │
       ├─ débit / crédit (règles portées par l'entité Wallet)          │
       ├─ INSERT transaction + audit de succès                         │
       └─ publication de TransactionCompletedEvent ────────────────────┘ COMMIT
                     │ (AFTER_COMMIT)
                     ▼
        NotificationPublisher ──► RabbitMQ ──► NotificationConsumer ──► table notifications
                                     │ (échec après 3 tentatives)
                                     ▼
                              dead-letter queue
```

---

## Choix techniques

### Sécurité
- **Access token JWT** (HS256, 15 min) validé par le *resource server* de Spring Security. Claims : `sub` (id),
  `email`, `roles`, `jti`.
- **Refresh token opaque** (7 jours) stocké dans **Redis** sous forme de **hash SHA-256** ; **rotation** à chaque
  usage via `GETDEL` atomique : un jeton volé et déjà utilisé est inutilisable.
- **Logout** : refresh token supprimé + `jti` de l'access token mis en **liste noire** Redis jusqu'à son expiration.
- **Anti brute-force** : 5 échecs → compte bloqué 15 min (compteur Redis à expiration).
- Comparaison de mot de passe même pour un e-mail inconnu (pas de fuite d'information par le temps de réponse).
- Rôles `USER` / `ADMIN` vérifiés au niveau URL **et** méthode (`@PreAuthorize`).

### Intégrité des fonds
- **Transactions SQL** : débit, crédit, écriture de la transaction et audit de succès sont atomiques.
- **Verrou pessimiste** (`SELECT … FOR UPDATE`) avant toute lecture de solde ; pour un transfert, les deux
  portefeuilles sont verrouillés **dans l'ordre de leur id** → pas de deadlock entre A→B et B→A simultanés.
- `@Version` (verrou optimiste) en filet de sécurité et contrainte SQL `CHECK (balance >= 0)`.
- **Idempotence** : en-tête `Idempotency-Key` + contrainte d'unicité `(initiated_by, idempotency_key)`.
- Montants en `BigDecimal` / `NUMERIC(19,2)`, jamais en `double`.
- Plafond journalier calculé dans le fuseau configuré (`Africa/Porto-Novo` par défaut).

### Audit
- Succès : écrit **dans** la transaction métier (opération et trace validées ensemble).
- Échec : écrit **après** le rollback, dans sa propre transaction (`REQUIRES_NEW`), une fois verrous et
  connexion libérés — évite d'épuiser le pool de connexions sous forte concurrence.

### Erreurs, validation, logs
- Toutes les erreurs suivent la **RFC 9457 (Problem Details)** avec un `code` métier stable, un `timestamp`,
  un `requestId` et, pour la validation, le détail par champ (`errors`).
- Chaque requête reçoit un `X-Request-Id` (repris s'il est fourni) placé dans le MDC → présent dans tous les logs.
- Schéma de base versionné avec **Flyway** ; Hibernate en mode `validate` uniquement.

---

## Tests

```bash
./mvnw test        # Docker doit être démarré (Testcontainers)
```

- **Tests unitaires** (JUnit 5, Mockito, AssertJ) : règles de l'entité `Wallet`, plafonds (dont le calcul du
  début de journée selon le fuseau), `WalletService` (ordre de verrouillage, rollback + audit en cas d'échec,
  idempotence), construction des notifications.
- **Tests d'intégration** (Spring Boot + **Testcontainers** : vrais PostgreSQL, Redis et RabbitMQ) :
  parcours d'authentification complet (rotation, logout, blocage), dépôts / retraits / transferts,
  notifications reçues via RabbitMQ, plafonds, pagination et filtres, isolation entre utilisateurs, back-office,
  et un **test de concurrence** : 20 retraits simultanés sur un solde ne permettant que 10 → exactement 10 réussissent,
  le solde finit à 0.

La CI GitHub Actions (`.github/workflows/ci.yml`) exécute l'ensemble des tests et construit l'image Docker.

---

## Configuration

| Variable | Défaut | Description |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/cashbank` / `cashbank` / `cashbank` | PostgreSQL |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ |
| `JWT_SECRET` | clé de dev | Clé HMAC en Base64 (≥ 256 bits). **À changer en production** : `openssl rand -base64 32` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@cashbank.local` / `Admin@12345` | Compte admin initial |

Les plafonds se règlent dans `application.yml` (`cashbank.limits.*`).

---

## Pistes d'amélioration

- **Transactional outbox** pour garantir la livraison des notifications même si RabbitMQ est indisponible au commit.
- Rate limiting global par IP / utilisateur (Redis, Bucket4j).
- Révocation de tous les refresh tokens d'un utilisateur lors de sa désactivation.
- Multi-devises avec taux de change, frais de transaction.
- Observabilité : traces distribuées (OpenTelemetry), métriques métier Micrometer.
