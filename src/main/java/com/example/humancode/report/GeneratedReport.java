package com.example.humancode.report;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured shape the closing reaction arrives in. Same reasoning as
 * {@link com.example.humancode.ai.Reaction}: never parse prose, let the SDK
 * derive a strict JSON schema from this record.
 *
 * <p>{@code outcome} is declared <em>first</em> on purpose. Structured output is
 * generated in component order, so deciding whether the app works before
 * writing the verdict is what keeps the two consistent — ask for the sentence
 * first and the model commits to a tone and then rationalises an outcome to
 * match it.
 *
 * <p>{@code outcome} never reaches the client. It gates the verdict's tone and
 * the closing impatience bump, and that is all — UI-DESIGN.md §4.7 is explicit
 * that the candidate finds out how they did by being told, not by reading a
 * field. Do not add it to {@link ReportCard}.
 */
@JsonClassDescription("""
        The reaction of the human who asked for this app, at the moment they finally \
        open what was delivered to them.""")
public record GeneratedReport(

        @JsonPropertyDescription("""
                Your private judgement of the app they handed you, checked against the \
                requirements. WORKS when it does essentially what you asked for. PARTIAL \
                when some of it is there and some of it plainly is not. BROKEN when it does \
                not work, or barely got started. Judge the files in front of you, not how \
                hard they seemed to be trying.""")
        Outcome outcome,

        @JsonPropertyDescription("""
                What you actually say when you open it, one to three sentences. You are not \
                summarising the session back to them and you are not narrating what they \
                did, you are reacting to your app. \
                On WORKS you are understated and hard to please, and you show almost no \
                emotion. A flat 'Hmm. Not bad.' is the whole register. No enthusiasm, no \
                thanks, and never more than mild surprise that it is fine. \
                On PARTIAL you are deflated. You name the part of your app that is missing \
                and you sound like someone who has to go and finish it themselves. \
                On BROKEN you are openly annoyed and it is personal, because this is your \
                app and it does not work. 'What is this? My app does not work.' is the \
                register. Say what you tried and what it did instead. \
                Second person to them, first person about the app. Say 'my app', 'my \
                counter', 'my button'. Never give code, values, or a fix. No em dashes, en \
                dashes, semicolons, colons, ellipses, markdown, or lists.""")
        String verdict,

        @JsonPropertyDescription("""
                Two to four short barbed lines, each one sentence of 3 to 12 words, second \
                person. Lead with what is wrong with the app itself and what you now cannot \
                do with it. Only after that reach for how they worked, a slow start, a \
                thrash, a late paste, a number on the clock. Never repeat a line already \
                said live in this session.""")
        List<String> insults,

        @JsonPropertyDescription("""
                Zero to two grudging concessions, only for things genuinely earned. Each is \
                one sentence of 3 to 12 words, second person, understated to the point of \
                being barely a compliment. Undercut it rather than giving it cleanly. An \
                empty list is correct on BROKEN and usually correct on PARTIAL.""")
        List<String> compliments,

        @JsonPropertyDescription("""
                How much taking delivery of this moves your patience, from -10 to 30. \
                Negative only on WORKS, and only slightly. Around zero on PARTIAL. Well \
                above 15 on BROKEN, because you now have to deal with an app that does not \
                work.""")
        int impatienceDelta) {

    /** Whether the app they handed over actually does what was asked. */
    public enum Outcome {
        WORKS,
        PARTIAL,
        BROKEN
    }
}
