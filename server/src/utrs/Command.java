package utrs;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed client command of the form {@code Action(arg, arg, ...)}.
 *
 * <p>Arguments are bare words ({@code User1}, {@code all}), coordinates ({@code (3,-4)}) or
 * double-quoted strings ({@code "Hello, World!"}, with {@code \"} and {@code \\} escapes).
 * Commas and parentheses inside quotes and coordinates are handled properly, which the
 * original split-on-comma parser could not do.
 */
public record Command(String action, List<Object> args) {

    /** Marker type so quoted strings can be told apart from bare words. */
    public record Text(String value) {}

    public static Command parse(String line) {
        return new Parser(line).parse();
    }

    public void expectArgs(int n, String usage) {
        if (args.size() != n) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    public String word(int i, String what) {
        if (args.get(i) instanceof String s) return s;
        throw new IllegalArgumentException(what + " must be a plain word");
    }

    public Coord coord(int i, String what) {
        if (args.get(i) instanceof Coord c) return c;
        throw new IllegalArgumentException(what + " must be a coordinate like (x,y)");
    }

    /** A quoted string; a single bare word is accepted too for convenience. */
    public String text(int i, String what) {
        Object a = args.get(i);
        if (a instanceof Text t) return t.value();
        if (a instanceof String s) return s;
        throw new IllegalArgumentException(what + " must be a \"quoted string\"");
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        Command parse() {
            skipWs();
            int start = pos;
            while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
            if (pos == start) throw error("expected a command name like Move(...)");
            String action = s.substring(start, pos);
            skipWs();
            expect('(');
            List<Object> args = new ArrayList<>();
            skipWs();
            if (peek() == ')') {
                pos++;
            } else {
                while (true) {
                    skipWs();
                    args.add(arg());
                    skipWs();
                    char c = next();
                    if (c == ')') break;
                    if (c != ',') throw error("expected ',' or ')'");
                }
            }
            skipWs();
            if (pos != s.length()) throw error("unexpected text after ')'");
            return new Command(action, List.copyOf(args));
        }

        private Object arg() {
            char c = peek();
            if (c == '"') return new Text(quoted());
            if (c == '(') return coord();
            int start = pos;
            while (pos < s.length() && ",()\"".indexOf(s.charAt(pos)) < 0
                    && !Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
            if (pos == start) throw error("expected an argument");
            return s.substring(start, pos);
        }

        private String quoted() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c == '\\') c = next();
                sb.append(c);
            }
        }

        private Coord coord() {
            expect('(');
            int x = integer();
            skipWs();
            expect(',');
            int y = integer();
            skipWs();
            expect(')');
            return new Coord(x, y);
        }

        private int integer() {
            skipWs();
            int start = pos;
            if (peek() == '-' || peek() == '+') pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            try {
                return Integer.parseInt(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw error("expected an integer");
            }
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private char next() {
            if (pos >= s.length()) throw error("unexpected end of command");
            return s.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                pos--;
                throw error("expected '" + c + "'");
            }
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Bad command at column " + (pos + 1) + ": " + msg);
        }
    }
}
