package com.backend.backend.service.agent;

import com.backend.backend.config.AppProperties;
import com.backend.backend.integration.ai.AiChatClient;
import com.backend.backend.integration.ai.AiRequest;
import com.backend.backend.integration.ai.AiToolRequest;
import com.backend.backend.integration.ai.AiToolTurn;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.CvSections;
import com.backend.backend.model.agent.AgentIntent;
import com.backend.backend.service.ai.AiJson;
import com.backend.backend.service.ai.JobParsingService;
import com.backend.backend.service.matching.CustomizationEngine;
import com.backend.backend.service.matching.MatchingEnginesTest;
import com.backend.backend.service.matching.ScoringEngine;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Wiring for agent tests: real tools/validator/engines, scripted AI. */
final class AgentTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final CvProfile CV = MatchingEnginesTest.CV;

    /** Replays a fixed sequence of model turns and records what the agent sent. */
    static final class ScriptedAi implements AiChatClient {
        final Deque<AiToolTurn> turns = new ArrayDeque<>();
        final List<AiToolRequest> requests = new ArrayList<>();
        String completion = "{\"positions\":[{\"title\":\".NET Backend Developer\",\"summary\":\"\",\"requirements\":"
                + "{\"technical\":[\"C#\",\".NET\",\"SQL Server\",\"REST APIs\"],\"optional\":[\"Docker\"],\"tools\":[\"Git\"]}}]}";

        ScriptedAi then(String content, AiToolTurn.Call... calls) {
            turns.add(new AiToolTurn(content, List.of(calls)));
            return this;
        }

        public AiToolTurn chat(AiToolRequest request) {
            requests.add(request);
            return turns.isEmpty() ? new AiToolTurn("Done.", List.of()) : turns.poll();
        }

        public String complete(AiRequest request) {
            return completion;
        }

        public boolean isConfigured() {
            return true;
        }

        public String providerName() {
            return "scripted";
        }

        public String model() {
            return "scripted";
        }
    }

    static AiToolTurn.Call call(String name, Map<String, Object> args) {
        return new AiToolTurn.Call("call-" + name, name, JSON.writeValueAsString(args));
    }

    static AppProperties props() {
        return new AppProperties(new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Ai(100, 24000, new AppProperties.Groq("k", "m", "low", "u", Duration.ofSeconds(1), Duration.ofSeconds(1), 0)),
                new AppProperties.RateLimit(5, Duration.ofMinutes(1)), new AppProperties.Limits(1024),
                new AppProperties.Latex(false, "tectonic", Duration.ofSeconds(5)));
    }

    static AgentOrchestrator orchestrator(ScriptedAi ai) {
        CustomizationEngine engine = new CustomizationEngine();
        AgentToolbox toolbox = new AgentToolbox(JSON, new ClaimValidator(),
                new JobParsingService(ai, new AiJson(JSON), props()), engine, new ScoringEngine());
        return new AgentOrchestrator(ai, toolbox, new IntentClassifier(), JSON);
    }

    static AgentState state(String... userMessages) {
        IntentClassifier intents = new IntentClassifier();
        return new AgentState(CV, CvSections.DEFAULT_ORDER, List.of(), null, List.of(userMessages),
                java.util.Arrays.stream(userMessages).filter(intents::isSelfStatement).toList(), Map.of(),
                new IntentClassifier().classify(userMessages[userMessages.length - 1]));
    }

    static AgentState state(AgentIntent intent, List<String> userMessages) {
        IntentClassifier intents = new IntentClassifier();
        return new AgentState(CV, CvSections.DEFAULT_ORDER, List.of(), null, userMessages,
                userMessages.stream().filter(intents::isSelfStatement).toList(), Map.of(), intent);
    }

    private AgentTestSupport() {
    }
}
