// UTRS web client. Talks to the Java server's HTTP gateway:
//   GET  /api/events            Server-Sent Events, one JSON event per message
//   POST /api/command?session=  one command line, same text protocol as the TCP clients
// By default the API is on the same origin; open index.html?server=http://host:8080 to point elsewhere.
"use strict";

const API = (new URLSearchParams(location.search).get("server") || "").replace(/\/$/, "");
// Cells across the visible board (odd, so the player sits in the middle); fewer on small screens.
const view = () => (board.clientWidth < 500 ? 13 : 21);
const MIN = -1000, MAX = 1000;
const USER_ID = /^[A-Za-z0-9_-]{1,32}$/;

const $ = (id) => document.getElementById(id);
const board = $("board");
const ctx = board.getContext("2d");

let sessionId = null;
let me = null;            // our user ID once the server welcomed us
let wantedUser = null;    // what we asked to join as (re-sent after reconnects)
let moveInFlight = false; // one move at a time, so we never send a stale "current position"
let moveTimer = null;
let hover = null;
const users = new Map();  // online user -> {x, y}

// ---------- server connection ----------

function connect() {
  const events = new EventSource(API + "/api/events");
  events.onmessage = (msg) => handle(JSON.parse(msg.data));
  events.onerror = () => {
    // EventSource reconnects by itself; a new session event will arrive when it does.
    sessionId = null;
    me = null;
    users.clear();
    setStatus(false, "Reconnecting…");
    render();
  };
}

async function send(line) {
  if (!sessionId) return;
  try {
    const res = await fetch(API + "/api/command?session=" + encodeURIComponent(sessionId), {
      method: "POST",
      headers: { "Content-Type": "text/plain;charset=utf-8" },
      body: line,
    });
    if (!res.ok) log("error", "Server rejected request (" + res.status + ")");
  } catch (e) {
    log("error", "Network error: " + e.message);
  }
}

function handle(ev) {
  switch (ev.type) {
    case "session":
      sessionId = ev.id;
      setStatus(true, "Connected");
      if (wantedUser) send(`Join(${wantedUser})`);
      break;
    case "welcome":
      me = ev.user;
      users.set(me, ev.pos);
      try { localStorage.setItem("utrs-user", me); } catch { /* storage may be blocked */ }
      setStatus(true, "Playing as " + me);
      $("message").disabled = false;
      document.querySelector("#chat-form button").disabled = false;
      log("system", `Joined as ${me} at ${fmt(ev.pos)}`);
      break;
    case "user":
      users.set(ev.user, ev.pos);
      break;
    case "joined":
      users.set(ev.user, ev.pos);
      log("system", `${ev.user} joined at ${fmt(ev.pos)}`);
      break;
    case "left":
      users.delete(ev.user);
      log("system", `${ev.user} left`);
      break;
    case "moved":
      users.set(ev.user, ev.to);
      if (ev.user === me) moveDone();
      break;
    case "message":
      logMessage(ev);
      break;
    case "info":
      log("info", ev.text);
      break;
    case "error":
      log("error", ev.text);
      if (moveInFlight) moveDone();
      break;
  }
  renderUsers();
  render();
}

// ---------- actions ----------

function moveTo(x, y) {
  if (!me || moveInFlight) return;
  const from = users.get(me);
  x = clamp(x); y = clamp(y);
  if (from.x === x && from.y === y) return;
  moveInFlight = true;
  moveTimer = setTimeout(moveDone, 3000); // never get stuck if a reply is lost
  send(`Move(${me}, ${fmt(from)}, ${fmt({ x, y })})`);
}

function moveDone() {
  moveInFlight = false;
  clearTimeout(moveTimer);
}

$("join-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const id = $("user-id").value.trim();
  if (!USER_ID.test(id)) return log("error", "User ID: 1-32 letters, digits, _ or -");
  if (me && me !== id) {
    // A connection speaks for one user; switching means starting a fresh session.
    location.search = new URLSearchParams({ ...Object.fromEntries(new URLSearchParams(location.search)), user: id });
    return;
  }
  wantedUser = id;
  send(`Join(${id})`);
});

$("chat-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const text = $("message").value.trim();
  if (!text || !me) return;
  const quoted = '"' + text.replace(/\\/g, "\\\\").replace(/"/g, '\\"') + '"';
  send(`Broadcast(${me}, ${$("target").value}, ${quoted})`);
  $("message").value = "";
});

document.addEventListener("keydown", (e) => {
  if (!me || e.target.matches("input, select, textarea") || e.ctrlKey || e.metaKey || e.altKey) return;
  const step = {
    ArrowUp: [0, -1], w: [0, -1], W: [0, -1],
    ArrowDown: [0, 1], s: [0, 1], S: [0, 1],
    ArrowLeft: [-1, 0], a: [-1, 0], A: [-1, 0],
    ArrowRight: [1, 0], d: [1, 0], D: [1, 0],
  }[e.key];
  if (!step) return;
  e.preventDefault();
  const p = users.get(me);
  moveTo(p.x + step[0], p.y + step[1]);
});

board.addEventListener("click", (e) => {
  const c = cellAt(e);
  if (c) moveTo(c.x, c.y);
});
board.addEventListener("mousemove", (e) => {
  hover = cellAt(e);
  $("hover-pos").textContent = hover ? "Cursor " + fmt(hover) : "";
  render();
});
board.addEventListener("mouseleave", () => {
  hover = null;
  $("hover-pos").textContent = "";
  render();
});

