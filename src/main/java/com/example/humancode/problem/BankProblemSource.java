package com.example.humancode.problem;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The curated, hand-checked bank. Default, and what dev should use. */
@Component
@ConditionalOnProperty(name = "humancode.problems.source", havingValue = "bank", matchIfMissing = true)
public class BankProblemSource implements ProblemSource {

    private final ProblemBank bank;

    public BankProblemSource(ProblemBank bank) {
        this.bank = bank;
    }

    @Override
    public Problem next(String id) {
        return id == null || id.isBlank() ? bank.random() : bank.require(id);
    }

    @Override
    public String describe() {
        return "curated bank (" + bank.all().size() + " problems)";
    }
}
