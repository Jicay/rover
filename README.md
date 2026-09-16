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
│   ├── dto/         # DTO d'entrée/sortie, isolés du modèle de domaine
│   └── view/        # BoardViewController, BoardView — interface Thymeleaf
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

## Interface web

L'interface est rendue **côté serveur** par Thymeleaf. Pas de SPA, pas de framework front :
quatre formulaires HTML, un tableau, et une trentaine de lignes de JavaScript pour le seul
joystick. Le cours porte sur les tests, pas sur le front — et un rendu serveur donne un DOM
déterministe, entièrement présent au `load` de la page, ce qui supprime la première source de
flakiness des tests end-to-end (attendre qu'un composant se monte).

L'habillage est celui d'un jeu de tactique au tour par tour, parce que c'est ce que le kata
**est** : des unités sur une grille, du terrain qui bloque, des ordres envoyés en séquence.
Le sol est du régolithe texturé, les rochers sont des cases creusées, et chaque rover reçoit
une couleur d'unité (`BoardView` attribue un index de palette par ordre de débarquement) pour
qu'on repère d'un coup d'œil qui est où. Le vocabulaire de l'écran suit — secteur, rochers,
débarquer, cap Est — mais **le domaine, les routes et les champs de formulaire gardent leurs
noms** : `board`, `obstacles`, `direction`. L'habillage ne traverse pas l'hexagone.

| Route | Méthode | Rôle |
|-------|---------|------|
| `/` | `GET` | formulaire de création, et liste des secteurs récents |
| `/ui/boards` | `POST` | crée le plateau, redirige vers sa page |
| `/ui/boards/{boardId}` | `GET` | la page du plateau : grille + formulaires |
| `/ui/boards/{boardId}/rovers` | `POST` | déploie un rover, redirige vers la page |
| `/ui/boards/{boardId}/commands` | `POST` | envoie une séquence au rover sélectionné |

**Le préfixe `/ui` n'est pas cosmétique.** `BoardController` expose déjà `GET /boards/{boardId}`
en JSON. Une vue montée sur le même chemin donnerait un *ambiguous mapping* et l'application
refuserait de démarrer. Séparer les deux arbres d'URL laisse l'API REST intacte : c'est le même
domaine derrière deux adapters driving, pas une API réécrite pour les besoins de l'écran.

**Chaque écriture suit le motif POST/redirect/GET.** Les trois `POST` ne rendent jamais de HTML :
ils redirigent vers `GET /ui/boards/{boardId}`. Un `F5` après un envoi de commandes ne rejoue donc
pas la séquence. Les erreurs métier (`RoverDomainException`) sont attrapées par le contrôleur de
vue, poussées en *flash attribute* et affichées sur la page d'arrivée — le même `try/catch`
explicite que dans `BoardController`, pas de `@ControllerAdvice`.

**La grille est construite en Kotlin, pas dans le template.** `BoardView.from(board)` produit
directement la liste des lignes, de `y = height - 1` à `y = 0`, pour que l'origine `(0,0)` du
domaine se retrouve **en bas à gauche** à l'écran et non en haut à gauche comme le voudrait
l'ordre naturel d'un `<table>`. Le template ne fait que deux `th:each` imbriqués : aucune
arithmétique, aucune logique métier dans le HTML.

`BoardViewController` et `BoardView` vivent dans `infrastructure/driving/view` : ce sont des
adapters driving au même titre que le contrôleur REST, et les tests d'architecture Konsist du
TP5 continuent de s'appliquer sans modification (le domaine reste intouché, `driving` et `driven`
continuent de s'ignorer).

### La liste des secteurs précédents

L'accueil liste les **douze secteurs les plus récents**, avec leurs dimensions et le nombre de
rovers et de rochers, et chaque entrée est un lien vers sa page. Sans elle, revenir sur un
secteur imposait de retrouver son UUID à la main.

C'est la seule fonctionnalité du TP8 qui **traverse tout l'hexagone**, et à ce titre elle sert
de rappel de l'architecture :

| Couche | Ajout |
|--------|-------|
| `domain/model` | `BoardSummary` — une projection de lecture : id, dimensions, deux compteurs |
| `domain/port` | `BoardPort.findRecent(limit)` |
| `domain/usecase` | `ListBoardsUseCase` |
| `application` | le bean correspondant |
| `driven/postgres` | le SQL, avec les compteurs en sous-requêtes |
| `driving/view` | `RecentBoardView`, qui met les libellés au pluriel |

**Pourquoi une projection et non `List<Board>`.** Charger l'agrégat entier de chaque secteur
pour n'afficher qu'un compteur ferait, pour douze secteurs, vingt-cinq requêtes et la
reconstruction de douze `Board` complets. `BoardSummary` tient en une seule requête. La leçon
vaut au-delà du kata : **un port n'est pas obligé de ne renvoyer que des agrégats** ; une
projection de lecture est un concept du domaine à part entière.

**Il a fallu une migration.** La table `boards` n'avait pas de colonne de date — on ne peut pas
lister « les plus récents » sans un ordre. `004-add-boards-created-at.xml` ajoute `created_at`
avec le même `clock_timestamp()` que `rovers`, plus un index descendant. Comme `save` est un
upsert qui ne touche pas la colonne, **réenregistrer un secteur ne le fait pas remonter dans la
liste** — ce qui est testé.

**Le plafond de douze est une décision de présentation**, donc il vit dans le contrôleur, pas
dans le domaine. Le use case se contente de refuser une limite inférieure à 1.

### L'aperçu cliquable

Saisir `1,2 3,3` à la main pour poser deux rochers demande de tenir un repère dans sa tête et
de ne pas se tromper de sens. L'écran d'accueil affiche donc un **aperçu du secteur à l'échelle
des dimensions saisies**, et chaque case y est un `<button>` qui pose ou retire un rocher.

Comme pour le joystick, il y a **deux entrées pour un seul état** : l'aperçu et le champ texte
écrivent tous deux `[data-testid="board-obstacles"]`, qui reste le champ réellement soumis. On
peut cliquer, puis corriger au clavier, puis recliquer. Sans JavaScript, le champ texte suffit.

Deux garde-fous, tous deux nés d'un vrai défaut :

- **rétrécir le secteur oublie les rochers qui en sortent.** Sans cela, poser un rocher en (4,3)
  puis ramener la largeur à 3 produit un `PositionOutOfBoardException` au moment de valider —
  et comme les erreurs passent par un `redirect`, le formulaire revient vide : tout est perdu.
  L'élagage se déclenche sur `change` (à la validation du champ), jamais sur `input` : sur
  `input`, taper « 12 » passerait par l'état « 1 » et effacerait presque tout au passage ;
- **au-delà de 400 cases, l'aperçu s'efface** au profit du champ texte. Le domaine accepte
  n'importe quelle dimension positive ; un secteur 1000 × 1000 fabriquerait un million de
  boutons et figerait l'onglet.

### Le joystick

La séquence de commandes se saisit de deux façons, et les deux écrivent le **même** champ :

- au clavier, dans `[data-testid="command-sequence"]` ;
- à la croix directionnelle, dont chaque touche concatène sa lettre dans ce champ.

La correspondance n'est pas celle d'un pavé directionnel ordinaire, et c'est le point à
expliquer en séance : **haut et bas translatent** (`F`, `B`), **gauche et droite font pivoter
sur place** (`L`, `R`). Ce ne sont pas des déplacements latéraux — le domaine n'en a pas. C'est
la commande de char. Deux choses rendent la chose lisible sans lire la documentation : les
icônes de `L` et `R` sont des flèches **courbes** de rotation, pas des flèches droites, et une
phrase sous le champ le dit en clair.

La touche centrale de la croix efface le dernier ordre. Une commande qui ne sait qu'ajouter est
inutilisable dès le premier faux clic — et le retour arrière comble le trou au milieu du pad.

`joystick.js` ne fait que trois choses : concaténer, retrancher, et filtrer la saisie en
majuscules sur `[FBLR]` en préservant la position du curseur. Aucun framework, aucun état
applicatif dans la page. **Sans JavaScript, le champ texte et le bouton Exécuter fonctionnent
toujours** : le joystick est un confort, jamais un passage obligé.

### Le contrat de sélection

Les tests end-to-end sont le seul niveau où le test dépend du **rendu**. Ce couplage est
inévitable, mais il peut être placé où l'on veut. Ici il est explicite et versionné : chaque
élément que les tests doivent atteindre porte un attribut `data-*` prévu pour ça.

| Attribut | Porté par | Valeur |
|----------|-----------|--------|
| `data-testid` | tous les éléments interactifs | `board-width`, `board-height`, `board-obstacles`, `create-board`, `rover-id`, `rover-x`, `rover-y`, `rover-direction`, `deploy-rover`, `command-rover`, `command-sequence`, `send-commands`, `joystick-erase`, `board-preview`, `recent-boards`, `grid`, `board-id`, `error` |
| `data-rover-option` | le bouton radio d'une carte d'unité | l'identifiant du rover pilotable |
| `data-command` | chaque touche du joystick | `F`, `B`, `L` ou `R` |
| `data-preview-cell` | chaque case de l'aperçu d'accueil | `"x,y"`, comme `data-cell` |
| `data-recent-board` | chaque entrée de la liste des secteurs | l'identifiant du secteur |
| `data-cell` | chaque `<td>` de la grille | `"x,y"` en coordonnées **du domaine** |
| `data-rover` | la case occupée par un rover | l'identifiant du rover |
| `data-direction` | la case occupée par un rover | `N`, `E`, `S` ou `W` |
| `data-obstacle` | la case portant un obstacle | `"true"`, **attribut absent** sinon |
| `data-unit` | la case et la carte d'une unité | son index de palette, qui donne sa couleur |

Deux détails font la robustesse de ce contrat :

- `data-rover` et `data-cell` sont portés par le **même** élément. Un test peut donc interroger
  la grille dans les deux sens sans sélecteur supplémentaire : « qu'y a-t-il en (2,3) ? »
  (`[data-cell="2,3"]`) et « où est Curiosity ? » (`[data-rover="curiosity"]`) ;
- les attributs facultatifs sont **absents** plutôt que vides. `th:attr` omet l'attribut quand
  l'expression vaut `null`, ce qui rend `should('not.have.attr', 'data-rover')` une assertion
  exacte : une case libre n'a pas d'attribut `data-rover`, elle n'en a pas un vide.

## Persistance

`BoardPort` est implémenté par un unique adapter, `BoardDAO`, qui lit et écrit **l'agrégat
entier** : `Board` est la seule frontière transactionnelle du domaine, il n'y a donc pas de
port par table. Le SQL est écrit à la main avec `NamedParameterJdbcTemplate` — pas de JPA,
pas d'ORM : le mapping relationnel ↔ agrégat reste visible et testable.

Le schéma est géré par Liquibase (`db/changelog.xml` + un changelog par table) :

| Table | Colonnes | Contraintes |
|-------|----------|-------------|
| `boards` | `id`, `width`, `height`, `created_at` | PK `id`, index descendant sur `created_at` |
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
- **Thymeleaf** pour l'interface web (rendu côté serveur, aucun JavaScript)
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
- **Cypress** 16 en **TypeScript** pour les tests end-to-end, contre ce même environnement

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

## Tests end-to-end

Un test end-to-end pilote l'application **par son interface**, exactement comme un utilisateur :
il tape dans des champs, clique sur des boutons et lit ce qui s'affiche. Il ne connaît ni les
routes REST, ni le schéma de la base, ni le modèle du domaine.

C'est le seul niveau où la règle métier centrale du kata se **voit** : un rover arrêté par un
autre rover n'est pas une exception qu'on attrape dans un test, c'est une flèche qui ne bouge pas
à l'écran. Les tests de composants du TP4 vérifiaient déjà la même règle via l'API ; ce qu'ils ne
pouvaient pas vérifier, c'est que la grille la montre.

Comme les tests de performance, les tests end-to-end s'exécutent contre l'application
**réellement déployée** par `docker-compose.yml` — pas contre un `bootRun`, pas contre un
`@SpringBootTest`. Un `bootRun` lancé depuis l'IDE n'est pas ce qui part en production.

```bash
# 1. Lever l'environnement (image construite, base migrée, API joignable)
docker compose up --detach --build --wait

# 2. Lancer Cypress
npm --prefix e2e ci
npm --prefix e2e run cypress:run

# 3. Démonter
docker compose down --volumes
```

`npm ci` télécharge normalement le binaire Cypress via son script `postinstall`. Si npm est
configuré avec `ignore-scripts=true` — c'est le cas de beaucoup de postes d'entreprise — le
paquet s'installe sans son binaire et Cypress échoue avec *« No version of Cypress is installed »*.
Le rattrapage est explicite et idempotent :

```bash
npm --prefix e2e exec cypress install
```

Pour écrire ou déboguer un scénario, le mode interactif ouvre un navigateur piloté qui rejoue le
test à chaque sauvegarde du fichier, avec la *time-travel* sur chaque commande :

```bash
npm --prefix e2e run cypress:open
```

### Le dossier `e2e/`

| Fichier | Rôle |
|---------|------|
| `package.json` | Cypress 16, TypeScript 5.9, les trois scripts npm |
| `tsconfig.json` | `strict`, `noEmit`, `types: ["cypress"]` — la compilation est faite par Cypress, `tsc` ne sert qu'au typage |
| `cypress.config.ts` | `baseUrl`, `video: false`, `defaultCommandTimeout` |
| `cypress/e2e/apercu.cy.ts` | l'aperçu cliquable de l'écran d'accueil |
| `cypress/e2e/deploiement.cy.ts` | création du plateau, débarquement, refus des collisions |
| `cypress/e2e/historique.cy.ts` | la liste des secteurs précédents |
| `cypress/e2e/pilotage.cy.ts` | séquences de commandes et les trois façons d'être bloqué |
| `cypress/e2e/joystick.cy.ts` | la croix directionnelle, la saisie manuelle et leur cohabitation |
| `cypress/support/commands.ts` | les commandes personnalisées (`cy.createBoard`, `cy.roverAt`, …) |
| `cypress/support/index.d.ts` | leur typage, déclaré dans le namespace `Cypress` |

`baseUrl` vaut `http://localhost:8080` dans la configuration, et se surcharge par la variable
d'environnement `CYPRESS_BASE_URL` sans toucher au fichier : c'est ainsi que la CI pointe
Cypress vers `http://rover:8080`, le nom de service du réseau Compose.

### Les dix-sept scénarios

| Fichier | Scénario | Ce qu'il démontre |
|---------|----------|-------------------|
| `apercu` | pose des rochers au clic et les reporte sur le secteur créé | le clic écrit bien le champ soumis, et le serveur place les rochers là où on les a posés |
| `apercu` | retire un rocher au second clic | la case est une bascule, pas un ajout |
| `apercu` | reflète la saisie manuelle dans l'aperçu | la synchronisation va dans les deux sens |
| `apercu` | oublie les rochers qui sortent du secteur rétréci | le garde-fou qui évite de perdre tout le formulaire sur une erreur de bornes |
| `deploiement` | affiche un rover déployé à sa position et dans son orientation | le tour complet formulaire → domaine → base → grille ; l'origine `(0,0)` est bien en bas à gauche |
| `deploiement` | affiche les obstacles du plateau | les obstacles saisis à la création survivent à la persistance et sont visibles |
| `deploiement` | refuse deux rovers sur la même case | l'invariant `409` de l'agrégat remonte jusqu'à un message d'erreur lisible, et le second rover n'apparaît pas |
| `pilotage` | déplace le rover selon la séquence de commandes | `FFRF` : avancer et pivoter se composent, la case de départ se libère |
| `pilotage` | **bloque un rover derrière un autre, puis le laisse repartir** | **le scénario qui justifie le domaine** : un rover est un obstacle mobile. Le blocage n'est pas une erreur — il n'y a aucun message, juste une flèche immobile. Puis le premier rover avance et le second peut enfin passer |
| `pilotage` | bloque un rover devant un obstacle, qui tourne et repart | un mouvement bloqué n'interrompt pas la séquence : `RF` repart dans une autre direction |
| `pilotage` | bloque un rover contre le bord du plateau, qui tourne et repart | même comportement pour la troisième cause de blocage, le mur |
| `historique` | liste le secteur créé et permet d'y revenir | le tour complet création → liste → retour sur la page du secteur |
| `historique` | résume les dimensions, les rovers et les rochers | les compteurs de la projection sont justes |
| `historique` | remonte le secteur le plus récent en tête de liste | l'ordre repose sur `created_at`, pas sur l'UUID |
| `joystick` | compose une séquence au joystick et déplace le rover | les touches remplissent bien le champ, et ce champ est bien celui que le formulaire envoie |
| `joystick` | accepte la saisie manuelle et le joystick dans le même champ | les deux entrées écrivent le même état, aucune n'est maître ; le retour arrière retranche le dernier ordre |
| `joystick` | refuse les caractères qui ne sont pas des commandes | `f x r 9` devient `FR` : le filtre côté page évite un aller-retour serveur pour un `InvalidCommandException` |

Les scénarios d'aperçu et de joystick ne testent pas le domaine : ils testent **la page**. C'est la
seule chose que les niveaux précédents ne pouvaient pas atteindre, et c'est exactement pour cela
qu'ils sont ici plutôt que dans les tests de composants.

Les trois causes de blocage — mur, obstacle, autre rover — sont couvertes séparément. Elles
partagent la même implémentation (`Board.isFree`), mais ce sont trois règles différentes du
cahier des charges : les tester ensemble ferait disparaître deux d'entre elles le jour où
l'implémentation se scinde.

### Des sélecteurs qui survivent au CSS

Un test end-to-end est celui qui casse le plus facilement pour de mauvaises raisons. Le choix des
sélecteurs est le principal levier.

Ce qui est utilisé : `[data-testid="..."]` pour les éléments interactifs, `[data-cell="x,y"]`,
`[data-rover]`, `[data-direction]` et `[data-obstacle]` pour la grille (voir *Le contrat de
sélection* plus haut). Ces attributs n'existent que pour les tests : les toucher est un acte
délibéré, jamais un effet de bord.

Ce qui a été écarté :

| Écarté | Pourquoi |
|--------|----------|
| classes CSS (`.cell`, `.rover`) | elles décrivent l'apparence. Renommer une classe en refaisant le style casse la suite, et rien ne signale au développeur qu'il a touché à un contrat de test |
| position dans le DOM (`tr:nth-child(2) td:nth-child(3)`) | c'est le pire de tous : la position d'une case dépend de l'ordre de rendu des lignes. Ajouter un en-tête de colonne à la grille décale tout, et le test n'échoue même pas — il vérifie la mauvaise case |
| texte affiché (`cy.contains('↑')`) | le glyphe est de la présentation. Remplacer les flèches par des icônes, ou traduire l'interface, casserait chaque assertion |
| `id` HTML | uniques par page, donc inutilisables pour les cases d'une grille, et souvent déjà pris par d'autres usages |
| `cy.request` vers l'API REST pour assertion | ce serait retester le TP4. L'assertion d'un test end-to-end doit porter sur ce que l'utilisateur **voit** |

Une nuance sur `data-testid` : il est souvent critiqué comme « du code de test dans le code de
production ». C'est exact, et c'est le but. L'alternative n'est pas *aucun* couplage, c'est un
couplage **implicite** à la mise en page — bien plus coûteux, parce qu'il n'est écrit nulle part.

**La refonte de l'interface a servi de preuve.** L'écran a été entièrement redessiné — palette,
typographies, structure des panneaux, tuiles en relief, ajout du joystick — et les dix
scénarios sont passés sans qu'une seule assertion soit modifiée. Un seul changement a été
nécessaire, dans `cy.sendCommands` : le `<select>` du rover piloté est devenu un jeu de boutons
radio, donc `.select(roverId)` est devenu `.check()` sur `[data-rover-option]`. **Une ligne, dans
la commande personnalisée, pour un changement de contrôle HTML.** C'est tout l'intérêt de
regrouper les sélecteurs à un seul endroit : le jour où la page change, on répare la commande,
pas les spécifications.

### Isolation des tests

Chaque test commence par `cy.createBoard(...)`, qui crée un plateau neuf via le formulaire. Un
plateau est identifié par un UUID et il est l'**agrégat** du domaine : deux plateaux ne partagent
rien, ni rovers, ni obstacles. Deux tests ne peuvent donc pas se transmettre d'état, quel que
soit leur ordre d'exécution — et la suite reste parallélisable.

C'est ce qui permet de se passer du `TRUNCATE` des tests de composants (TP4). Là-bas, les
scénarios partageaient un contexte Spring et une base, et il fallait nettoyer entre chacun ; ici
la donnée s'accumule sans jamais se croiser. Une base qui grossit au fil des exécutions n'est pas
un problème d'isolation : c'est le même compromis que le tirage de plateaux neufs dans les tests
de performance.

La liste des secteurs récents est le seul écran **global** : elle montre les douze derniers
secteurs, y compris ceux créés par les autres tests. Les scénarios d'historique n'assertent donc
jamais sur le contenu entier de la liste, toujours sur `[data-recent-board="<leur propre id>"]` —
une assertion qui reste vraie quoi que les voisins aient créé.

Cypress ajoute son propre filet : `testIsolation` (actif par défaut) vide cookies et stockage
local entre deux tests. Le cookie de session qui porte les *flash attributes* ne survit donc pas
d'un test à l'autre — un message d'erreur ne peut pas fuir dans le test suivant.

### Cypress local ou dockerisé

Les deux fonctionnent, les spécifications sont identiques.

```bash
# Cypress installé localement — le plus pratique pour itérer
npm --prefix e2e run cypress:run

# Cypress via son image Docker — aucune installation, ni Node ni navigateur
docker run --rm --network rover \
  --volume "$PWD/e2e:/e2e" \
  --workdir /e2e \
  --env CYPRESS_BASE_URL=http://rover:8080 \
  cypress/included:16.0.0 --browser chrome
```

C'est la variante Docker qui tourne en CI, pour les mêmes raisons que k6 au TP7 : elle **épingle
la version de Cypress, de Node et du navigateur** (l'image 16.0.0 embarque Node 24 et Chrome 153),
et elle n'impose au runner aucune installation de navigateur. Un `cypress-io/github-action` sur un
runner nu devrait télécharger Chrome, puis le binaire Cypress — deux caches de plus à gérer, et
deux versions qui bougent sous les pieds de la suite.

L'image `cypress/included` a son point d'entrée sur `cypress run` : les arguments passés au
`docker run` sont ceux de la commande. Elle ignore le `node_modules` éventuellement monté depuis
l'hôte — elle utilise son propre Cypress global — ce qui évite de lui servir un binaire compilé
pour macOS.

En local, Cypress installé directement reste plus agréable : sortie en couleurs, mode interactif,
pas de volume à monter.

> **Chrome plutôt qu'Electron.** Cypress 16 déprécie l'Electron embarqué comme navigateur de test
> et affiche un avertissement à chaque exécution. Les scripts npm et la commande CI passent donc
> `--browser chrome` : c'est le navigateur que l'image Docker embarque, et celui qu'ont déjà les
> postes de développement.

> **Le binaire Cypress ne vient pas du registre npm.** `npm install` récupère le paquet ; le
> binaire, lui, est téléchargé séparément par un script `postinstall` et mis en cache dans
> `~/Library/Caches/Cypress` (ou `~/.cache/Cypress`). Un registre d'entreprise qui autorise le
> paquet ne garantit donc rien sur le binaire, et inversement `ignore-scripts=true` casse
> l'installation sans casser `npm ci`.

> **Cypress 16.1.0 a été refusé par la curation Artifactory** (`MIN-AGE-02-SUPPLYCHAIN` :
> « package version is 1 days old »). La suite est épinglée sur **16.0.0**, publiée deux semaines
> plus tôt. C'est une contrainte d'environnement, pas un choix technique — mais elle illustre un
> vrai point : la dernière version d'une dépendance n'est pas toujours installable, et un projet
> qui ne sait pas dire *quelle* version il utilise ne sait pas non plus reproduire ses exécutions.

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

Les tests end-to-end vivent eux aussi dans un **workflow séparé**, `e2e.yml`, déclenché
**manuellement** (`workflow_dispatch`) :

1. construction de l'image et démarrage de l'environnement (`docker compose up --wait`)
2. exécution de Cypress depuis `cypress/included:16.0.0`, sur le réseau Compose
3. en cas d'échec : captures d'écran publiées en artefact, logs de l'application affichés
4. `docker compose down --volumes`, toujours

**Pourquoi un workflow séparé et non des étapes dans `ci.yml` ?** Parce que la nature de la
dépendance n'est pas la même. `ci.yml` n'a besoin que d'un JDK et de Docker pour Testcontainers ;
le job end-to-end construit une image applicative, démarre deux conteneurs et en lance un
troisième qui embarque un navigateur. Les mélanger allongerait le retour sur chaque push d'une
préoccupation qui n'a rien à voir avec la compilation, et rendrait la lecture d'un échec plus
difficile : « le build est rouge » ne dirait plus si c'est le code ou l'assemblage déployé.

**Pourquoi manuel, alors que ces tests sont déterministes ?** C'est un choix assumé, et il
mérite d'être discuté en séance parce qu'il va contre la règle générale. Contrairement aux tests
de performance, rien ici ne dépend des caprices d'un runner partagé : les assertions portent sur
des attributs du DOM, pas sur des millisecondes. Ces tests **pourraient** bloquer une pull request,
et sur un vrai produit ils le devraient. Le prix à payer serait de plusieurs minutes sur chaque
push — construction de l'image applicative comprise. Sur ce dépôt, qui est un support de cours et
non un produit livré, on garde la CI courte et on lance la campagne end-to-end à la demande.

La question à retenir n'est donc pas « manuel ou automatique », mais **ce qui justifie l'un ou
l'autre** : un test de performance est écarté de la CI parce qu'il y serait *faux* ; un test
end-to-end n'en est écarté que parce qu'il y est *lent*. Le premier argument est définitif, le
second est un arbitrage qu'on révise le jour où le pipeline le permet.
