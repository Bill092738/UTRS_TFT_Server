package utrs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Starts the game server with both front doors: the TCP line protocol for the Java/GameMaker
 * clients and an HTTP gateway for the web frontend.
 *
 * <pre>
 * java -cp server/out utrs.App [--host 127.0.0.1] [--tcp-port 23363] [--http-port 8080]
 *                              [--web-root web] [--save save.txt]
 * </pre>
 * Use --host 0.0.0.0 to accept connections from other machines. A port of 0 disables that gateway.
 */
public class App {
    public static void main(String[] args) throws IOException {
        String host = "127.0.0.1";
        int tcpPort = 23363;
        int httpPort = 8080;
        Path webRoot = Path.of("web");
        Path saveFile = Path.of("save.txt");

        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (flag.equals("-h") || flag.equals("--help")) {
                System.out.println("Options: --host ADDR  --tcp-port N  --http-port N  --web-root DIR  --save FILE");
                return;
            }
            if (i + 1 >= args.length) usage("Missing value for " + flag);
            String value = args[++i];
            switch (flag) {
                case "--host" -> host = value;
                case "--tcp-port" -> tcpPort = port(value);
                case "--http-port" -> httpPort = port(value);
                case "--web-root" -> webRoot = Path.of(value);
                case "--save" -> saveFile = Path.of(value);
                default -> usage("Unknown option " + flag);
            }
        }

        GameServer game = new GameServer(saveFile);
        game.load();

        // Save every few seconds when something changed, and once more on Ctrl+C.
        ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "autosave");
            t.setDaemon(true);
            return t;
        });
        saver.scheduleWithFixedDelay(game::saveIfDirty, 5, 5, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            game.saveIfDirty();
            System.out.println("Saved, bye.");
        }));

        if (tcpPort > 0) new TcpGateway(game, host, tcpPort).start();
        if (httpPort > 0) new WebGateway(game, host, httpPort, webRoot).start();
    }

    private static int port(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            usage("Not a port number: " + s);
            return -1;
        }
    }

    private static void usage(String problem) {
        System.err.println(problem + " (try --help)");
        System.exit(2);
    }
}
