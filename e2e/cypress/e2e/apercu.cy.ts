describe('Aperçu du secteur', () => {
  beforeEach(() => {
    cy.visit('/')
  })

  it('pose des rochers au clic et les reporte sur le secteur créé', () => {
    cy.toggleObstacle(1, 2)
    cy.toggleObstacle(3, 3)

    cy.get('[data-testid="board-obstacles"]').should('have.value', '1,2 3,3')

    cy.get('[data-testid="create-board"]').click()

    cy.cell(1, 2).should('have.attr', 'data-obstacle', 'true')
    cy.cell(3, 3).should('have.attr', 'data-obstacle', 'true')
    cy.cell(0, 0).should('not.have.attr', 'data-obstacle')
  })

  it('retire un rocher au second clic', () => {
    cy.toggleObstacle(2, 1)

    cy.get('[data-preview-cell="2,1"]').should('have.attr', 'data-obstacle', 'true')

    cy.toggleObstacle(2, 1)

    cy.get('[data-preview-cell="2,1"]').should('not.have.attr', 'data-obstacle')
    cy.get('[data-testid="board-obstacles"]').should('have.value', '')
  })

  it('reflète la saisie manuelle dans l\'aperçu', () => {
    cy.get('[data-testid="board-obstacles"]').type('0,0 4,3')

    cy.get('[data-preview-cell="0,0"]').should('have.attr', 'data-obstacle', 'true')
    cy.get('[data-preview-cell="4,3"]').should('have.attr', 'data-obstacle', 'true')
    cy.get('[data-preview-cell="1,1"]').should('not.have.attr', 'data-obstacle')
  })

  it('oublie les rochers qui sortent du secteur rétréci', () => {
    cy.toggleObstacle(4, 3)

    cy.get('[data-testid="board-width"]').clear().type('3').blur()

    cy.get('[data-preview-cell="4,3"]').should('not.exist')
    cy.get('[data-testid="board-obstacles"]').should('have.value', '')
  })
})
