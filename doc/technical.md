# Protocol

The same text commands are used by every client. TCP clients send them one per line; the web
frontend POSTs them over HTTP. All text is UTF-8.

## Commands (client → server)

```
Action(arg, arg, ...)
```

- Action names are case-sensitive: `Join`, `Move`, `Broadcast`, `Users`, `Help`.
- An argument is one of:
  - a **word**: `User1`, `all`. A user ID is 1-32 of `A-Z a-z 0-9 _ -`, and cannot be `all`.
  - a **coordinate**: `(x,y)` with integers in -1000..1000. Spaces and negative numbers are fine: `( -3 , 4 )`.
  - a **quoted string**: `"Hello, (world)!"`. Commas and parentheses inside are fine; write `\"` for a quote and `\\` for a backslash.
- Whitespace around arguments is ignored.

| Command | Meaning |
| --- | --- |
| `Join(userID)` | Say who this connection is. New users start at (0,0); returning users get their saved position. |
| `Move(userID, (x,y), (x2,y2))` | Move from the current position to any in-bounds cell. The current position must match the server's record. |
| `Broadcast(userID, target, "text")` | `target` is `all` or an online user ID (a direct message, delivered to sender and target). Max 500 characters. |
| `Users()` | List online users and positions. |
| `Help()` | Show the command list. |

**Identity.** A connection is bound to the first user ID it uses, either through `Join` or through
the first `Move`/`Broadcast` (older clients never send `Join`). After that, commands naming any other
user are rejected. Several connections may be the same user, e.g. two browser tabs.

There is no authentication: anyone who can reach the server can pick any user ID. Keep the default
`--host 127.0.0.1` or run it on a trusted network.

## Events (server → client)

Over TCP, each event is one line in the same `Name(args)` style. On the web stream, each is a JSON
object.

| TCP line | JSON `type` | Sent to |
| --- | --- | --- |
| `Welcome(User1, (0,0))` | `welcome` `{user, pos}` | the connection that joined |
| `User(User2, (5,5))` | `user` `{user, pos}` | the joiner (roster) or whoever sent `Users()` |
| `Joined(User1, (0,0))` | `joined` `{user, pos}` | everyone else, when a user comes online |
| `Left(User1)` | `left` `{user}` | everyone, when a user's last connection closes |
| `Moved(User1, (0,0), (1,1))` | `moved` `{user, from, to}` | everyone |
| `Message(User1, all, "text")` | `message` `{from, target, text}` | everyone, or the sender and target for direct messages |
| `Info("text")` | `info` `{text}` | the requester |
| `Error("reason")` | `error` `{text}` | the requester |

`pos`, `from` and `to` are `{"x":0,"y":0}`.

## Web API

| Endpoint | |
| --- | --- |
| `GET /api/events` | Server-Sent Events. The first message is `{"type":"session","id":"..."}`, then events as above. A `: ping` comment is sent every 15 s. |
| `POST /api/command?session=ID` | Body: one or more command lines (`text/plain`). Returns `204`; replies arrive on the event stream. `404` if the session is unknown. |
| `GET /api/state` | `{"bounds":{"min":-1000,"max":1000},"users":[{"user","pos","online"}]}` for every known user. |
| `GET /*` | Static files from `--web-root`. |

API responses carry `Access-Control-Allow-Origin: *`, so a frontend hosted somewhere else can use the
API: open `index.html?server=http://host:8080`.

To write another client, such as a mobile app or a bot, open the event stream, keep the session id,
and POST commands with it.

## Persistence

`save.txt` holds one `userID=x,y` per line. It is written every 5 seconds when something changed, and
again on shutdown (Ctrl+C). It is replaced atomically, so a crash mid-write can't corrupt it. Lines from
the 3.0 format (`User1=Broadcast to all: ...`) are skipped on load.

## Threading

Each connection has a bounded outgoing queue drained by its own thread, so a slow client can never stall
the game. A client that falls more than 1024 events behind is disconnected. Game state changes are
serialised inside `GameServer`.
