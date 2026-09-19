package com.example.humancode.report;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured shape the interviewer's closing verdict arrives in. Same
 * reasoning as {@link com.example.humancode.ai.Reaction}: never parse prose,
 * let the SDK derive a strict JSON schema from this record.
 */
@JsonClassDescription("The interviewer's closing verdict on the whole session, for the report card.")
public record GeneratedReport(

        @JsonPropertyDescription("""
                Your closing verdict on the whole session, two to three sentences. Judge the \
                approach and the process, not just whether it passed. Same voice as your live \
                reactions: dry, second person, no coaching, no solution language. Be specific \
                to what actually happened this session, not a generic verdict that would fit \
                any candidate.""")
        String verdict,

        @JsonPropertyDescription("""
                Two to four short barbed lines about specific moments from this session — a \
                slow start, a thrash, a late paste, a number on the clock. Each is one \
                sentence, 3 to 12 words, second person, same voice as your live reactions. \
                Never repeat a line already said live in this session.""")
        List<String> insults,

        @JsonPropertyDescription("""
                Zero to two begrudging compliments, only for things genuinely earned this \
                session. Each is one sentence, 3 to 12 words, second person, undercutting the \
                praise rather than giving it cleanly. An empty list is correct when nothing \
                here earned one.""")
        List<String> compliments) {
}
