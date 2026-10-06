package utrs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The game itself, independent of how clients connect. TCP and web sessions both feed raw
 * command lines into {@link #handle} and receive {@link Event}s back.
 */
public final class GameServer {
    static final String HELP = "Commands: Join(userID) | Move(userID, (x,y), (x,y)) | "
            + "Broadcast(userID, all|userID, \"message\") | Users() | Help()";

    private static final Pattern USER_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final int MAX_MESSAGE = 500;

    private final Path saveFile;
    /** Last known position of every user ever seen. Guarded by {@code this}. */
    private final Map<String, Coord> positions = new LinkedHashMap<>();
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private boolean dirty;

    public GameServer(Path saveFile) {
        this.saveFile = saveFile;
    }

    public void connect(Session s) {
        sessions.add(s);
    }

    public synchronized void disconnect(Session s) {
        if (!sessions.remove(s)) return;
        String user = s.user();
        if (user != null && !isOnline(user)) {
            broadcast(new Event.Left(user));
        }
    }

    /** Parses and executes one command line from {@code s}. Problems are reported back as Error events. */
    public void handle(Session s, String line) {
        line = line.replace("\0", "").strip(); // GameMaker buffers may carry a trailing NUL
        if (line.isEmpty()) return;
        try {
            execute(s, Command.parse(line));
        } catch (IllegalArgumentException e) {
            s.send(new Event.Error(e.getMessage()));
        }
    }

    private synchronized void execute(Session s, Command c) {
        switch (c.action()) {
            case "Help" -> s.send(new Event.Info(HELP));
            case "Join" -> {
                c.expectArgs(1, "Join(userID)");
                String user = c.word(0, "userID");
                if (user.equals(s.user())) {
                    s.send(new Event.Welcome(user, positions.get(user)));
                } else {
                    actAs(s, user);
                }
            }
            case "Users" -> {
                c.expectArgs(0, "Users()");
                sendRoster(s);
            }
            case "Move" -> {
                c.expectArgs(3, "Move(userID, (x,y), (x,y))");
                String user = actAs(s, c.word(0, "userID"));
                Coord from = c.coord(1, "current position");
                Coord to = c.coord(2, "destination");
                if (!to.inBounds()) {
                    throw new IllegalArgumentException("Destination " + to + " is outside "
                            + Coord.MIN + ".." + Coord.MAX);
                }
                Coord actual = positions.get(user);
                if (!from.equals(actual)) {
                    throw new IllegalArgumentException("Position mismatch: " + user + " is at " + actual
                            + ", not " + from);
                }
                positions.put(user, to);
                dirty = true;
                broadcast(new Event.Moved(user, from, to));
            }
            case "Broadcast" -> {
                c.expectArgs(3, "Broadcast(userID, all|userID, \"message\")");
                String user = actAs(s, c.word(0, "userID"));
                String target = c.word(1, "target");
                String text = c.text(2, "message").replaceAll("[\\r\\n]+", " ").strip();
                if (text.isEmpty()) throw new IllegalArgumentException("Message is empty");
                if (text.length() > MAX_MESSAGE) {
                    throw new IllegalArgumentException("Message is longer than " + MAX_MESSAGE + " characters");
                }
                Event msg = new Event.Message(user, target, text);
                if (target.equalsIgnoreCase("all")) {
                    broadcast(msg);
                } else if (!isOnline(target)) {
                    throw new IllegalArgumentException("User " + target + " is not online");
                } else {
                    for (Session other : sessions) {
                        if (target.equals(other.user()) || user.equals(other.user())) other.send(msg);
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown command " + c.action() + ". " + HELP);
        }
    }

    /**
     * Makes sure {@code s} speaks for {@code user}. The first user named on a connection binds it
     * (so old clients that never send Join keep working); after that it cannot impersonate others.
     */
    private String actAs(Session s, String user) {
        if (s.user() != null) {
            if (!s.user().equals(user)) {
                throw new IllegalArgumentException("This connection is " + s.user() + ", not " + user);
            }
            return user;
        }
        if (!USER_ID.matcher(user).matches() || user.equalsIgnoreCase("all")) {
            throw new IllegalArgumentException("Invalid userID '" + user
                    + "': use 1-32 letters, digits, '_' or '-' (and not 'all')");
        }
        boolean wasOnline = isOnline(user);
        s.bind(user);
        if (positions.putIfAbsent(user, Coord.ORIGIN) == null) dirty = true;
        Coord pos = positions.get(user);
        s.send(new Event.Welcome(user, pos));
        sendRoster(s);
        if (!wasOnline) {
            Event joined = new Event.Joined(user, pos);
            for (Session other : sessions) {
                if (other != s) other.send(joined);
            }
        }
        return user;
    }

    private void sendRoster(Session s) {
        for (String user : onlineUsers()) {
            s.send(new Event.Present(user, positions.get(user)));
        }
    }

    private boolean isOnline(String user) {
        for (Session s : sessions) {
            if (user.equals(s.user())) return true;
        }
        return false;
    }

    private Set<String> onlineUsers() {
        Set<String> online = new java.util.TreeSet<>();
        for (Session s : sessions) {
            if (s.user() != null) online.add(s.user());
        }
        return online;
    }

    private void broadcast(Event e) {
        for (Session s : sessions) s.send(e);
    }

    /** Snapshot for GET /api/state. */
    public synchronized String stateJson() {
        Set<String> online = onlineUsers();
        StringBuilder sb = new StringBuilder("{\"bounds\":{\"min\":" + Coord.MIN + ",\"max\":" + Coord.MAX
                + "},\"users\":[");
        boolean first = true;
        for (Map.Entry<String, Coord> e : positions.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"user\":").append(Event.q(e.getKey()))
                    .append(",\"pos\":").append(e.getValue().toJson())
                    .append(",\"online\":").append(online.contains(e.getKey())).append('}');
        }
        return sb.append("]}").toString();
    }

    // ---- persistence: save.txt holds one "userID=x,y" per line ----

    public synchronized void load() {
        if (!Files.exists(saveFile)) {
            System.out.println("No " + saveFile + " yet, starting with an empty world");
            return;
        }
        try {
            for (String line : Files.readAllLines(saveFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("=", 2);
                Coord pos = parts.length == 2 ? Coord.parsePair(parts[1]) : null;
                if (pos != null && pos.inBounds() && USER_ID.matcher(parts[0]).matches()) {
                    positions.put(parts[0], pos);
                } else if (!line.isBlank()) {
                    // e.g. the old "User1=Broadcast to all: hi" entries from version 3.0
                    System.out.println("Skipping unrecognised save line: " + line);
                }
            }
            System.out.println("Loaded " + positions.size() + " user(s) from " + saveFile);
        } catch (IOException e) {
            System.err.println("Could not read " + saveFile + ": " + e.getMessage());
        }
    }

    /** Writes save.txt if anything changed since the last save. */
    public void saveIfDirty() {
        List<Map.Entry<String, Coord>> snapshot;
        synchronized (this) {
            if (!dirty) return;
            dirty = false;
            snapshot = List.copyOf(positions.entrySet());
        }
        try {
            Path tmp = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, Coord> e : snapshot) {
                    Coord p = e.getValue();
                    w.write(e.getKey() + "=" + p.x() + "," + p.y());
                    w.newLine();
                }
            }
            Files.move(tmp, saveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            synchronized (this) {
                dirty = true;
            }
            System.err.println("Could not write " + saveFile + ": " + e.getMessage());
        }
    }
}
