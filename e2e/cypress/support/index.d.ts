type Direction = 'N' | 'E' | 'S' | 'W'

interface BoardOptions {
  width: number
  height: number
  obstacles?: string
}

interface RoverOptions {
  id: string
  x: number
  y: number
  direction: Direction
}

declare namespace Cypress {
  interface Chainable {
    createBoard(options: BoardOptions): Chainable<void>

    deployRover(options: RoverOptions): Chainable<void>

    selectRover(roverId: string): Chainable<JQuery<HTMLElement>>

    pressJoystick(commands: string): Chainable<void>

    sequence(): Chainable<JQuery<HTMLElement>>

    toggleObstacle(x: number, y: number): Chainable<JQuery<HTMLElement>>

    sendCommands(roverId: string, commands: string): Chainable<void>

    cell(x: number, y: number): Chainable<JQuery<HTMLElement>>

    rover(roverId: string): Chainable<JQuery<HTMLElement>>

    roverAt(roverId: string, x: number, y: number, direction: Direction): Chainable<void>
  }
}
