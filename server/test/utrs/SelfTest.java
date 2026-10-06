package utrs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free checks for the parser and game rules. Run with ./run.sh test */
public class SelfTest {
    private static int failures;

    public static void main(String[] args) throws Exception {
        parser();
        game();
        persistence();
        if (failures > 0) {
            System.err.println(failures + " check(s) FAILED");
            System.exit(1);
        }
        System.out.println("All checks passed");
    }

    static void parser() {
        Command c = Command.parse("Move(User1, (0,0), (1,1))");
        check("Move".equals(c.action()), "action name");
        check(c.args().equals(List.of("User1", new Coord(0, 0), new Coord(1, 1))), "move args " + c.args());

        c = Command.parse("  Move( User1 , ( -5 , 7 ) ,(1000,-1000) )  ");
        check(c.args().equals(List.of("User1", new Coord(-5, 7), new Coord(1000, -1000))), "whitespace/negatives");

        c = Command.parse("Broadcast(User1, all, \"Hello, (World)! \\\"quoted\\\"\")");
        check(c.text(2, "msg").equals("Hello, (World)! \"quoted\""), "quoted text with commas/parens: " + c.args());

        check(Command.parse("Help()").args().isEmpty(), "no-arg command");
        for (String bad : List.of("", "Move", "Move(", "Move(a,", "Move((1,2)", "Move(a) extra", "Broadcast(a, b, \"open")) {
            try {
                Command.parse(bad);
                check(false, "should reject: " + bad);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    static void game() {
        GameServer game = new GameServer(Path.of("unused-save.txt"));
        Fake a = new Fake(), b = new Fake();
        game.connect(a);
        game.connect(b);

        game.handle(a, "Move(User1, (0,0), (1,1))"); // legacy client: no Join, binds implicitly
        check(a.has("Welcome(User1, (0,0))"), "implicit join welcome " + a.lines);
        check(a.has("Moved(User1, (0,0), (1,1))"), "move ok " + a.lines);
        check(b.has("Moved(User1, (0,0), (1,1))"), "move broadcast to others");

        game.handle(a, "Move(User1, (0,0), (2,2))");
        check(a.last().startsWith("Error(\"Position mismatch"), "stale position rejected: " + a.last());

        game.handle(a, "Move(User1, (1,1), (1001,0))");
        check(a.last().startsWith("Error(\"Destination"), "out of bounds rejected");

        game.handle(b, "Join(User2)");
        check(b.has("User(User1, (1,1))"), "roster on join " + b.lines);
        check(a.has("Joined(User2, (0,0))"), "others told about join");

        game.handle(b, "Move(User1, (1,1), (5,5))");
        check(b.last().startsWith("Error(\"This connection is User2"), "no impersonation: " + b.last());

        game.handle(a, "Broadcast(User1, all, \"Hello, World!\")");
        check(b.has("Message(User1, all, \"Hello, World!\")"), "broadcast with comma intact " + b.lines);

        Fake c = new Fake();
        game.connect(c);
        game.handle(c, "Join(User3)");
        c.lines.clear();
        game.handle(a, "Broadcast(User1, User2, \"psst\")");
        check(b.has("Message(User1, User2, \"psst\")") && c.lines.isEmpty(), "direct message only to target");

        game.handle(a, "Help()");
        check(a.last().startsWith("Info(\"Commands:"), "help goes back to the client");

        game.handle(a, "Dance()");
        check(a.last().startsWith("Error(\"Unknown command"), "unknown command reported");

        game.disconnect(b);
        check(a.has("Left(User2)"), "leave broadcast");
        check(game.stateJson().contains("{\"user\":\"User2\",\"pos\":{\"x\":0,\"y\":0},\"online\":false}"),
                "state json " + game.stateJson());
    }

    static void persistence() throws Exception {
        Path dir = Files.createTempDirectory("utrs-test");
        Path save = dir.resolve("save.txt");
        Files.writeString(save, "User1=Broadcast to all: hi\nUser2=3,-4\n");
        GameServer game = new GameServer(save);
        game.load();
        Fake a = new Fake();
        game.connect(a);
        game.handle(a, "Move(User2, (3,-4), (6,7))");
        check(a.has("Moved(User2, (3,-4), (6,7))"), "loaded position used " + a.lines);
        game.saveIfDirty();
        check(Files.readString(save).equals("User2=6,7" + System.lineSeparator()), "saved: " + Files.readString(save));
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.err.println("FAIL: " + what);
        }
    }

    static final class Fake extends Session {
        final List<String> lines = new ArrayList<>();

        @Override
        public void send(Event e) {
            lines.add(e.toLine());
        }

        boolean has(String line) {
            return lines.contains(line);
        }

        String last() {
            return lines.get(lines.size() - 1);
        }

        @Override
        protected void onClose() {}

        @Override
        public String describe() {
            return "fake";
        }
    }
}
