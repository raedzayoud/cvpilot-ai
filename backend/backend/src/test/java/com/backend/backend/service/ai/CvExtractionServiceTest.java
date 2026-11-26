package com.backend.backend.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.backend.backend.config.AppProperties;
import com.backend.backend.integration.ai.AiChatClient;
import com.backend.backend.integration.ai.AiException;
import com.backend.backend.integration.ai.AiRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CvExtractionServiceTest {

    private static final String TEX = """
            \\documentclass{article}
            \\begin{document}
            \\section*{Sami Ben Ali}
            Developer -- sami@example.com -- +216 20 111 222
            \\section*{Skills}
            Java, Spring Boot, Angular % comment
            \\end{document}
            """;

    private static AppProperties props() {
        return new AppProperties(new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Ai(10, 24000, new AppProperties.Groq("k", "m", "low", "u",
                        Duration.ofSeconds(1), Duration.ofSeconds(1), 0)),
                new AppProperties.RateLimit(5, Duration.ofMinutes(1)), new AppProperties.Limits(1024),
                new AppProperties.Latex(false, "tectonic", Duration.ofSeconds(5)));
    }

    private static AiChatClient client(String reply, AtomicReference<AiRequest> seen) {
        return new AiChatClient() {
            public String complete(AiRequest request) {
                seen.set(request);
                if (reply == null) throw new AiException(AiException.Kind.UNAVAILABLE, "down");
                return reply;
            }
            public boolean isConfigured() { return true; }
            public String providerName() { return "fake"; }
            public String model() { return "fake"; }
        };
    }

    @Test
    void fallbackReadsLatexWithoutCommandsWhenAiFails() {
        var service = new CvExtractionService(client(null, new AtomicReference<>()), new AiJson(JsonMapper.builder().build()), props());
        var result = service.extract(TEX);

        assertThat(result.note()).contains("Automatic extraction failed");
        assertThat(result.profile().personalInfo().fullName()).isEqualTo("Sami Ben Ali");
        assertThat(result.profile().personalInfo().email()).isEqualTo("sami@example.com");
        assertThat(result.profile().skills()).contains("Java", "Spring Boot", "Angular");
    }

    @Test
    void parsesModelJsonEvenWithCodeFencesAndAssignsItemIds() {
        String reply = """
                ```json
                {"language":"french","profile":{"personalInfo":{"fullName":"Sami"},"skills":["Java"],
                 "experiences":[{"role":"Dev","company":"Acme","bullets":["Built APIs"]}]}}
                ```""";
        AtomicReference<AiRequest> seen = new AtomicReference<>();
        var service = new CvExtractionService(client(reply, seen), new AiJson(JsonMapper.builder().build()), props());
        var result = service.extract("Sami\nDev at Acme");

        assertThat(result.note()).isNull();
        assertThat(result.language()).isEqualTo("French");
        assertThat(result.profile().experiences().getFirst().id()).isEqualTo("exp-1");
        assertThat(seen.get().jsonMode()).isTrue();
        assertThat(seen.get().messages().getFirst().role()).isEqualTo("system");
    }
}
