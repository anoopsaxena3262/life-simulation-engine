// Playback client for /api/v1/boards. Relative URLs keep the page and the API
// on one origin, so the browser does not apply CORS.
// Delete src/main/resources/static to remove this page.

const MAX_SIDE = 40;

const PRESETS = {
  blinker: ["000", "111", "000"],
  block: ["0000", "0110", "0110", "0000"],
  toad: ["000000", "001110", "011100", "000000"],
  beacon: ["1100", "1100", "0011", "0011"],
  glider: ["010000", "001000", "111000", "000000", "000000", "000000"],
  empty: ["0000", "0000", "0000", "0000"],
  single: ["000", "010", "000"],
};

const canvas = document.querySelector("#board");
const generationLabel = document.querySelector("#generation");
const lastCall = document.querySelector("#last-call");
const boardIdLabel = document.querySelector("#board-id");
const conclusionLabel = document.querySelector("#conclusion");
const message = document.querySelector("#message");
const presetSelect = document.querySelector("#preset");
const widthInput = document.querySelector("#width");
const heightInput = document.querySelector("#height");
const speedInput = document.querySelector("#speed");
const speedValue = document.querySelector("#speed-value");

const state = {
  width: 3,
  height: 3,
  grid: [],
  boardId: null,
  generation: 0,
  frames: new Map(),
  finalInfo: null,
  finalNote: null,
  playing: false,
  busy: false,
  timer: 0,
  lifetime: 0,
};

if (location.protocol === "file:") {
  setMessage("Open this page from the running service at http://localhost:8080/.", true);
}

function rowsToGrid(rows) {
  return rows.map((row) => [...row].map((cell) => cell === "1"));
}

function copyGrid(grid) {
  return grid.map((row) => row.slice());
}

function emptyGrid(width, height) {
  return Array.from({ length: height }, () => Array.from({ length: width }, () => false));
}

function delay() {
  return Number(speedInput.value);
}

function setMessage(text, isError) {
  message.textContent = text || "";
  message.classList.toggle("error", Boolean(isError));
}

function noteCall(text) {
  lastCall.textContent = text;
}

function invalidate() {
  state.lifetime += 1;
  state.playing = false;
  window.clearTimeout(state.timer);
}

function loadLocal(width, height, grid) {
  invalidate();
  state.width = width;
  state.height = height;
  state.grid = copyGrid(grid);
  state.boardId = null;
  state.generation = 0;
  state.frames = new Map();
  state.finalInfo = null;
  state.finalNote = null;
  widthInput.value = String(width);
  heightInput.value = String(height);
  noteCall("No request yet");
  render();
}

function boardFromPattern(name, width, height) {
  const rows = PRESETS[name];
  const patternWidth = rows[0].length;
  const patternHeight = rows.length;
  let boardWidth = width;
  let boardHeight = height;
  let note = "";
  if (boardWidth < patternWidth || boardHeight < patternHeight) {
    boardWidth = Math.max(boardWidth, patternWidth);
    boardHeight = Math.max(boardHeight, patternHeight);
    note = `That pattern needs at least ${patternWidth} by ${patternHeight}, so the board is that size.`;
  }
  const grid = emptyGrid(boardWidth, boardHeight);
  const row0 = Math.floor((boardHeight - patternHeight) / 2);
  const col0 = Math.floor((boardWidth - patternWidth) / 2);
  for (let row = 0; row < patternHeight; row += 1) {
    for (let col = 0; col < patternWidth; col += 1) {
      grid[row0 + row][col0 + col] = rows[row][col] === "1";
    }
  }
  return { width: boardWidth, height: boardHeight, grid, note };
}

function applyPreset(name) {
  const width = clampSide(widthInput.value);
  const height = clampSide(heightInput.value);
  if (name === "custom") {
    loadLocal(width, height, emptyGrid(width, height));
    setMessage("Blank board. Click cells, then Play.");
    return;
  }
  const built = boardFromPattern(name, width, height);
  loadLocal(built.width, built.height, built.grid);
  setMessage(built.note);
}

function clampSide(value) {
  const number = Number(value);
  if (!Number.isInteger(number)) return 12;
  return Math.min(MAX_SIDE, Math.max(1, number));
}

function render() {
  draw();
  generationLabel.textContent = `${state.width}×${state.height} · Generation ${state.generation}`;
  boardIdLabel.textContent = state.boardId || "Not uploaded";
  conclusionLabel.textContent = conclusionText();
  speedValue.textContent = `${delay()} ms`;
  document.querySelector("#pause").disabled = !state.playing;
  document.querySelector("#play").disabled = state.playing;
  document.querySelector("#reset").disabled = !state.boardId;
}

function conclusionText() {
  if (state.finalInfo) {
    const info = state.finalInfo;
    return `${info.terminationKind}, period ${info.period}, first seen at ${info.firstOccurrenceGeneration}, walked ${info.generationsComputed}, limit ${info.generationsLimit}`;
  }
  if (state.finalNote) return state.finalNote;
  if (state.boardId) return "Asking /final…";
  return "Upload a board to ask /final.";
}

