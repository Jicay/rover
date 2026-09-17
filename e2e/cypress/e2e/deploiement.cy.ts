describe('Déploiement', () => {
  it('affiche un rover déployé à sa position et dans son orientation', () => {
    cy.createBoard({ width: 5, height: 5 })

    cy.deployRover({ id: 'curiosity', x: 1, y: 3, direction: 'N' })

    cy.roverAt('curiosity', 1, 3, 'N')
    cy.cell(0, 0).should('not.have.attr', 'data-rover')
  })

  it('affiche les obstacles du plateau', () => {
    cy.createBoard({ width: 4, height: 4, obstacles: '1,1 2,3' })

    cy.cell(1, 1).should('have.attr', 'data-obstacle', 'true')
    cy.cell(2, 3).should('have.attr', 'data-obstacle', 'true')
    cy.cell(0, 0).should('not.have.attr', 'data-obstacle')
  })

  it('refuse deux rovers sur la même case', () => {
    cy.createBoard({ width: 4, height: 4 })
    cy.deployRover({ id: 'curiosity', x: 2, y: 2, direction: 'N' })

    cy.deployRover({ id: 'perseverance', x: 2, y: 2, direction: 'S' })

    cy.get('[data-testid="error"]').should('be.visible')
    cy.rover('perseverance').should('not.exist')
    cy.roverAt('curiosity', 2, 2, 'N')
  })
})
