export {}

const testId = (name: string) => `[data-testid="${name}"]`

const fill = (name: string, value: string) => {
  cy.get(testId(name)).clear()
  if (value !== '') {
    cy.get(testId(name)).type(value)
  }
}

Cypress.Commands.add('createBoard', ({ width, height, obstacles = '' }) => {
  cy.visit('/')
  fill('board-width', String(width))
  fill('board-height', String(height))
  fill('board-obstacles', obstacles)
  cy.get(testId('create-board')).click()
  cy.location('pathname').should('match', /^\/ui\/boards\/[0-9a-f-]+$/)
})

Cypress.Commands.add('deployRover', ({ id, x, y, direction }) => {
  fill('rover-id', id)
  fill('rover-x', String(x))
  fill('rover-y', String(y))
  cy.get(testId('rover-direction')).select(direction)
  cy.get(testId('deploy-rover')).click()
})

Cypress.Commands.add('selectRover', (roverId) => {
  cy.get(`[data-rover-option="${roverId}"]`).check()
})

Cypress.Commands.add('pressJoystick', (commands) => {
  commands.split('').forEach((command) => {
    cy.get(`[data-command="${command}"]`).click()
  })
})

Cypress.Commands.add('sequence', () => cy.get(testId('command-sequence')))

Cypress.Commands.add('toggleObstacle', (x, y) => {
  cy.get(`[data-preview-cell="${x},${y}"]`).click()
})

Cypress.Commands.add('sendCommands', (roverId, commands) => {
  cy.selectRover(roverId)
  fill('command-sequence', commands)
  cy.get(testId('send-commands')).click()
})

Cypress.Commands.add('cell', (x, y) => cy.get(`[data-cell="${x},${y}"]`))

Cypress.Commands.add('rover', (roverId) => cy.get(`[data-rover="${roverId}"]`))

Cypress.Commands.add('roverAt', (roverId, x, y, direction) => {
  cy.cell(x, y)
    .should('have.attr', 'data-rover', roverId)
    .and('have.attr', 'data-direction', direction)
})
