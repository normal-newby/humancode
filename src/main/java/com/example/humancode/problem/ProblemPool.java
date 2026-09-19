package com.example.humancode.problem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.humancode.config.HumancodeProperties;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps a few model-written problems warm, so starting a session does not mean
 * watching a spinner.
 *
 * <p>Generation takes 35-45 seconds — writing tests that are actually correct is
 * real work. With {@code source=generated} that used to happen in front of the
 * begin button, so the first thing a candidate experienced was most of a minute
 * of dead screen. The pool moves the call off the critical path: one problem is
 * taken, one is generated in the background for whoever comes next.
 *
 * <p><b>The pool survives restarts.</b> It is written to
 * {@code humancode.problems.cache-file} on every change and read back at
 * startup. Without that, every restart began cold, which in practice meant
 * every restart began with a bank problem — and restarting is exactly what you
 * do while working on the thing. The file also makes warming free after the
 * first run: nothing is regenerated that was already paid for.
 *
 * <p>Cost is bounded: at most {@code poolSize} calls to fill, then one per
 * session taken. Nothing is generated speculatively beyond the target, so an
 * idle server that nobody visits costs nothing.
 */
@Component
@ConditionalOnProperty(name = "humancode.problems.source", havingValue = "generated")
@Slf4j
public class ProblemPool {

    /**
     * How long a session start will wait on a generation already running.
     *
     * <p>Short on purpose: a generation takes well over half a minute, so this
     * is not "wait for it", it is "take it if it is about to land". Anything
     * longer and the candidate is staring at a dead button again, which is the
     * problem this class exists to remove.
     */
    private static final long NEARLY_READY_MILLIS = 8_000;

    private final ProblemGenerator generator;
    private final ObjectMapper mapper;
    private final int target;
    private final Path cacheFile;

    private final BlockingQueue<Problem> warm = new LinkedBlockingQueue<>();
    /** Generations currently running, so a top-up cannot stampede. */
    private final AtomicInteger inFlight = new AtomicInteger();

    private final ExecutorService filler = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "problem-pool");
        thread.setDaemon(true);
        return thread;
    });

    public ProblemPool(ProblemGenerator generator, ObjectMapper mapper, HumancodeProperties props) {
        this.generator = generator;
        this.mapper = mapper;
        this.target = Math.max(0, props.problems().poolSize());
        this.cacheFile = StringUtils.hasText(props.problems().cacheFile())
                ? Path.of(props.problems().cacheFile())
                : null;
    }

    /** Loads what the last run left, then fills the gap. Never during startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (target == 0) {
            log.info("Problem pool disabled (pool-size=0); generation will block on session start");
            return;
        }

        restore();
        if (warm.isEmpty()) {
            log.info("Pool is empty; warming to {}. Sessions started in the next minute or so will"
                    + " fall back to a bank problem.", target);
        } else {
            log.info("Restored {} problem(s) from {}; topping up to {}", warm.size(), cacheFile, target);
        }
        topUp();
    }

    /** True while at least one generation is running. */
    public boolean busy() {
        return inFlight.get() > 0;
    }

    /**
     * A ready problem, if there is one.
     *
     * <p>When the pool is cold but a generation is already running — a genuinely
     * first run, typically — this gives that one a few seconds to land rather
     * than starting a second. Paying twice for the same problem is the obvious
     * way to make a pool more expensive than no pool at all.
     *
     * @return empty if nothing is warm and nothing landed in time
     */
    public Optional<Problem> take() {
        Problem ready = warm.poll();

        if (ready == null && busy()) {
            log.info("Pool is cold; giving the generation in flight {}ms to land", NEARLY_READY_MILLIS);
            try {
                ready = warm.poll(NEARLY_READY_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (ready != null) {
            // Persist the removal, or a restart hands out the same problem again.
            persist();
        }
        topUp();
        return Optional.ofNullable(ready);
    }

    /** Fills the gap between what is warm, what is coming, and the target. */
    private void topUp() {
        int missing = target - warm.size() - inFlight.get();
        for (int i = 0; i < missing; i++) {
            inFlight.incrementAndGet();
            filler.submit(() -> {
                try {
                    generator.generate().ifPresentOrElse(
                            problem -> {
                                warm.offer(problem);
                                persist();
                                log.info("Pool now holds {} problem(s); newest is '{}'",
                                        warm.size(), problem.title());
                            },
                            // Not fatal: the source falls back, and the next
                            // take() will try again. Loud, because a pool that
                            // silently never fills looks exactly like a pool
                            // that is working.
                            () -> log.warn("Pool top-up produced nothing usable"));
                } catch (RuntimeException e) {
                    log.warn("Pool top-up failed ({})", e.toString());
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        }
    }

    private void restore() {
        if (cacheFile == null || !Files.exists(cacheFile)) {
            return;
        }
        try {
            List<Problem> cached = mapper.readValue(Files.readString(cacheFile), new TypeReference<>() {
            });
            cached.stream().limit(target).forEach(warm::offer);
        } catch (IOException | RuntimeException e) {
            // A cache written by older code, or a half-written file. Not worth
            // failing startup over — the pool just refills.
            log.warn("Could not read the problem cache at {} ({}); starting cold", cacheFile, e.toString());
        }
    }

    /**
     * Writes the whole pool. Called from the filler thread and from take(), so
     * it snapshots rather than iterating the live queue.
     */
    private synchronized void persist() {
        if (cacheFile == null) {
            return;
        }
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(cacheFile, mapper.writeValueAsString(new ArrayList<>(warm)));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not write the problem cache to {} ({}); the pool will be cold after a restart",
                    cacheFile, e.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        filler.shutdownNow();
    }
}
