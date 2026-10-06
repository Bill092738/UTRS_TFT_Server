package utrs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * The original line-based TCP protocol (Java and GameMaker clients): one command per line in,
 * one event per line out, UTF-8.
 */
final class TcpGateway {
    private final GameServer game;
    private final ServerSocket serverSocket;

    TcpGateway(GameServer game, String host, int port) throws IOException {
        this.game = game;
        this.serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
    }

    void start() {
        Thread t = new Thread(this::acceptLoop, "tcp-accept");
        t.start();
        System.out.println("TCP clients:  " + serverSocket.getInetAddress().getHostAddress() + ":"
                + serverSocket.getLocalPort());
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(() -> serve(socket), "tcp-" + socket.getPort()).start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) System.err.println("Accept failed: " + e.getMessage());
            }
        }
    }

    private void serve(Socket socket) {
        TcpSession session = new TcpSession(socket);
        System.out.println("TCP client connected: " + session.describe());
        game.connect(session);
        Thread writer = new Thread(session::writeLoop, "tcp-out-" + socket.getPort());
        writer.start();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!session.isClosed() && (line = in.readLine()) != null) {
                game.handle(session, line);
            }
        } catch (IOException e) {
            if (!session.isClosed()) System.err.println("TCP client error: " + e.getMessage());
        } finally {
            session.close();
            game.disconnect(session);
            System.out.println("TCP client disconnected: " + session.describe());
        }
    }

    private static final class TcpSession extends Session {
        private final Socket socket;

        TcpSession(Socket socket) {
            this.socket = socket;
        }

        void writeLoop() {
            try {
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), false);
                while (!isClosed()) {
                    Event e = next(1000);
                    if (e == null) continue;
                    out.print(e.toLine());
                    out.print('\n');
                    out.flush();
                    if (out.checkError()) break;
                }
            } catch (IOException | InterruptedException ignored) {
                // fall through and close
            }
            close();
        }

        @Override
        protected void onClose() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        @Override
        public String describe() {
            return socket.getRemoteSocketAddress() + (user() != null ? " (" + user() + ")" : "");
        }
    }
}
