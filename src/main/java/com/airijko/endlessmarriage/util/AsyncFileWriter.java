package com.airijko.endlessmarriage.util;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Debounced, off-thread file writer for small JSON/config persistence.
 *
 * <p>Callers serialize their data to a String <em>on their own thread</em> (cheap
 * for a few KB) and hand the immutable result here; the slow part — the disk write
 * (fsync + atomic rename) — is moved off the world tick thread onto a single
 * dedicated daemon I/O thread. This deliberately does NOT use
 * {@code HytaleServer.SCHEDULED_EXECUTOR} (a single thread shared with config-save
 * and the shutdown watchdog) so a slow disk can't stall the engine scheduler.
 *
 * <p>Writes are <b>coalesced per path</b>: rapid saves to the same file within the
 * debounce window collapse to only the last content, so a burst of mutations costs
 * one write, not N. {@link #delete} is ordered against writes on the same path
 * (last call wins).
 *
 * <p>Durability trade-off: a hard crash within the debounce window loses the last
 * unwritten change. {@link #flushAllNow()} MUST be called on clean shutdown to drain
 * everything synchronously. The window is intentionally short.
 *
 * <p>Snapshot rule: the String passed in is immutable, so there is no race with the
 * caller's live data. Never pass a reference to mutable state here.
 *
 * <p>NOTE: duplicated per-module (Rifts/Guilds/Leveling/Marriage/Chat) rather than
 * shared, since the cross-jar dependency on EL-Core is only compileOnly/soft.
 */
public final class AsyncFileWriter {

    private static final Logger LOG = Logger.getLogger(AsyncFileWriter.class.getName());
    private static final long DEBOUNCE_MS = 1_000L;

    /** Shared module-wide instance. */
    public static final AsyncFileWriter INSTANCE = new AsyncFileWriter("Endless-Marriage-FileWriter");

    private final ScheduledExecutorService exec;
    private final Object lock = new Object();
    private final Map<Path, Pending> pending = new HashMap<>();

    private static final class Pending {
        String content;          // content to write; ignored when isDelete
        boolean isDelete;
        ScheduledFuture<?> task;
    }

    private AsyncFileWriter(@Nonnull String threadName) {
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    /** Queue an atomic write of {@code content} to {@code file} (debounced). */
    public void write(@Nonnull Path file, @Nonnull String content) {
        schedule(file, content, false);
    }

    /** Queue a delete of {@code file} (debounced, ordered against pending writes). */
    public void delete(@Nonnull Path file) {
        schedule(file, null, true);
    }

    private void schedule(@Nonnull Path file, String content, boolean isDelete) {
        synchronized (lock) {
            Pending p = pending.computeIfAbsent(file, f -> new Pending());
            p.content = content;
            p.isDelete = isDelete;
            if (p.task != null) {
                p.task.cancel(false);
            }
            try {
                p.task = exec.schedule(() -> flush(file), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                // Executor already shut down — write inline so nothing is lost.
                pending.remove(file);
                applyNow(file, content, isDelete);
            }
        }
    }

    private void flush(@Nonnull Path file) {
        String content;
        boolean isDelete;
        synchronized (lock) {
            Pending p = pending.remove(file);
            if (p == null) {
                return;
            }
            content = p.content;
            isDelete = p.isDelete;
        }
        applyNow(file, content, isDelete); // disk I/O outside the lock
    }

    private void applyNow(@Nonnull Path file, String content, boolean isDelete) {
        try {
            if (isDelete) {
                Files.deleteIfExists(file);
                return;
            }
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "AsyncFileWriter failed for " + file, e);
        }
    }

    /** Synchronously drain every pending write/delete. Call on clean shutdown. */
    public void flushAllNow() {
        List<Object[]> work;
        synchronized (lock) {
            work = new ArrayList<>(pending.size());
            for (Map.Entry<Path, Pending> e : pending.entrySet()) {
                Pending p = e.getValue();
                if (p.task != null) {
                    p.task.cancel(false);
                }
                work.add(new Object[]{e.getKey(), p.content, p.isDelete});
            }
            pending.clear();
        }
        for (Object[] w : work) {
            applyNow((Path) w[0], (String) w[1], (Boolean) w[2]);
        }
    }
}
