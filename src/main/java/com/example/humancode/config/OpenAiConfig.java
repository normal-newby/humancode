package com.example.humancode.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

@Configuration
public class OpenAiConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenAiConfig.class);

    /**
     * One client for the whole application — it is thread-safe and holds a
     * connection pool, so building one per request would be wasteful.
     */
    @Bean
    public OpenAiClientHolder openAiClientHolder(HumancodeProperties props) {
        String key = props.ai().apiKey();
        if (!StringUtils.hasText(key)) {
            log.warn("""
                    OPENAI_API_KEY is not set. HumanCode will run with a canned \
                    interviewer: triggers fire and the UI works, but every line is \
                    pre-written. Export the key and restart for the real thing.""");
            return new OpenAiClientHolder(null);
        }

        Duration timeout = props.ai().requestTimeout();
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(key)
                .timeout(timeout == null ? Duration.ofSeconds(30) : timeout)
                .build();

        log.info("OpenAI client ready (model={}, quipModel={})", props.ai().model(), props.ai().quipModel());
        return new OpenAiClientHolder(client);
    }
}
