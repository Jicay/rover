describe('Joystick', () => {
  beforeEach(() => {
    cy.createBoard({ width: 5, height: 5 })
    cy.deployRover({ id: 'curiosity', x: 1, y: 1, direction: 'N' })
    cy.selectRover('curiosity')
  })

  it('compose une séquence au joystick et déplace le rover', () => {
    cy.pressJoystick('FFRF')

    cy.sequence().should('have.value', 'FFRF')

    cy.get('[data-testid="send-commands"]').click()

    cy.roverAt('curiosity', 2, 3, 'E')
  })

  it('accepte la saisie manuelle et le joystick dans le même champ', () => {
    cy.sequence().type('FF')
    cy.pressJoystick('RB')

    cy.sequence().should('have.value', 'FFRB')

    cy.get('[data-testid="joystick-erase"]').click()

    cy.sequence().should('have.value', 'FFR')

    cy.get('[data-testid="send-commands"]').click()

    cy.roverAt('curiosity', 1, 3, 'E')
  })

  it('refuse les caractères qui ne sont pas des commandes', () => {
    cy.sequence().type('f x r 9')

    cy.sequence().should('have.value', 'FR')
  })
})
