package utrs;

/**
 * Something the server tells clients. Each event has two encodings: a single text line in the
 * same {@code Name(args)} style as commands (TCP clients), and a JSON object (web clients).
 */
public sealed interface Event {

    String toLine();

    String toJson();

    /** Reply to the connection that just identified itself as {@code user}. */
    record Welcome(String user, Coord pos) implements Event {
        public String toLine() { return "Welcome(" + user + ", " + pos + ")"; }
        public String toJson() { return "{\"type\":\"welcome\",\"user\":" + q(user) + ",\"pos\":" + pos.toJson() + "}"; }
    }

    /** One entry of the online-user roster, sent after Welcome and in reply to Users(). */
    record Present(String user, Coord pos) implements Event {
        public String toLine() { return "User(" + user + ", " + pos + ")"; }
        public String toJson() { return "{\"type\":\"user\",\"user\":" + q(user) + ",\"pos\":" + pos.toJson() + "}"; }
    }

    record Joined(String user, Coord pos) implements Event {
        public String toLine() { return "Joined(" + user + ", " + pos + ")"; }
        public String toJson() { return "{\"type\":\"joined\",\"user\":" + q(user) + ",\"pos\":" + pos.toJson() + "}"; }
    }

    record Left(String user) implements Event {
        public String toLine() { return "Left(" + user + ")"; }
        public String toJson() { return "{\"type\":\"left\",\"user\":" + q(user) + "}"; }
    }

    record Moved(String user, Coord from, Coord to) implements Event {
        public String toLine() { return "Moved(" + user + ", " + from + ", " + to + ")"; }
        public String toJson() {
            return "{\"type\":\"moved\",\"user\":" + q(user) + ",\"from\":" + from.toJson() + ",\"to\":" + to.toJson() + "}";
        }
    }

    record Message(String from, String target, String text) implements Event {
        public String toLine() { return "Message(" + from + ", " + target + ", " + quote(text) + ")"; }
        public String toJson() {
            return "{\"type\":\"message\",\"from\":" + q(from) + ",\"target\":" + q(target) + ",\"text\":" + q(text) + "}";
        }
    }

    record Info(String text) implements Event {
        public String toLine() { return "Info(" + quote(text) + ")"; }
        public String toJson() { return "{\"type\":\"info\",\"text\":" + q(text) + "}"; }
    }

    record Error(String text) implements Event {
        public String toLine() { return "Error(" + quote(text) + ")"; }
        public String toJson() { return "{\"type\":\"error\",\"text\":" + q(text) + "}"; }
    }

    /** Protocol string literal: double quotes with \" and \\ escaped. */
    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** JSON string literal. */
    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
