package utrs;

/** A grid position. The world spans -1000..+1000 on both axes with (0,0) at the centre. */
public record Coord(int x, int y) {
    public static final int MIN = -1000;
    public static final int MAX = 1000;
    public static final Coord ORIGIN = new Coord(0, 0);

    public boolean inBounds() {
        return x >= MIN && x <= MAX && y >= MIN && y <= MAX;
    }

    /** Parses the save-file form "x,y". Returns null if it is not a valid coordinate. */
    public static Coord parsePair(String s) {
        String[] parts = s.split(",");
        if (parts.length != 2) return null;
        try {
            return new Coord(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Protocol form, e.g. "(3,-4)". */
    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }

    public String toJson() {
        return "{\"x\":" + x + ",\"y\":" + y + "}";
    }
}
