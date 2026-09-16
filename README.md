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
- **Testcontainers** 2 pour les tests d'intégration de la couche driven et les tests de composants
- **Cucumber** 7 (`cucumber-junit-platform-engine` + `cucumber-spring`) et **RestAssured** 6
  pour les tests de composants
- **MockK** pour les mocks, **SpringMockK** (`@MockkBean`) pour les beans mockés
- **JaCoCo** pour la couverture de code
- **PITest** (mutateurs `STRONGER`) pour les tests de mutation du domaine
- **k6** pour les tests de performance, **Docker Compose** pour l'environnement qu'ils mesurent

## Lancer l'application

```bash
docker run -d --name rover-pg -p 5432:5432 \
  -e POSTGRES_DB=rover -e POSTGRES_USER=rover -e POSTGRES_PASSWORD=rover postgres:18-alpine

./gradlew bootRun
```

Liquibase crée les trois tables au démarrage. L'URL, l'utilisateur et le mot de passe sont
surchargeables par `DATABASE_URL`, `DATABASE_USERNAME` et `DATABASE_PASSWORD`.

## Environnement dockerisé

Les tests de performance ont besoin d'une application **réellement déployée**, pas d'un
`bootRun` lancé depuis l'IDE. `docker-compose.yml` démarre l'application et sa base :

```bash
# Construit l'image, démarre Postgres puis l'application, et ne rend la main
# qu'une fois les deux conteneurs sains (--wait)
docker compose up --detach --build --wait

# Vérification
curl http://localhost:8080/actuator/health

# Démontage complet, volume de la base compris
docker compose down --volumes
```

| Service | Image | Port | Santé |
|---------|-------|------|-------|
| `postgres` | `postgres:18-alpine` | 5432 | `pg_isready` |
| `rover` | construite par le `Dockerfile` | 8080 | `GET /actuator/health` |

**Image de l'application : `Dockerfile` multi-étages, pas `bootBuildImage`.** Les buildpacks
Spring Boot produisent une meilleure image sans écrire une ligne de Dockerfile, mais ils ne
sont pas déclenchables par Compose : il faudrait lancer `./gradlew bootBuildImage` *avant*
`docker compose up`, donc deux commandes et un JDK 25 installé sur le poste. Avec un
`Dockerfile`, `build: .` suffit et **une seule commande** monte l'environnement complet,
sur un poste qui n'a que Docker. La première étape compile le jar dans un
`eclipse-temurin:25-jdk`, la seconde n'embarque qu'un `eclipse-temurin:25-jre`.

**L'application attend sa base** par un double verrou :

1. `postgres` déclare un `healthcheck` (`pg_isready`) ; `rover` déclare
   `depends_on: postgres: condition: service_healthy`. Compose ne démarre donc l'application
   qu'une fois la base **prête à accepter des connexions** — et non pas seulement « conteneur
   démarré », ce que fait un `depends_on` nu et qui rend le démarrage aléatoire : Liquibase se
   connecte dans les toutes premières secondes de vie de l'application et échoue si la base
   n'écoute pas encore.
2. `rover` déclare à son tour un `healthcheck` HTTP sur `/actuator/health`. C'est ce qui donne
   un sens à `docker compose up --wait` : la commande ne rend la main que lorsque l'API répond
   vraiment, migrations Liquibase appliquées. Sans cela, k6 démarrerait pendant l'initialisation
   du contexte Spring et mesurerait le démarrage de la JVM.

> `spring-boot-starter-actuator` est ajouté pour ce seul besoin. Un conteneur ne peut pas
> s'auto-diagnostiquer avec un `depends_on` : il lui faut un endpoint que Docker puisse
> interroger. `curl` est installé dans l'image finale pour la même raison —
> `eclipse-temurin:25-jre` est une image Ubuntu minimale qui n'embarque ni `curl` ni `wget`.

## Lancer les tests

Les tests unitaires (`src/test`) couvrent le domaine. Les tests d'intégration
(`src/testIntegration`) valident les deux bords de l'hexagone :

