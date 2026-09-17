describe('Secteurs précédents', () => {
  it('liste le secteur créé et permet d\'y revenir', () => {
    cy.createBoard({ width: 5, height: 4 })

    cy.location('pathname').then((path) => {
      const id = path.split('/').pop()

      cy.visit('/')
      cy.get(`[data-recent-board="${id}"]`).click()

      cy.location('pathname').should('eq', `/ui/boards/${id}`)
      cy.get('[data-testid="board-id"]').should('have.text', id)
    })
  })

  it('résume les dimensions, les rovers et les rochers de chaque secteur', () => {
    cy.createBoard({ width: 6, height: 3, obstacles: '1,1 2,2' })
    cy.deployRover({ id: 'curiosity', x: 0, y: 0, direction: 'N' })

    cy.location('pathname').then((path) => {
      const id = path.split('/').pop()

      cy.visit('/')

      cy.get(`[data-recent-board="${id}"]`).should('contain', '6 × 3')
      cy.get(`[data-recent-board="${id}"]`).should('contain', '1 rover')
      cy.get(`[data-recent-board="${id}"]`).should('contain', '2 rochers')
    })
  })

  it('remonte le secteur le plus récent en tête de liste', () => {
    cy.createBoard({ width: 4, height: 4 })

    cy.location('pathname').then((first) => {
      const older = first.split('/').pop()

      cy.createBoard({ width: 5, height: 5 })

      cy.location('pathname').then((second) => {
        const newer = second.split('/').pop()

        cy.visit('/')

        cy.get('[data-testid="recent-boards"] a').first()
          .should('have.attr', 'data-recent-board', newer)
        cy.get(`[data-recent-board="${older}"]`).should('exist')
      })
    })
  })
})
