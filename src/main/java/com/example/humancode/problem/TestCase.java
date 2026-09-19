package com.example.humancode.problem;

import java.util.List;

/**
 * One assertion, shipped to the browser and executed there.
 *
 * <p>{@code args} are spread into the candidate's entry point; {@code expected}
 * is compared against the return value.
 *
 * <p>These <em>are</em> visible to the candidate — the runner is a Web Worker in
 * their own browser, so it cannot execute a test it has not been given. That is
 * an accepted trade: inputs and outputs do not hand over the algorithm, and the
 * reference solution and rubric are still stripped server-side. Genuinely hidden
 * tests would require a server-side runner, which is a different project.
 */
public record TestCase(List<Object> args, Object expected) {
}
