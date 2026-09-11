# Mars Rover Kata

Implémentation du kata Mars Rover en Kotlin, suivant les principes TDD et une architecture hexagonale.

Ce dépôt est le corrigé du cours *Méthodologie de tests et tests unitaires* (M2 Ynov).
Chaque commit correspond à une étape du TP guidé.

## Problème

Plusieurs rovers évoluent sur un même plateau rectangulaire semé d'obstacles, en recevant
des séquences de commandes :

| Commande | Action |
|----------|--------|
| `F` | Avancer d'une case dans la direction courante |
| `B` | Reculer d'une case dans la direction courante |
| `L` | Pivoter de 90° à gauche |
| `R` | Pivoter de 90° à droite |

Un mouvement est bloqué par une limite du plateau, un obstacle ou un autre rover. Dans ce cas
le rover reste sur place et **la séquence se poursuit** : un rover bloqué peut tourner et
repartir dans une autre direction.

## Modèle du domaine

```
Board(id, width, height, obstacles, rovers)   # agrégat
  ├── Position(x, y)         # (0,0) = coin bas-gauche
  ├── Rover(id, position, direction)
  ├── Direction { N, E, S, W }
  └── Command { F, B, L, R }
```

**Orientations et deltas :** `N` → y+1 | `S` → y-1 | `E` → x+1 | `W` → x-1

Le `Board` est l'agrégat : lui seul connaît l'occupation des cases, donc lui seul peut arbitrer
les murs, les obstacles et les collisions. Ses invariants sont garantis à la construction :

- dimensions ≥ 1×1
- rovers et obstacles à l'intérieur du plateau
- deux rovers ne partagent jamais une case, et aucun rover ne se tient sur un obstacle
- les identifiants de rovers sont uniques sur un plateau

## Architecture

```
domain/
├── model/      # Board (agrégat), Rover, Position, Direction, Command
├── exception/  # Erreurs métier, traduites en codes HTTP par l'infrastructure
├── port/       # BoardPort — persistance de l'agrégat
└── usecase/    # CreateBoard, DeployRover, ExecuteCommands, GetBoard

application/    # UseCasesConfiguration — câblage des use cases en beans Spring
infrastructure/
├── driving/
│   ├── controller/  # BoardController — routes REST
│   └── dto/         # DTO d'entrée/sortie, isolés du modèle de domaine
└── driven/
    └── postgres/    # BoardDAO — implémentation Postgres de BoardPort (SQL écrit à la main)
```

Le domaine ne contient que du Kotlin natif : aucun framework, aucune bibliothèque externe.
Les use cases sont des classes ordinaires ; c'est la couche `application` qui les expose
en beans, et la couche `driving` qui traduit HTTP ↔ domaine.

## API REST

| Route | Corps de requête | Succès |
|-------|------------------|--------|
| `POST /boards` | `{"width":5,"height":4,"obstacles":[{"x":1,"y":1}]}` | `201` |
| `POST /boards/{boardId}/rovers` | `{"id":"rover-1","x":2,"y":3,"direction":"E"}` | `201` |
| `POST /boards/{boardId}/rovers/{roverId}/commands` | `{"commands":"FFRFF"}` | `200` |
| `GET /boards/{boardId}` | — | `200` |

Les quatre routes renvoient le même corps : l'état du plateau
(`id`, `width`, `height`, `obstacles`, `rovers`). L'identifiant du plateau est un UUID généré
par la couche driving : le domaine ne génère rien, il reste déterministe et testable.

Les erreurs métier sont traduites par un `try/catch` explicite dans le controller — pas de
`@RestControllerAdvice` — afin de garder un couplage minimal à Spring :

| Exception du domaine | Code |
|----------------------|------|
| `InvalidBoardDimensionsException`, `InvalidCommandException`, `PositionOutOfBoardException` | `400` |
| `PositionAlreadyOccupiedException`, `DuplicateRoverIdException` | `409` |
| `BoardNotFoundException`, `RoverNotFoundException` | `404` |

Un corps de requête malformé (JSON invalide, champ obligatoire absent, direction inconnue)
donne `400` via le traitement par défaut de Spring MVC.

## Persistance

`BoardPort` est implémenté par un unique adapter, `BoardDAO`, qui lit et écrit **l'agrégat
entier** : `Board` est la seule frontière transactionnelle du domaine, il n'y a donc pas de
port par table. Le SQL est écrit à la main avec `NamedParameterJdbcTemplate` — pas de JPA,
pas d'ORM : le mapping relationnel ↔ agrégat reste visible et testable.

Le schéma est géré par Liquibase (`db/changelog.xml` + un changelog par table) :

| Table | Colonnes | Contraintes |
|-------|----------|-------------|
| `boards` | `id`, `width`, `height` | PK `id` |
| `rovers` | `board_id`, `rover_id`, `x`, `y`, `direction`, `created_at` | **PK composite `(board_id, rover_id)`**, FK vers `boards` |
| `obstacles` | `id`, `board_id`, `x`, `y` | **unicité `(board_id, x, y)`**, FK vers `boards` |