function draw() {
  const cssWidth = canvas.clientWidth || 520;
  const cssHeight = cssWidth;
  const scale = window.devicePixelRatio || 1;
  const bitmap = Math.round(cssWidth * scale);
  if (canvas.width !== bitmap || canvas.height !== bitmap) {
    canvas.width = bitmap;
    canvas.height = bitmap;
  }
  const ctx = canvas.getContext("2d");
  ctx.setTransform(scale, 0, 0, scale, 0, 0);
  const cell = cssWidth / state.width;
  const cellH = cssHeight / state.height;
  ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue("--dead").trim() || "#f7f4ee";
  ctx.fillRect(0, 0, cssWidth, cssHeight);
  ctx.fillStyle = "#1c1915";
  for (let row = 0; row < state.height; row += 1) {
    for (let col = 0; col < state.width; col += 1) {
      if (!state.grid[row][col]) continue;
      const pad = Math.min(cell, cellH) > 16 ? 1 : 0;
      ctx.fillRect(col * cell + pad, row * cellH + pad, cell - pad * 2, cellH - pad * 2);
    }
  }
  ctx.strokeStyle = "#b7aa96";
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let col = 1; col < state.width; col += 1) {
    ctx.moveTo(col * cell + 0.5, 0);
    ctx.lineTo(col * cell + 0.5, cssHeight);
  }
  for (let row = 1; row < state.height; row += 1) {
    ctx.moveTo(0, row * cellH + 0.5);
    ctx.lineTo(cssWidth, row * cellH + 0.5);
  }
  ctx.stroke();
}

function cellFromEvent(event) {
  const rect = canvas.getBoundingClientRect();
  const col = Math.floor(((event.clientX - rect.left) / rect.width) * state.width);
  const row = Math.floor(((event.clientY - rect.top) / rect.height) * state.height);
  if (row < 0 || col < 0 || row >= state.height || col >= state.width) return null;
  return { row, col };
}

async function api(path, options) {
  const response = await fetch(path, {
    ...options,
    headers: {
      Accept: "application/json",
      ...(options && options.body ? { "Content-Type": "application/json" } : {}),
    },
  });
  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }
  if (!response.ok) {
    const error = new Error(problemMessage(body, response.status));
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return body;
}

function problemMessage(body, status) {
  if (!body) return `The service returned ${status}.`;
  if (body.detail) return body.detail;
  if (body.title) return body.title;
  return `The service returned ${status}.`;
}

function remember(generation, cells) {
  state.frames.set(generation, copyGrid(cells));
}

function showFrame(generation, cells) {
  state.generation = generation;
  state.grid = copyGrid(cells);
  render();
}

async function upload() {
  const ticket = state.lifetime;
  const path = "/api/v1/boards";
  noteCall(`POST ${path}`);
  const created = await api(path, {
    method: "POST",
    body: JSON.stringify({
      width: state.width,
      height: state.height,
      cells: state.grid,
    }),
  });
  if (ticket !== state.lifetime) return null;
  state.boardId = created.id;
  state.generation = created.generation;
  state.grid = copyGrid(created.cells);
  state.frames = new Map([[created.generation, copyGrid(created.cells)]]);
  state.finalInfo = null;
  state.finalNote = null;
  render();
  void loadFinal(created.id, ticket);
  return created;
}

async function loadFinal(boardId, ticket) {
  const path = `/api/v1/boards/${boardId}/final`;
  try {
    const response = await fetch(path, { headers: { Accept: "application/json" } });
    const body = await response.json();
    if (ticket !== state.lifetime || state.boardId !== boardId) return;
    if (response.status === 422) {
      state.finalNote = body.detail || "No conclusion.";
      render();
      return;
    }
    if (!response.ok) {
      state.finalNote = problemMessage(body, response.status);
      render();
      return;
    }
    state.finalInfo = body;
    state.finalNote = null;
    render();
  } catch {
    if (ticket === state.lifetime && state.boardId === boardId) {
      state.finalNote = "Could not reach /final.";
      render();
    }
  }
}

function cycleReady(info) {
  for (let offset = 0; offset < info.period; offset += 1) {
    if (!state.frames.has(info.firstOccurrenceGeneration + offset)) return false;
  }
  return true;
}

function nextIndex() {
  const info = state.finalInfo;
  if (!info) return state.generation + 1;
  if (info.terminationKind === "FIXED_POINT" || info.terminationKind === "EXTINCT") {
    if (state.generation >= info.firstOccurrenceGeneration) return null;
    return state.generation + 1;
  }
  if (info.terminationKind === "CYCLE" && cycleReady(info)) {
    const start = info.firstOccurrenceGeneration;
    const period = info.period;
    if (state.generation + 1 >= start + period) {
      return start + ((state.generation + 1 - start) % period);
    }
  }
  return state.generation + 1;
}

function stoppedMessage() {
  const info = state.finalInfo;
  if (!info) return "Paused.";
  if (info.terminationKind === "EXTINCT") {
    return `Extinct at generation ${info.firstOccurrenceGeneration}.`;
  }
  if (info.terminationKind === "FIXED_POINT") {
    return `Still life at generation ${info.firstOccurrenceGeneration}.`;
  }
  return `Looping a cycle of period ${info.period}.`;
}