- **driving** — `@WebMvcTest` + MockMvc, use cases mockés (routage, JSON, codes HTTP) ;
- **driven** — `@SpringBootTest` sur un **vrai Postgres** lancé par Testcontainers
  (schéma Liquibase, SQL, reconstruction de l'agrégat). Docker doit tourner.

Chaque test de la couche driven suit les trois temps : préparation de la base, appel de la
méthode, vérification du résultat **et** du contenu des tables après l'appel — ce dernier
point étant le seul moyen de prouver que `save` ne duplique rien.

Les tests de composants (`src/testComponent`) valident l'application **entière** : elle est
démarrée pour de vrai sur un port aléatoire, contre un vrai Postgres, et pilotée uniquement
par son API REST. **Aucun mock.** Voir la section suivante.

```bash
# Tests unitaires
./gradlew test

# Tests d'intégration (driving + driven, nécessite Docker)
./gradlew testIntegration

# Tests de composants (nécessite Docker)
./gradlew testComponent

# Les trois, via check
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

## Tests de composants

Un test de composant valide **l'application entière**, démarrée pour de vrai avec sa vraie
base. On y teste le fonctionnement global — pas les règles métier précises, déjà couvertes
par les tests unitaires du domaine.

Les scénarios sont écrits en Gherkin (`src/testComponent/resources/features/rover.feature`),
en langage métier : ils se lisent sans connaître le code, et un PO peut les relire.

```gherkin
Scenario: A rover does not drive through another rover
  Given a board of 5 cells by 5
  And a rover "Curiosity" landed at (2, 2) facing North
  And a rover "Perseverance" landed at (2, 1) facing North
  When I send the sequence "F" to rover "Perseverance"
  Then rover "Perseverance" is at (2, 1) facing North
  And rover "Curiosity" is at (2, 2) facing North
```

| Fichier | Rôle |
|---------|------|
| `rover.feature` | les scénarios, en langage métier |
| `CucumberRunnerTest` | suite JUnit Platform (`@Suite` + moteur `cucumber`) **et** configuration Spring (`@CucumberContextConfiguration` + `@SpringBootTest(RANDOM_PORT)`) |
| `RoverStepDefs` | traduction des phrases en appels HTTP RestAssured |
| `DatabaseCleanupHooks` | `TRUNCATE` avant chaque scénario |
| `junit-platform.properties` | `cucumber.plugin` → rapports `pretty`, HTML et JSON |

**Isolation des scénarios.** Le contexte Spring et le conteneur Postgres sont partagés par
toute la suite — les redémarrer à chaque scénario coûterait des minutes. L'isolation repose
sur deux mécanismes :

- tout l'état de l'application vit en base, donc un `TRUNCATE` dans un hook `@Before` suffit
  à garantir qu'aucun scénario n'hérite du précédent, quel que soit l'ordre d'exécution ;
- `cucumber-spring` recrée les classes de glue dans le scope `cucumber-glue`, donc l'id du
  plateau et la dernière réponse HTTP mémorisés par `RoverStepDefs` repartent de zéro.

> Le support de cours cite `io.cucumber:cucumber-junit` : c'est le module **JUnit 4**,
> obsolète ici. La suite tourne sur `cucumber-junit-platform-engine` (JUnit 5).

> Cucumber 7.34.x est compilé contre JUnit 5.14.2 / JUnit Platform 1.14.2 — exactement les
> versions épinglées pour le moteur Kotest. C'est Cucumber qui s'aligne sur ces pins, pas
> l'inverse : le BOM Spring Boot 4 pousse Jupiter 6, incompatible avec Kotest.

> RestAssured n'est plus géré par le BOM Spring Boot 4 (il l'était en Boot 3) : sa version
> est déclarée explicitement.

> Sur Spring Boot 4, `@LocalServerPort` vit toujours dans
> `org.springframework.boot.test.web.server` (contrairement à `@WebMvcTest`, qui a déménagé
> dans `org.springframework.boot.webmvc.test.autoconfigure`).

## Tests de performance

Tous les niveaux précédents se suffisaient d'un contexte Spring en mémoire. Un test de
performance, non : il mesure une **application déployée**, avec sa vraie base, son vrai réseau
et son vrai serveur HTTP. C'est la raison d'être du `docker-compose.yml` ci-dessus.

Le dossier `performance/` suit le découpage du support de cours :

| Fichier | Rôle |
|---------|------|
| `config.json` | les scénarios (`executor`, `rate`, `duration`, `preAllocatedVUs`, `maxVUs`, `exec`) et les `thresholds` |
| `index.js` | les appels HTTP — le parcours métier |

### Le parcours mesuré

Un `GET` trivial ne mesurerait que Tomcat. Chaque itération rejoue le parcours métier complet,
soit **6 requêtes** :

1. `POST /boards` — un plateau 10×10 avec 5 obstacles tirés au hasard ;
2. `POST /boards/{id}/rovers` ×2 — deux rovers déployés en `(0,0)` et `(0,2)` ;
3. `POST /boards/{id}/rovers/{roverId}/commands` ×2 — une séquence de 15 commandes `FBLR`
   tirées au hasard ;
4. `GET /boards/{id}` — relecture de l'état.

C'est ce parcours qui met le domaine **et** la base sous charge : `BoardDAO.save` réécrit
l'agrégat entier (upsert du plateau, suppression puis réinsertion des rovers et des obstacles)
à chaque écriture, donc chaque `POST` coûte plusieurs allers-retours SQL.

Le tirage aléatoire est contraint pour ne jamais produire d'erreur métier : les obstacles
naissent en `x ≥ 4`, loin des positions de départ des rovers, et un plateau neuf est créé à
chaque itération. Sans cette précaution, un `409 Position already occupied` ferait grimper
`http_req_failed` et on mesurerait la gestion d'erreur au lieu de la performance. Les commandes,
elles, peuvent être totalement aléatoires : un mouvement bloqué par un mur, un obstacle ou un
autre rover n'est pas une erreur, le rover reste sur place et la séquence continue.

Chaque requête porte un tag `name` fixe (`POST /boards/_/rovers`, …). Sans lui, k6 agrégerait
par URL et produirait des dizaines de milliers de métriques distinctes — une par UUID de plateau.

### Les scénarios

```bash
docker compose up --detach --build --wait
k6 run --config performance/config.json --summary-mode full performance/index.js
docker compose down --volumes
```

Les quatre scénarios s'enchaînent dans une seule exécution, séquencés par leur `startTime` :

| Scénario | Executor | Charge | Ce qu'il illustre |
|----------|----------|--------|-------------------|
| `chauffe` | `constant-arrival-rate` | 10 it/s pendant 20 s | rien — il chauffe la JVM (voir plus bas) |
| `performance` | `constant-arrival-rate` | 5 it/s pendant 30 s | **test de performance** : la latence de référence, à charge faible, quand rien ne contend. C'est le meilleur temps de réponse que l'application sait offrir |
| `charge` | `constant-arrival-rate` | 25 it/s pendant 1 min | **test de charge** : la charge nominale attendue, tenue dans la durée. La question posée est « est-ce que ça tient ? », pas « jusqu'où ça monte ? » |
| `stress` | `ramping-arrival-rate` | rampe 50 → 200 → 500 it/s | **test de stress** : on pousse délibérément au-delà du nominal pour trouver le point de rupture. Son résultat n'est pas un pass/fail, c'est un chiffre : la limite |

Les deux premiers sont à **débit constant** (`constant-arrival-rate`), le troisième à **débit
croissant** (`ramping-arrival-rate`). C'est la différence structurante : un executor en
`arrival-rate` impose un débit d'arrivée indépendant des temps de réponse, à la différence des
executors en `vus` où une application qui ralentit reçoit mécaniquement moins de trafic — et où
la saturation devient donc invisible.

### Les chiffres mesurés

Mesure de référence, environnement neuf (`docker compose down --volumes` puis `up`), Apple
Silicon sous Docker Desktop :

| Scénario | Débit obtenu | latence `p(95)` | `p(50)` | max | échecs HTTP | itérations abandonnées |
|----------|--------------|-----------------|---------|-----|-------------|------------------------|
| `chauffe` | 60 req/s | 14,94 ms | 6,52 ms | 244,95 ms | 0 % | 0 |
| `performance` | 30 req/s | **16,09 ms** | 8,57 ms | 81,32 ms | 0 % | 0 |
| `charge` | 150 req/s | **8,44 ms** | 4,55 ms | 58,86 ms | 0 % | 0 |
| `stress` | ≈ 1 850 req/s en moyenne sur la rampe | **367,15 ms** | 20,30 ms | 1,59 s | 0 % | **922 (2,9 %)** |

Une seconde exécution, lancée sans redémonter l'environnement — la base contenait alors déjà
les ~35 000 plateaux du premier run — donne `p(95)` = 14,23 ms pour `performance` et 8,34 ms
pour `charge` : les deux scénarios nominaux sont **stables**. Le scénario `stress`, lui, se
dégrade nettement (`p(95)` 367 ms → 646 ms, 922 → 1 541 itérations abandonnées). Un seuil de
charge peut donc être calibré sans précaution particulière ; une mesure de stress, elle, n'a de
sens qu'à partir d'un état de base connu — c'est-à-dire après `docker compose down --volumes`.

Les `thresholds` en découlent, réglés à environ **2,5 à 3 fois** la valeur mesurée : assez près
pour qu'une régression réelle les casse, assez loin pour ne pas clignoter au moindre bruit de
mesure.

```json
"thresholds": {
  "http_req_failed{scenario:performance}":   ["rate<0.01"],
  "http_req_duration{scenario:performance}": ["p(95)<40"],
  "http_req_failed{scenario:charge}":        ["rate<0.01"],
  "http_req_duration{scenario:charge}":      ["p(95)<25"]
}
```

Les seuils sont **taggés par scénario**. Un seuil global serait ininterprétable : la rampe de
stress, qui représente 94 % des requêtes du run, écraserait à elle seule la mesure des deux
autres scénarios (`p(95)` global = 355 ms, contre 8,44 ms pour le seul scénario de charge).

**`stress` n'a volontairement aucun threshold.** Un test de stress qui « passe » n'a rien
mesuré : on l'écrit pour trouver la limite, pas pour la valider. Lui donner un seuil obligerait
soit à le calibrer si haut qu'il ne détecte rien, soit à faire échouer le build à chaque
exécution.

### Ce que le stress a montré

À 500 itérations/s visées, l'application ne renvoie **aucune erreur** — `http_req_failed` reste
à 0 %. La rupture ne se lit pas dans le taux d'échec mais ailleurs :

- la latence `p(95)` passe de 8,44 ms à 367 ms, soit un facteur **43** ;
- la durée d'une itération complète passe de 44 ms à 1,88 s au `p(95)` ;
- 922 itérations sont **abandonnées** (`dropped_iterations`) : au moment programmé, plus aucun
  VU n'était libre, parce que les précédents étaient encore en train d'attendre.

C'est le comportement typique d'une saturation de file d'attente : Tomcat accepte les
connexions, HikariCP fait patienter, et la dégradation se manifeste en temps de réponse, pas en
code d'erreur. **Surveiller uniquement le taux d'erreur ne détecte pas ce type de panne.**

### Les pièges rencontrés

**La JVM démarre froide.** Sans le scénario `chauffe`, le premier scénario exécuté paie le
coût du JIT et de l'ouverture du pool de connexions : mesuré à 19,36 ms de `p(95)` sur une JVM
froide contre 14,78 ms sur la même JVM chaude, soit **24 % d'écart**, uniquement dû à l'ordre
d'exécution. Sans phase de chauffe, la ligne de référence mesure le démarrage de l'application.

**La latence ne croît pas avec la charge.** Le résultat le plus contre-intuitif de la mesure :
le scénario `performance` (30 req/s) est **deux fois plus lent** que le scénario `charge`
(150 req/s) — 16,09 ms contre 8,44 ms de `p(95)`. L'effet est reproductible, et il subsiste
après la phase de chauffe : ce n'est donc pas le JIT. L'explication la plus probable est que la
machine hôte n'est jamais tout à fait chaude à débit faible — cœurs parqués, fréquence basse,
caches CPU et buffers Postgres refroidis entre deux requêtes espacées de 30 ms. Sous charge
soutenue, toute la pile reste chaude. La leçon n'est pas l'explication, c'est le fait :
**une mesure à faible débit n'est pas automatiquement le « meilleur cas »**, et un `p(95)` sur
900 requêtes est de toute façon bien plus bruité que sur 9 000.

**Les chiffres ci-dessus valent pour cette machine.** Sur un runner GitHub Actions partagé à
2 ou 4 vCPU, ils seront plusieurs fois supérieurs. Recalibrer les seuils fait partie du travail
d'installation des tests dans un nouvel environnement ; c'est précisément pour cette raison
qu'ils ne gardent pas le pipeline (voir CI/CD).

### k6 : local ou via Docker

Les deux fonctionnent, le script et la configuration sont identiques.

```bash
# k6 installé localement (brew install k6) — le plus pratique pour itérer
k6 run --config performance/config.json --summary-mode full performance/index.js

# k6 via son image Docker — aucune installation requise
docker run --rm --network rover \
  --volume "$PWD/performance:/performance:ro" \
  --env BASE_URL=http://rover:8080 \
  grafana/k6:1.1.0 run --quiet --summary-mode full \
  --config /performance/config.json /performance/index.js
```

La variante Docker est celle de la CI : elle n'impose aucune installation et **épingle la
version de k6**, ce qui compte pour comparer deux mesures dans le temps. Elle rejoint
l'application par le réseau Compose (`networks.default.name: rover` fixe son nom, sinon Compose
le dérive du nom du dossier) et l'appelle par son nom de service, `http://rover:8080` : pas de
`--network host`, qui ne se comporte pas de la même façon sur macOS et sur Linux.

En local, k6 installé directement est plus agréable — sortie en couleurs, pas de conteneur à
relancer — et supprime une couche de virtualisation entre l'injecteur et l'application.

## CI/CD

Le pipeline GitHub Actions s'exécute sur chaque push/PR vers `main` ou `master` :

1. Compilation et packaging (`assemble`)
2. Tests unitaires + publication des résultats
3. Tests d'intégration + publication des résultats
4. Tests de composants + publication des résultats et du rapport Cucumber
5. Rapport JaCoCo agrégé (unitaires + intégration + composants)
6. Tests de mutation PITest (artefact uploadé)

> Les tests de composants ne font quasiment pas bouger la couverture agrégée
> (98,48 % d'instructions avant comme après, 96,43 % → 97,62 % de branches) : ils repassent
> sur du code déjà couvert. C'est attendu — leur valeur est la confiance dans l'assemblage
> réel, pas le chiffre de couverture.

Les tests de performance vivent dans un **workflow séparé**, `performance.yml`, déclenché
**manuellement** (`workflow_dispatch`) :

1. construction de l'image et démarrage de l'environnement (`docker compose up --wait`)
2. exécution de k6 depuis son image Docker
3. publication du résumé k6 en artefact, logs de l'application en cas d'échec
4. `docker compose down --volumes`, toujours, même si k6 a échoué

**Pourquoi pas dans `ci.yml`, sur chaque push ?** Deux raisons, et la seconde est la vraie.

- Le coût : construire l'image puis exécuter la campagne ajoute plusieurs minutes à un build
  qui doit rester court pour être utile.
- La validité : un runner GitHub Actions est une machine partagée, à 2 ou 4 vCPU, dont la
  charge voisine est invisible. Un seuil de latence y varie d'un run à l'autre sans qu'aucune
  ligne de code n'ait changé. Le brancher sur chaque push, c'est produire des échecs aléatoires
  — et la seule réaction rationnelle des développeurs face à un test qui échoue au hasard est
  de relancer le build, puis de relever le seuil, puis de l'ignorer. Un test de performance
  instable ne protège de rien et apprend à ne pas regarder les rouges.

La mesure qui fait foi est celle d'un environnement dédié et stable. En CI partagée, le
workflow manuel garde sa valeur : il vérifie que l'environnement se monte, que le parcours
tient sous charge, et il donne un ordre de grandeur — pas une mesure de référence.
