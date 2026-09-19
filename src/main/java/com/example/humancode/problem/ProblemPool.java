package com.example.humancode.problem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * <p><b>One queue per difficulty.</b> The candidate picks easy, medium or hard
 * before starting, and a pool holding three easy problems cannot answer a
 * request for a hard one — it would fall back to the bank every time and the
 * choice would look decorative. So {@code pool-size} is the target <em>per
 * difficulty</em>: at 1, three problems stay warm and a first run makes three
 * calls, once, because the cache survives restarts.
 *
 * <p>Cost is bounded: at most {@code poolSize} calls per difficulty to fill,
 * then one per session taken. Nothing is generated speculatively beyond the
 * target, so an idle server that nobody visits costs nothing.
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

    private final Map<Difficulty, BlockingQueue<Problem>> warm = new EnumMap<>(Difficulty.class);
    /** Generations running per difficulty, so a top-up cannot stampede. */
    private final Map<Difficulty, AtomicInteger> inFlight = new EnumMap<>(Difficulty.class);

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

        for (Difficulty difficulty : Difficulty.values()) {
            warm.put(difficulty, new LinkedBlockingQueue<>());
            inFlight.put(difficulty, new AtomicInteger());
        }
    }

    /** Loads what the last run left, then fills the gap. Never during startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (target == 0) {
            log.info("Problem pool disabled (pool-size=0); generation will block on session start");
            return;
        }

        restore();
        if (size() == 0) {
            log.info("Pool is empty; warming {} per difficulty. Sessions started in the next few"
                    + " minutes will fall back to a bank problem.", target);
        } else {
            log.info("Restored {} problem(s) from {} ({}); topping up to {} per difficulty",
                    size(), cacheFile, census(), target);
        }
        topUp();
    }

    /** True while a generation that could serve this request is running. */
    public boolean busy(Difficulty difficulty) {
        return queuesFor(difficulty).stream().anyMatch(level -> inFlight.get(level).get() > 0);
    }

    private int size() {
        return warm.values().stream().mapToInt(BlockingQueue::size).sum();
    }

    /** "easy 1, medium 1, hard 0" for the log, so a thin pool is visible. */
    private String census() {
        List<String> parts = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            parts.add(difficulty.label() + " " + warm.get(difficulty).size());
        }
        return String.join(", ", parts);
    }

    /**
     * Which queues to draw from, in order. A null difficulty means the caller
     * does not care, so any of them will do.
     */
    private List<Difficulty> queuesFor(Difficulty difficulty) {
        return difficulty == null ? List.of(Difficulty.values()) : List.of(difficulty);
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
    public Optional<Problem> take(Difficulty difficulty) {
        Problem ready = null;
        for (Difficulty level : queuesFor(difficulty)) {
            ready = warm.get(level).poll();
            if (ready != null) {
                break;
            }
        }

        if (ready == null && busy(difficulty)) {
            log.info("No warm {} problem; giving the generation in flight {}ms to land",
                    difficulty == null ? "any" : difficulty.label(), NEARLY_READY_MILLIS);
            for (Difficulty level : queuesFor(difficulty)) {
                try {
                    ready = warm.get(level).poll(NEARLY_READY_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (ready != null) {
                    break;
                }
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
        for (Difficulty difficulty : Difficulty.values()) {
            int missing = target - warm.get(difficulty).size() - inFlight.get(difficulty).get();
            for (int i = 0; i < missing; i++) {
                inFlight.get(difficulty).incrementAndGet();
                filler.submit(() -> {
                    try {
                        generator.generate(difficulty).ifPresentOrElse(
                                problem -> {
                                    warm.get(difficulty).offer(problem);
                                    persist();
                                    log.info("Pool now holds {} ({}); newest is \"{}\"",
                                            size(), census(), problem.title());
                                },
                                // Not fatal: the source falls back, and the next
                                // take() tries again. Loud, because a pool that
                                // silently never fills looks exactly like a pool
                                // that is working.
                                () -> log.warn("Pool top-up for {} produced nothing usable",
                                        difficulty.label()));
                    } catch (RuntimeException e) {
                        log.warn("Pool top-up for {} failed ({})", difficulty.label(), e.toString());
                    } finally {
                        inFlight.get(difficulty).decrementAndGet();
                    }
                });
            }
        }
    }

    private void restore() {
        if (cacheFile == null || !Files.exists(cacheFile)) {
            return;
        }
        try {
            List<Problem> cached = mapper.readValue(Files.readString(cacheFile), new TypeReference<>() {
            });
            int skipped = 0;
            for (Problem problem : cached) {
                // A cache written against an older Problem shape deserializes
                // without throwing — the fields that no longer exist are simply
                // dropped and the ones that did not exist then come back empty.
                // Left unchecked that hands a candidate a session with no files
                // in it, which looks like a UI bug rather than a stale cache.
                if (problem.files().isEmpty()) {
                    skipped++;
                    continue;
                }
                // Bucketed by what the problem says it is. A cache written
                // before difficulties existed, or under a different target,
                // lands where it belongs and the top-up sorts out the rest.
                Difficulty.parse(problem.difficulty())
                        .filter(level -> warm.get(level).size() < target)
                        .ifPresent(level -> warm.get(level).offer(problem));
            }
            if (skipped > 0) {
                log.warn("Discarded {} cached problem(s) with no files — the cache predates the"
                        + " current problem shape; they will be regenerated", skipped);
            }
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
            List<Problem> everything = new ArrayList<>();
            warm.values().forEach(everything::addAll);
            Files.writeString(cacheFile, mapper.writeValueAsString(everything));
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