async function advance(ticket) {
  const index = nextIndex();
  if (index === null) return false;
  if (state.frames.has(index)) {
    if (ticket !== state.lifetime) return false;
    noteCall(`cached generation ${index}`);
    showFrame(index, state.frames.get(index));
    if (state.finalInfo && state.finalInfo.terminationKind === "CYCLE") {
      setMessage(stoppedMessage());
    }
    return true;
  }
  const path = `/api/v1/boards/${state.boardId}/generations/${index}`;
  noteCall(`GET ${path}`);
  const body = await api(path);
  if (ticket !== state.lifetime || !state.boardId) return false;
  remember(body.generation, body.cells);
  showFrame(body.generation, body.cells);
  setMessage("");
  return true;
}

function queue() {
  window.clearTimeout(state.timer);
  state.timer = window.setTimeout(() => { void runFrame(); }, delay());
}

async function runFrame() {
  if (!state.playing || state.busy) return;
  const ticket = state.lifetime;
  state.busy = true;
  try {
    const moved = await advance(ticket);
    if (ticket !== state.lifetime) return;
    if (!moved) {
      state.playing = false;
      setMessage(stoppedMessage());
      render();
      return;
    }
    if (state.playing) queue();
  } catch (error) {
    if (ticket !== state.lifetime) return;
    state.playing = false;
    setMessage(error.message, true);
    render();
  } finally {
    state.busy = false;
  }
}

async function play() {
  if (state.playing || state.busy) return;
  setMessage("");
  if (!state.boardId) {
    state.busy = true;
    try {
      const created = await upload();
      if (!created) return;
    } catch (error) {
      setMessage(error.message, true);
      return;
    } finally {
      state.busy = false;
    }
  }
  if (nextIndex() === null) {
    setMessage(stoppedMessage());
    render();
    return;
  }
  state.playing = true;
  render();
  void runFrame();
}

function pause() {
  state.playing = false;
  window.clearTimeout(state.timer);
  setMessage("Paused.");
  render();
}

async function step() {
  state.playing = false;
  window.clearTimeout(state.timer);
  if (state.busy) {
    render();
    return;
  }
  setMessage("");
  if (!state.boardId) {
    try {
      const created = await upload();
      if (!created) return;
    } catch (error) {
      setMessage(error.message, true);
      return;
    }
  }
  const ticket = state.lifetime;
  state.busy = true;
  try {
    const moved = await advance(ticket);
    if (ticket !== state.lifetime) return;
    if (!moved) setMessage(stoppedMessage());
  } catch (error) {
    if (ticket !== state.lifetime) return;
    setMessage(error.message, true);
  } finally {
    state.busy = false;
  }
}

async function reset() {
  if (!state.boardId) return;
  pause();
  const ticket = state.lifetime;
  const path = `/api/v1/boards/${state.boardId}`;
  noteCall(`GET ${path}`);
  try {
    const body = await api(path);
    if (ticket !== state.lifetime) return;
    remember(0, body.cells);
    showFrame(body.generation, body.cells);
    setMessage("Back to the uploaded board.");
  } catch (error) {
    setMessage(error.message, true);
  }
}

async function jumpToFinal() {
  pause();
  if (!state.boardId) {
    setMessage("Upload a board first.", true);
    return;
  }
  const ticket = state.lifetime;
  const boardId = state.boardId;
  if (!state.finalInfo) {
    noteCall(`GET /api/v1/boards/${boardId}/final`);
    await loadFinal(boardId, ticket);
  }
  if (ticket !== state.lifetime || state.boardId !== boardId) return;
  if (!state.finalInfo) {
    setMessage(state.finalNote || "No conclusion.", true);
    return;
  }
  const info = state.finalInfo;
  showFrame(info.firstOccurrenceGeneration, info.cells);
  setMessage(stoppedMessage());
}

canvas.addEventListener("click", (event) => {
  if (state.busy) return;
  const cell = cellFromEvent(event);
  if (!cell) return;
  invalidate();
  state.boardId = null;
  state.frames = new Map();
  state.finalInfo = null;
  state.finalNote = null;
  state.generation = 0;
  state.grid[cell.row][cell.col] = !state.grid[cell.row][cell.col];
  noteCall("No request yet");
  render();
  setMessage("Draft changed. Play uploads it as a new board.");
});

presetSelect.addEventListener("change", () => applyPreset(presetSelect.value));

document.querySelector("#resize").addEventListener("click", () => {
  applyPreset(presetSelect.value);
});

document.querySelector("#play").addEventListener("click", () => { void play(); });
document.querySelector("#pause").addEventListener("click", pause);
document.querySelector("#step").addEventListener("click", () => { void step(); });
document.querySelector("#reset").addEventListener("click", () => { void reset(); });
document.querySelector("#final").addEventListener("click", () => { void jumpToFinal(); });

speedInput.addEventListener("input", () => {
  speedValue.textContent = `${delay()} ms`;
});

window.addEventListener("resize", draw);

applyPreset("blinker");
