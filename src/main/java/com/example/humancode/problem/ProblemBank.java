package com.example.humancode.problem;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;

import tools.jackson.databind.ObjectMapper;

/**
 * Loads the curated problem set off the classpath at startup.
 *
 * <p>Note the import: this is Jackson 3 ({@code tools.jackson}), which is what
 * Spring Boot 4 auto-configures. The OpenAI SDK independently pulls in Jackson 2
 * ({@code com.fasterxml.jackson}) for its own schema generation. Both live on
 * the classpath happily, but only the Jackson 3 mapper exists as a bean — see
 * CLAUDE.md §5.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProblemBank {

    private static final String LOCATION = "classpath:problems/*.json";

    private final ObjectMapper mapper;
    private final Map<String, Problem> byId = new LinkedHashMap<>();

    @PostConstruct
    void load() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        List<Problem> loaded = new ArrayList<>();
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                loaded.add(mapper.readValue(in, Problem.class));
            } catch (RuntimeException e) {
                // Jackson 3 throws unchecked, so a bad problem file surfaces here.
                throw new IllegalStateException("Malformed problem file: " + resource.getFilename(), e);
            }
        }
        loaded.sort(Comparator.comparing(Problem::id));
        loaded.forEach(p -> byId.put(p.id(), p));

        if (byId.isEmpty()) {
            throw new IllegalStateException("No problems found at " + LOCATION);
        }
        log.info("Loaded {} problems: {}", byId.size(), byId.keySet());
    }

    public Optional<Problem> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Problem require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown problem: " + id));
    }

    public List<Problem> all() {
        return List.copyOf(byId.values());
    }

    public Problem random() {
        List<Problem> all = all();
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }
}
