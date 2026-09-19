package com.example.humancode.problem;

import java.util.Locale;
import java.util.Optional;

/** The runtime a candidate writes for: a browser app or a Python program. */
public enum ProblemRuntime {
    WEB("web", "javascript"),
    PYTHON("python", "python");

    private final String label;
    private final String sessionLanguage;

    ProblemRuntime(String label, String sessionLanguage) {
        this.label = label;
        this.sessionLanguage = sessionLanguage;
    }

    public String label() {
        return label;
    }

    /** Value persisted with the session and used by Monaco as the default language. */
    public String sessionLanguage() {
        return sessionLanguage;
    }

    /** Browser tasks may have HTML, CSS and JavaScript files; Python tasks have a .py file. */
    public boolean matches(Problem problem) {
        boolean hasPython = problem.files().stream()
                .anyMatch(file -> "python".equals(file.language()) || file.name().endsWith(".py"));
        return this == PYTHON ? hasPython : !hasPython;
    }

    /** Accepts API-friendly aliases while preserving the old JavaScript default. */
    public static Optional<ProblemRuntime> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "web", "html", "javascript", "js" -> Optional.of(WEB);
            case "python", "py" -> Optional.of(PYTHON);
            default -> Optional.empty();
        };
    }
}
