package com.example.humancode.problem;

/**
 * Where a session's problem comes from.
 *
 * <p>Dev uses the curated bank so runs are repeatable and the tests are known
 * good. Production generates a fresh problem per session, which needs the API.
 * Selected by {@code humancode.problems.source}.
 */
public interface ProblemSource {

    /** @param id optional specific problem; ignored by sources that generate. */
    Problem next(String id);

    String describe();
}
