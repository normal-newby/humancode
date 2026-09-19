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
    public Problem next(String id, Difficulty difficulty) {
        return id == null || id.isBlank() ? bank.random(difficulty) : bank.require(id);
    }

    @Override
    public String describe() {
        return "curated bank (" + bank.all().size() + " problems)";
    }
}
