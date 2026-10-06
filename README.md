# UTRS_TFT_Server

A small multiplayer grid server: players have positions on a 2001×2001 grid and can chat. One
Java server talks to two kinds of client at the same time:

- **TCP clients** (the Java console client, GameMaker) speak a plain-text line protocol on port 23363.
- **Browsers** use the web frontend on port 8080. Browsers can't open raw TCP sockets, so the
  server also exposes the same game over HTTP (Server-Sent Events + POST).

Both kinds see each other: a move made in GameMaker shows up live in the browser and vice versa.
No libraries or build tools are needed, only JDK 17 or newer.

## Quick start

```bash
./run.sh            # Windows: run.bat
```

Then open <http://localhost:8080/>, pick a user ID, and move with WASD / arrow keys or by clicking
a cell. Open a second tab with another ID to see multiplayer.

To connect the Java console client in another terminal:

```bash
javac -d clients/java/out clients/java/demoCli.java
java -cp clients/java/out demoCli User1           # [userID] [host] [port]
```

Then type `/Move (3,-2)`, `/Broadcast all "hello"`, `/Users`, `/Help`.

### Options

```
./run.sh --host 0.0.0.0      # accept connections from other machines (default 127.0.0.1)
         --tcp-port 23363    # 0 disables the TCP gateway
         --http-port 8080    # 0 disables the web gateway
         --web-root web      # where the frontend files live
         --save save.txt     # position save file
./run.sh test                # run the self-test
```

## Project structure

```
server/src/utrs/
  App.java          entry point and options
  GameServer.java   game rules, shared by both gateways; persistence
  Command.java      command parser: Action(word, (x,y), "quoted, text")
  Event.java        server -> client events (text line or JSON)
  Session.java      one connected client with a non-blocking outbox
  TcpGateway.java   line protocol for Java / GameMaker clients
  WebGateway.java   HTTP: /api/events (SSE), /api/command, /api/state, static files
server/test/utrs/SelfTest.java
web/                browser frontend (plain HTML/CSS/JS, no build step)
clients/java/       console client
clients/gamemaker/  GameMaker client sketch (networking left as a guide)
archive/            earlier abandoned attempts (Python JSON server, Patchwire GML scripts)
doc/technical.md    protocol reference
```

## Protocol summary

Full details are in [doc/technical.md](doc/technical.md).

| Client sends | Server replies / broadcasts |
| --- | --- |
| `Join(User1)` | `Welcome(User1, (0,0))` + one `User(id, (x,y))` per online user; others get `Joined(User1, (0,0))` |
| `Move(User1, (0,0), (1,1))` | everyone gets `Moved(User1, (0,0), (1,1))` |
| `Broadcast(User1, all, "Hi, all")` | everyone gets `Message(User1, all, "Hi, all")` |
| `Broadcast(User1, User2, "psst")` | User1 and User2 get `Message(User1, User2, "psst")` |
| `Users()` | `User(id, (x,y))` per online user |
| `Help()` | `Info("Commands: ...")` |
| anything invalid | `Error("reason")` to the sender only |

When a client disconnects, everyone gets `Left(User1)`. Positions are saved to `save.txt` every
few seconds and on shutdown, and restored on start.

## Changes from 3.0

Version 3.0 compiled but most commands did not work:

- `Move` always failed: the parser cut the command at the first `)`, so `(0,0)` was split apart.
  New users were also never put on the grid, so even a correctly parsed move failed the
  position check.
- `Broadcast` truncated messages at the first comma, and the `target` was ignored.
- `Help()` printed on the server console instead of replying to the client.
- `save.txt` was never written: the shutdown hook was registered after the endless accept loop.
  Positions weren't part of the saved data anyway.
- The clients rejected negative coordinates, and the Java client updated its own position even when
  the server refused the move, which put it out of sync for good.

The 2001×2001 `String` grid (about 4 million cells) is replaced by a map of user → position.
Several players may share a cell.
