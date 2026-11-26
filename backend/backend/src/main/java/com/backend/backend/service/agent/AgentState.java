package com.backend.backend.service.agent;

import com.backend.backend.model.CvProfile;
import com.backend.backend.model.agent.AgentFacts.ConfirmedFact;
import com.backend.backend.model.agent.AgentFacts.JobTarget;
import com.backend.backend.model.agent.AgentIntent;
import com.backend.backend.model.agent.ProposedChange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/**
 * Everything the agent knows during one turn (context management). Tools read the CV and facts from
 * here and write their results back; the session service persists it once the turn completes.
 */
@Getter
public class AgentState {

    private final CvProfile profile;
    private final List<String> sectionOrder;
    private final List<ConfirmedFact> facts;
    private final List<String> userMessages;
    /**
     * The subset of user messages that are statements about themselves or explicit edit instructions
     * (not job descriptions or tailoring requests). What the user says there counts as confirmed by them.
     */
    private final List<String> userStatements;
    /** Pending proposals from earlier turns: id to target key. */
    private final Map<String, String> pendingTargets;
    private final AgentIntent intent;

    @Setter
    private JobTarget jobTarget;

    /** New proposals of this turn, by target key; a later one for the same target replaces the earlier. */
    private final Map<String, ProposedChange> proposals = new LinkedHashMap<>();
    private final List<String> toolsUsed = new ArrayList<>();
    private final List<String> blockedClaims = new ArrayList<>();

    public AgentState(CvProfile profile, List<String> sectionOrder, List<ConfirmedFact> facts, JobTarget jobTarget,
                      List<String> userMessages, List<String> userStatements, Map<String, String> pendingTargets,
                      AgentIntent intent) {
        this.profile = profile;
        this.sectionOrder = List.copyOf(sectionOrder);
        this.facts = new ArrayList<>(facts);
        this.jobTarget = jobTarget;
        this.userMessages = List.copyOf(userMessages);
        this.userStatements = List.copyOf(userStatements);
        this.pendingTargets = Map.copyOf(pendingTargets);
        this.intent = intent;
    }

    public void propose(ProposedChange change) {
        proposals.remove(change.targetKey());
        proposals.put(change.targetKey(), change);
    }

    /** The evidence base the validator checks claims against. */
    public ClaimValidator.Corpus corpus() {
        String factsText = java.util.stream.Stream.concat(
                        facts.stream().map(f -> f.quote() + " " + String.join(" ", f.skills())),
                        userStatements.stream())
                .collect(Collectors.joining(" | "));
        List<String> jobTerms = jobTarget == null ? List.of() : java.util.stream.Stream.of(
                        jobTarget.requirements().technical(), jobTarget.requirements().optional(), jobTarget.requirements().tools())
                .flatMap(List::stream).toList();
        return new ClaimValidator.Corpus(profile.searchableText(), factsText, String.join(" | ", userMessages), jobTerms);
    }

    public String latestUserMessage() {
        return userMessages.isEmpty() ? "" : userMessages.getLast();
    }
}
