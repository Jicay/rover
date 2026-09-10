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
```

Le domaine ne contient que du Kotlin natif : aucun framework, aucune bibliothèque externe.

## Stack technique

- **Kotlin** 2.3.20 + **Spring Boot** 4.0.6
- **Java** 25
- **Kotest** 6 (tests unitaires, tests property-based)
- **MockK** pour les mocks
- **JaCoCo** pour la couverture de code
- **PITest** (mutateurs `STRONGER`) pour les tests de mutation

## Lancer les tests

```bash
# Tests unitaires
./gradlew test

# Rapport de couverture JaCoCo (build/reports/jacoco/test/html/)
./gradlew jacocoTestReport

# Tests de mutation PITest (build/reports/pitest/)
./gradlew pitest
```

> Les mutants qui survivent portent sur du bytecode généré par Kotlin (`copy`, lambdas inline) :
> ce sont des mutants équivalents, que le plugin Arcmutate Kotlin saurait écarter. 100 % de
> mutants tués n'est pas un objectif atteignable ici, et ce n'est pas le but.

## CI/CD

Le pipeline GitHub Actions s'exécute sur chaque push/PR vers `main` ou `master` :

1. Build sans tests
2. Tests unitaires + publication des résultats
3. Rapport JaCoCo (artefact uploadé)
4. Tests de mutation PITest (artefact uploadé)
