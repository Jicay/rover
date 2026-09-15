Feature: Driving rovers on a board

  As a mission operator,
  I want to deploy rovers on a board and send them command sequences,
  so that they explore the terrain without ever getting lost.

  Coordinates are given as (x, y): x goes East, y goes North,
  cell (0, 0) being the South-West corner of the board.

  Scenario: A rover executes the sequence it is given
    Given a board of 5 cells by 5
    And a rover "Curiosity" landed at (1, 1) facing North
    When I send the sequence "FFRF" to rover "Curiosity"
    Then rover "Curiosity" is at (2, 3) facing East

  Scenario: A rover stops at the edge of the board instead of falling off
    Given a board of 3 cells by 3
    And a rover "Opportunity" landed at (1, 2) facing North
    When I send the sequence "F" to rover "Opportunity"
    Then rover "Opportunity" is at (1, 2) facing North

  Scenario: A rover blocked by a rock still carries on with its sequence
    Given a board of 5 cells by 5 with a rock at (2, 3)
    And a rover "Spirit" landed at (2, 1) facing North
    When I send the sequence "FFRF" to rover "Spirit"
    Then rover "Spirit" is at (3, 2) facing East

  Scenario: A rover does not drive through another rover
    Given a board of 5 cells by 5
    And a rover "Curiosity" landed at (2, 2) facing North
    And a rover "Perseverance" landed at (2, 1) facing North
    When I send the sequence "F" to rover "Perseverance"
    Then rover "Perseverance" is at (2, 1) facing North
    And rover "Curiosity" is at (2, 2) facing North
    When I send the sequence "F" to rover "Curiosity"
    And I send the sequence "F" to rover "Perseverance"
    Then rover "Curiosity" is at (2, 3) facing North
    And rover "Perseverance" is at (2, 2) facing North

  Scenario: Two rovers cannot be landed on the same cell
    Given a board of 5 cells by 5
    And a rover "Curiosity" landed at (2, 2) facing North
    When I try to land rover "Perseverance" at (2, 2) facing South
    Then the deployment is rejected because the cell is already taken
    And the board holds only one rover
