package com.example.humancode.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Personas are markdown files, not string constants, so tone can be edited and
 * reloaded without a recompile — and so swapping one swaps a whole prompt-cache
 * namespace cleanly.
 */
@Component
public class PersonaLibrary {

    private static final Logger log = LoggerFactory.getLogger(PersonaLibrary.class);
    private static final String LOCATION = "classpath:personas/*.md";

    private final Map<String, String> byId = new LinkedHashMap<>();

    @PostConstruct
    void load() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            String id = filename.replaceAll("\\.md$", "");
            try (InputStream in = resource.getInputStream()) {
                byId.put(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        if (byId.isEmpty()) {
            throw new IllegalStateException("No personas found at " + LOCATION);
        }
        log.info("Loaded {} personas: {}", byId.size(), byId.keySet());
    }

    public String require(String id) {
        String persona = byId.get(id);
        if (persona == null) {
            throw new IllegalArgumentException("Unknown persona: " + id + " (have " + byId.keySet() + ")");
        }
        return persona;
    }

    public List<String> ids() {
        return List.copyOf(byId.keySet());
    }
}