Les deux contraintes en gras ne sont pas décoratives : elles font respecter **en base** les
invariants que l'agrégat garantit en mémoire. L'identifiant d'un rover est fourni par le
client et n'est unique qu'au sein d'un plateau → clé primaire composite ; `Board.obstacles`
est un `Set` de positions → contrainte d'unicité. Si le DAO régresse, la base refuse l'écriture
au lieu de laisser passer une donnée incohérente.

`save` est **idempotent** : la ligne `boards` est upsertée (`ON CONFLICT DO UPDATE`), les
rovers et obstacles du plateau sont supprimés puis réinsérés, le tout dans une seule
transaction. Sauvegarder deux fois le même plateau laisse exactement les mêmes lignes.

`created_at` (valeur par défaut `clock_timestamp()`, qui avance à chaque instruction même
dans une seule transaction) sert à relire les rovers dans leur ordre de déploiement :
`Board.rovers` est une `List`, l'aller-retour doit restituer le même ordre.

## Stack technique

- **Kotlin** 2.3.20 + **Spring Boot** 4.0.6 (Spring MVC, Jackson 3)
- **Java** 25
- **PostgreSQL** + **Liquibase** pour le schéma, `NamedParameterJdbcTemplate` pour le SQL
- **Kotest** 6 (tests unitaires, tests property-based, tests d'intégration via
  `kotest-extensions-spring`)
- **Testcontainers** 2 pour les tests d'intégration de la couche driven
- **MockK** pour les mocks, **SpringMockK** (`@MockkBean`) pour les beans mockés
- **JaCoCo** pour la couverture de code
- **PITest** (mutateurs `STRONGER`) pour les tests de mutation du domaine

## Lancer l'application

```bash
docker run -d --name rover-pg -p 5432:5432 \
  -e POSTGRES_DB=rover -e POSTGRES_USER=rover -e POSTGRES_PASSWORD=rover postgres:18-alpine

./gradlew bootRun
```

Liquibase crée les trois tables au démarrage. L'URL, l'utilisateur et le mot de passe sont
surchargeables par `DATABASE_URL`, `DATABASE_USERNAME` et `DATABASE_PASSWORD`.

## Lancer les tests

Les tests unitaires (`src/test`) couvrent le domaine. Les tests d'intégration
(`src/testIntegration`) valident les deux bords de l'hexagone :

- **driving** — `@WebMvcTest` + MockMvc, use cases mockés (routage, JSON, codes HTTP) ;
- **driven** — `@SpringBootTest` sur un **vrai Postgres** lancé par Testcontainers
  (schéma Liquibase, SQL, reconstruction de l'agrégat). Docker doit tourner.

Chaque test de la couche driven suit les trois temps : préparation de la base, appel de la
méthode, vérification du résultat **et** du contenu des tables après l'appel — ce dernier
point étant le seul moyen de prouver que `save` ne duplique rien.

```bash
# Tests unitaires
./gradlew test

# Tests d'intégration (driving + driven, nécessite Docker)
./gradlew testIntegration

# Les deux, via check
./gradlew build

# Rapport de couverture agrégé (build/reports/jacoco/jacocoFullReport/)
./gradlew jacocoFullReport

# Tests de mutation PITest (build/reports/pitest/)
./gradlew pitest
```

> `kotest-extensions-spring` est publié sous `io.kotest` et suit le versioning de Kotest depuis
> la 6 (`io.kotest:kotest-extensions-spring:6.1.11`). L'ancien artefact
> `io.kotest.extensions:kotest-extensions-spring` s'arrête à 1.3.0 et casse sur Kotest 6.

> Testcontainers 2 a renommé ses modules : `org.testcontainers:postgresql` est devenu
> `org.testcontainers:testcontainers-postgresql`, et `PostgreSQLContainer` a déménagé dans
> le package `org.testcontainers.postgresql`. La version vient du BOM Spring Boot 4 (2.0.5).

> Le conteneur est monté par `install(TestContainerProjectExtension(postgres))` dans le bloc
> `init` du spec : il démarre à l'instanciation de la classe, donc **avant** que
> `SpringExtension` ne construise le contexte. L'ancienne `ContainerExtension` (dépréciée en
> 6.1, supprimée en 6.2) démarrait le conteneur dans `beforeSpec`, ce qui est trop tard pour
> un `@SpringBootTest` : la DataSource et Liquibase se connectent avant.

> Les mutants qui survivent portent sur du bytecode généré par Kotlin (`copy`, lambdas inline) :
> ce sont des mutants équivalents, que le plugin Arcmutate Kotlin saurait écarter. 100 % de
> mutants tués n'est pas un objectif atteignable ici, et ce n'est pas le but.

## CI/CD

Le pipeline GitHub Actions s'exécute sur chaque push/PR vers `main` ou `master` :

1. Compilation et packaging (`assemble`)
2. Tests unitaires + publication des résultats
3. Tests d'intégration + publication des résultats
4. Rapport JaCoCo agrégé (unitaires + intégration)
5. Tests de mutation PITest (artefact uploadé)
