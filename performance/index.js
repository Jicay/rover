import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const BOARD_WIDTH = 10;
const BOARD_HEIGHT = 10;
const OBSTACLE_COUNT = 5;
const OBSTACLE_MIN_X = 4;
const COMMAND_LENGTH = 15;
const COMMAND_ALPHABET = 'FBLR';

const ROVERS = [
  { id: 'R1', x: 0, y: 0, direction: 'N' },
  { id: 'R2', x: 0, y: 2, direction: 'E' },
];

function randomInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}

function randomObstacles() {
  const obstacles = [];
  for (let i = 0; i < OBSTACLE_COUNT; i += 1) {
    obstacles.push({
      x: randomInt(OBSTACLE_MIN_X, BOARD_WIDTH - 1),
      y: randomInt(0, BOARD_HEIGHT - 1),
    });
  }
  return obstacles;
}

function randomCommands() {
  let commands = '';
  for (let i = 0; i < COMMAND_LENGTH; i += 1) {
    commands += COMMAND_ALPHABET[randomInt(0, COMMAND_ALPHABET.length - 1)];
  }
  return commands;
}

function post(url, body, name) {
  return http.post(url, JSON.stringify(body), { headers: JSON_HEADERS, tags: { name } });
}

export function parcours() {
  const created = post(
    `${BASE_URL}/boards`,
    { width: BOARD_WIDTH, height: BOARD_HEIGHT, obstacles: randomObstacles() },
    'POST /boards',
  );
  if (!check(created, { 'plateau cree': (r) => r.status === 201 })) {
    return;
  }

  const boardId = created.json('id');

  for (const rover of ROVERS) {
    const deployed = post(`${BASE_URL}/boards/${boardId}/rovers`, rover, 'POST /boards/_/rovers');
    check(deployed, { 'rover deploye': (r) => r.status === 201 });
  }

  for (const rover of ROVERS) {
    const executed = post(
      `${BASE_URL}/boards/${boardId}/rovers/${rover.id}/commands`,
      { commands: randomCommands() },
      'POST /boards/_/rovers/_/commands',
    );
    check(executed, { 'commandes executees': (r) => r.status === 200 });
  }

  const reloaded = http.get(`${BASE_URL}/boards/${boardId}`, { tags: { name: 'GET /boards/_' } });
  check(reloaded, {
    'plateau relu': (r) => r.status === 200,
    'rovers presents': (r) => r.status === 200 && r.json('rovers').length === ROVERS.length,
  });
}

export default function () {
  parcours();
}