// ---------- drawing ----------

function centre() {
  return (me && users.get(me)) || { x: 0, y: 0 };
}

function cellAt(e) {
  const r = board.getBoundingClientRect();
  const n = view(), size = r.width / n;
  const c = centre(), half = (n - 1) / 2;
  const x = c.x - half + Math.floor((e.clientX - r.left) / size);
  const y = c.y - half + Math.floor((e.clientY - r.top) / size);
  return x >= MIN && x <= MAX && y >= MIN && y <= MAX ? { x, y } : null;
}

function render() {
  const css = getComputedStyle(document.documentElement);
  const color = (name) => css.getPropertyValue(name).trim();
  const dpr = window.devicePixelRatio || 1;
  const px = Math.round(board.clientWidth * dpr);
  if (board.width !== px || board.height !== px) board.width = board.height = px;
  const n = view(), size = px / n;
  const c = centre(), half = (n - 1) / 2;
  const toScreen = (v, origin) => (v - origin + half) * size;

  ctx.clearRect(0, 0, px, px);

  // Outside the world
  ctx.fillStyle = color("--grid");
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      const x = c.x - half + i, y = c.y - half + j;
      if (x < MIN || x > MAX || y < MIN || y > MAX) ctx.fillRect(i * size, j * size, size, size);
    }
  }

  // Grid lines, with the x=0 / y=0 axes a little stronger
  ctx.lineWidth = Math.max(1, dpr);
  for (let i = 0; i <= n; i++) {
    const wx = c.x - half + i, wy = c.y - half + i;
    ctx.strokeStyle = color("--grid");
    line(i * size, 0, i * size, px);
    line(0, i * size, px, i * size);
    ctx.strokeStyle = color("--axis");
    if (wx === 0) line((i + 0.5) * size, 0, (i + 0.5) * size, px);
    if (wy === 0) line(0, (i + 0.5) * size, px, (i + 0.5) * size);
  }

  if (hover) {
    ctx.strokeStyle = color("--accent");
    ctx.strokeRect(toScreen(hover.x, c.x) + 1, toScreen(hover.y, c.y) + 1, size - 2, size - 2);
  }

  // Players (several can share a cell; draw ours last so it is on top)
  const entries = [...users].sort(([a], [b]) => (a === me) - (b === me));
  ctx.textAlign = "center";
  ctx.textBaseline = "bottom";
  ctx.font = `${Math.round(11 * dpr)}px system-ui, sans-serif`;
  for (const [id, p] of entries) {
    if (Math.abs(p.x - c.x) > half || Math.abs(p.y - c.y) > half) continue;
    const cx = toScreen(p.x, c.x) + size / 2, cy = toScreen(p.y, c.y) + size / 2;
    ctx.fillStyle = id === me ? color("--accent") : color("--other");
    ctx.beginPath();
    ctx.arc(cx, cy, size * 0.32, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = color("--text");
    ctx.fillText(id, cx, cy - size * 0.36);
  }

  $("me-pos").textContent = me ? `${me} at ${fmt(users.get(me))}` : "Not joined";
}

function line(x1, y1, x2, y2) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
}

// ---------- side panel ----------

function renderUsers() {
  const list = $("users");
  list.replaceChildren();
  const ids = [...users.keys()].sort();
  for (const id of ids) {
    const li = document.createElement("li");
    if (id === me) li.className = "me";
    li.append(span(id + (id === me ? " (you)" : "")), span(fmt(users.get(id))));
    list.append(li);
  }
  $("online-count").textContent = ids.length ? `(${ids.length})` : "";

  const target = $("target"), chosen = target.value;
  target.replaceChildren(new Option("all", "all"));
  for (const id of ids) if (id !== me) target.append(new Option(id, id));
  target.value = ids.includes(chosen) ? chosen : "all";
}

function logMessage(ev) {
  const li = document.createElement("li");
  const from = span(ev.from, "from");
  li.append(from);
  if (ev.target !== "all") li.append(" ", span(ev.from === me ? `→ ${ev.target}` : "(direct)", "dm"));
  li.append(": " + ev.text);
  append(li);
}

function log(kind, text) {
  const li = document.createElement("li");
  li.className = kind;
  li.textContent = text;
  append(li);
}

function append(li) {
  const box = $("log");
  const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 20;
  box.append(li);
  while (box.children.length > 200) box.firstChild.remove();
  if (atBottom) box.scrollTop = box.scrollHeight;
}

function setStatus(online, text) {
  $("status").className = "status " + (online ? "online" : "offline");
  $("status").textContent = text;
}

function span(text, cls) {
  const s = document.createElement("span");
  s.textContent = text;
  if (cls) s.className = cls;
  return s;
}

const fmt = (p) => `(${p.x},${p.y})`;
const clamp = (v) => Math.max(MIN, Math.min(MAX, v));

// ---------- start ----------

let saved = null;
try { saved = localStorage.getItem("utrs-user"); } catch { /* storage may be blocked */ }
const initial = new URLSearchParams(location.search).get("user") || saved;
if (initial && USER_ID.test(initial)) {
  $("user-id").value = initial;
  wantedUser = initial;
}
window.addEventListener("resize", render);
matchMedia("(prefers-color-scheme: dark)").addEventListener("change", render);
render();
connect();
