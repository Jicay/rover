describe('Pilotage', () => {
  it('déplace le rover sur la grille selon la séquence de commandes', () => {
    cy.createBoard({ width: 5, height: 5 })
    cy.deployRover({ id: 'curiosity', x: 1, y: 1, direction: 'N' })

    cy.sendCommands('curiosity', 'FFRF')

    cy.roverAt('curiosity', 2, 3, 'E')
    cy.cell(1, 1).should('not.have.attr', 'data-rover')
  })

  it('bloque un rover derrière un autre, puis le laisse repartir quand la voie se libère', () => {
    cy.createBoard({ width: 5, height: 5 })
    cy.deployRover({ id: 'curiosity', x: 2, y: 2, direction: 'N' })
    cy.deployRover({ id: 'perseverance', x: 2, y: 1, direction: 'N' })

    cy.sendCommands('perseverance', 'F')

    cy.roverAt('perseverance', 2, 1, 'N')
    cy.roverAt('curiosity', 2, 2, 'N')

    cy.sendCommands('curiosity', 'F')

    cy.roverAt('curiosity', 2, 3, 'N')

    cy.sendCommands('perseverance', 'F')

    cy.roverAt('perseverance', 2, 2, 'N')
  })

  it('bloque un rover devant un obstacle, qui tourne et repart', () => {
    cy.createBoard({ width: 5, height: 5, obstacles: '1,2' })
    cy.deployRover({ id: 'curiosity', x: 1, y: 1, direction: 'N' })

    cy.sendCommands('curiosity', 'F')

    cy.roverAt('curiosity', 1, 1, 'N')
    cy.cell(1, 2).should('have.attr', 'data-obstacle', 'true').and('not.have.attr', 'data-rover')

    cy.sendCommands('curiosity', 'RF')

    cy.roverAt('curiosity', 2, 1, 'E')
  })

  it('bloque un rover contre le bord du plateau, qui tourne et repart', () => {
    cy.createBoard({ width: 3, height: 3 })
    cy.deployRover({ id: 'curiosity', x: 0, y: 0, direction: 'S' })

    cy.sendCommands('curiosity', 'F')

    cy.roverAt('curiosity', 0, 0, 'S')

    cy.sendCommands('curiosity', 'LF')

    cy.roverAt('curiosity', 1, 0, 'E')
  })
})
