package utrs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Lets browsers play, since they cannot open raw TCP sockets. Uses only the JDK:
 *
 * <ul>
 *   <li>{@code GET /api/events} - Server-Sent Events stream. The first event is
 *       {@code {"type":"session","id":...}}; every later one is an {@link Event} as JSON.
 *   <li>{@code POST /api/command?session=ID} - body is one command line in the same text protocol
 *       the TCP clients use, e.g. {@code Move(User1, (0,0), (1,1))}. Replies arrive on the stream.
 *   <li>{@code GET /api/state} - JSON snapshot of all known users.
 *   <li>everything else - static files from the web root (the frontend).
 * </ul>
 */
final class WebGateway {
    private static final long HEARTBEAT_MS = 15_000;
    private static final int MAX_BODY = 4096;
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "json", "application/json",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    private final GameServer game;
    private final Path webRoot;
    private final HttpServer http;
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();

    WebGateway(GameServer game, String host, int port, Path webRoot) throws IOException {
        this.game = game;
        this.webRoot = webRoot.toAbsolutePath().normalize();
        this.http = HttpServer.create(new InetSocketAddress(host, port), 0);
        // Each event stream holds a thread for its lifetime, so the default single thread won't do.
        http.setExecutor(Executors.newCachedThreadPool());
        http.createContext("/api/events", this::events);
        http.createContext("/api/command", this::command);
        http.createContext("/api/state", this::state);
        http.createContext("/", this::staticFile);
    }

    void start() {
        http.start();
        String host = http.getAddress().getAddress().getHostAddress();
        System.out.println("Web frontend: http://" + (host.equals("0.0.0.0") ? "localhost" : host) + ":"
                + http.getAddress().getPort() + "/  (serving " + webRoot + ")");
    }

    private void events(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("X-Accel-Buffering", "no"); // stop nginx from buffering the stream
        cors(ex);
        ex.sendResponseHeaders(200, 0);

        WebSession session = new WebSession(ex.getRemoteAddress().toString());
        sessions.put(session.id(), session);
        game.connect(session);
        try (OutputStream out = ex.getResponseBody()) {
            write(out, "retry: 2000\ndata: {\"type\":\"session\",\"id\":\"" + session.id() + "\"}\n\n");
            while (!session.isClosed()) {
                Event e = session.next(HEARTBEAT_MS);
                // The comment line doubles as a liveness probe: writing to a gone client throws.
                write(out, e == null ? ": ping\n\n" : "data: " + e.toJson() + "\n\n");
            }
        } catch (IOException | InterruptedException ignored) {
            // browser went away
        } finally {
            session.close();
            sessions.remove(session.id());
            game.disconnect(session);
        }
    }

    private void command(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        cors(ex);
        WebSession session = sessions.get(queryParam(ex.getRequestURI(), "session"));
        if (session == null) {
            reply(ex, 404, "application/json", "{\"error\":\"unknown session; reconnect to /api/events\"}");
            return;
        }
        byte[] body;
        try (InputStream in = ex.getRequestBody()) {
            body = in.readNBytes(MAX_BODY + 1);
        }
        if (body.length > MAX_BODY) {
            reply(ex, 413, "application/json", "{\"error\":\"command too long\"}");
            return;
        }
        for (String line : new String(body, StandardCharsets.UTF_8).split("\n")) {
            game.handle(session, line);
        }
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    private void state(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;
        cors(ex);
        reply(ex, 200, "application/json", game.stateJson());
    }

    private void staticFile(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;
        String path = ex.getRequestURI().getPath();
        if (path.endsWith("/")) path += "index.html";
        Path file = webRoot.resolve(path.substring(1)).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            reply(ex, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        String name = file.getFileName().toString();
        String ext = name.substring(name.lastIndexOf('.') + 1);
        ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        byte[] bytes = Files.readAllBytes(file);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static boolean method(HttpExchange ex, String expected) throws IOException {
        if (ex.getRequestMethod().equals(expected)) return true;
        ex.getResponseHeaders().set("Allow", expected);
        reply(ex, 405, "text/plain; charset=utf-8", "Method not allowed");
        return false;
    }

    /** API is usable from a frontend hosted elsewhere (plain text POSTs need no preflight). */
    private static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    }

    private static void reply(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void write(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String queryParam(URI uri, String name) {
        String q = uri.getRawQuery();
        if (q == null) return null;
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) return pair.substring(eq + 1);
        }
        return null;
    }

    private static final class WebSession extends Session {
        private final String remote;

        WebSession(String remote) {
            this.remote = remote;
        }

        @Override
        protected void onClose() {
            // the event loop in events() notices isClosed() within one heartbeat
        }

        @Override
        public String describe() {
            return "web " + remote + (user() != null ? " (" + user() + ")" : "");
        }
    }
}
