package com.example.humancode.problem;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** The curated, hand-checked bank. Default, and what dev should use. */
@Component
@ConditionalOnProperty(name = "humancode.problems.source", havingValue = "bank", matchIfMissing = true)
@RequiredArgsConstructor
public class BankProblemSource implements ProblemSource {

    private final ProblemBank bank;

    @Override
    public Problem next(String id, Difficulty difficulty, ProblemType type, ProblemRuntime runtime) {
        if (id != null && !id.isBlank()) {
            Problem problem = bank.require(id);
            if (type != null && problem.type() != type) {
                throw new IllegalArgumentException("Problem '" + id + "' is " + problem.type().label()
                        + ", not " + type.label());
            }
            if (runtime != null && !runtime.matches(problem)) {
                throw new IllegalArgumentException("Problem '" + id + "' is not a " + runtime.label()
                        + " problem");
            }
            return problem;
        }
        return bank.random(difficulty, type, runtime);
    }

    @Override
    public String describe() {
        return "curated bank (" + bank.all().size() + " problems)";
    }
}
