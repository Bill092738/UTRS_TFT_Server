package utrs;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One connected client (a TCP socket or a browser's event stream).
 *
 * <p>Outgoing events go through a bounded queue drained by the transport's own thread, so a slow
 * or stuck client can never block the game loop; if it falls too far behind it is dropped.
 */
public abstract class Session {
    private static final int MAX_QUEUED = 1024;

    private final String id = UUID.randomUUID().toString();
    private final BlockingQueue<Event> outbox = new LinkedBlockingQueue<>(MAX_QUEUED);
    private volatile String user;
    private volatile boolean closed;

    public String id() {
        return id;
    }

    /** The user this connection speaks for, or null until it sends Join or its first command. */
    public String user() {
        return user;
    }

    void bind(String user) {
        this.user = user;
    }

    public boolean isClosed() {
        return closed;
    }

    public void send(Event e) {
        if (closed) return;
        if (!outbox.offer(e)) {
            System.err.println("Dropping slow client " + describe());
            close();
        }
    }

    /** Waits up to {@code timeoutMs} for the next outgoing event; null on timeout. */
    Event next(long timeoutMs) throws InterruptedException {
        return outbox.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public final void close() {
        if (closed) return;
        closed = true;
        onClose();
    }

    protected abstract void onClose();

    public abstract String describe();
}
