package com.backend.backend.service.agent;

import com.backend.backend.dto.AgentDto;
import com.backend.backend.dto.PageResponse;
import com.backend.backend.entity.AgentMessage;
import com.backend.backend.entity.AgentProposal;
import com.backend.backend.entity.AgentSession;
import com.backend.backend.entity.Cv;
import com.backend.backend.exception.ApiException;
import com.backend.backend.mapper.CvMapper;
import com.backend.backend.model.ActivityType;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.CvSections;
import com.backend.backend.model.Experience;
import com.backend.backend.model.ExportFormat;
import com.backend.backend.model.ProjectItem;
import com.backend.backend.model.agent.AgentFacts.ConfirmedFact;
import com.backend.backend.model.agent.AgentFacts.JobTarget;
import com.backend.backend.model.agent.AgentFacts.TurnMeta;
import com.backend.backend.model.agent.AgentIntent;
import com.backend.backend.model.agent.ProposalStatus;
import com.backend.backend.model.agent.ProposedChange;
import com.backend.backend.repository.AgentMessageRepository;
import com.backend.backend.repository.AgentProposalRepository;
import com.backend.backend.repository.AgentSessionRepository;
import com.backend.backend.repository.CvRepository;
import com.backend.backend.service.ActivityService;
import com.backend.backend.service.export.CvDocumentFactory;
import com.backend.backend.service.export.CvExportService;
import com.backend.backend.util.JsonCodec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

/**
 * Persists agent sessions and enforces the approval workflow: the agent proposes, the user accepts,
 * rejects, edits or regenerates; accepted changes go into the session's working copy, and only
 * "Save to CV" writes them to the CV itself.
 */
@Service
public class AgentSessionService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<ConfirmedFact>> FACTS = new TypeReference<>() {
    };

    public record ExportResult(byte[] content, String mimeType, String fileName) {
    }

    private final AgentSessionRepository sessions;
    private final AgentMessageRepository messages;
    private final AgentProposalRepository proposals;
    private final CvRepository cvs;
    private final CvMapper cvMapper;
    private final JsonCodec json;
    private final AgentOrchestrator orchestrator;
    private final IntentClassifier intents;
    private final ChangeApplier applier;
    private final CvDocumentFactory documents;
    private final CvExportService exporter;
    private final ActivityService activity;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public AgentSessionService(AgentSessionRepository sessions, AgentMessageRepository messages, AgentProposalRepository proposals,
                               CvRepository cvs, CvMapper cvMapper, JsonCodec json, AgentOrchestrator orchestrator,
                               IntentClassifier intents, ChangeApplier applier, CvDocumentFactory documents,
                               CvExportService exporter, ActivityService activity, TransactionTemplate tx) {
        this.sessions = sessions;
        this.messages = messages;
        this.proposals = proposals;
        this.cvs = cvs;
        this.cvMapper = cvMapper;
        this.json = json;
        this.orchestrator = orchestrator;
        this.intents = intents;
        this.applier = applier;
        this.documents = documents;
        this.exporter = exporter;
        this.activity = activity;
        this.tx = tx;
        this.readTx = new TransactionTemplate(tx.getTransactionManager());
        this.readTx.setReadOnly(true);
    }

    // ------------------------------------------------------------------ sessions

    @Transactional(readOnly = true)
    public PageResponse<AgentDto.SessionSummary> list(UUID cvId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.badRequest("Invalid page or size.");
        var pageable = PageRequest.of(page, size);
        var result = cvId == null ? sessions.findAllByOrderByUpdatedAtDesc(pageable) : sessions.findByCvIdOrderByUpdatedAtDesc(cvId, pageable);
        return PageResponse.of(result, this::summary);
    }

    @Transactional
    public AgentDto.Workspace create(UUID cvId) {
        Cv cv = cvs.findById(cvId).orElseThrow(() -> ApiException.notFound("CV not found."));
        CvProfile profile = cvMapper.profileOf(cv);
        AgentSession session = new AgentSession();
        session.setCv(cv);
        session.setTitle(cv.getFileName());
        session.setBaseProfile(json.write(profile));
        session.setWorkingProfile(json.write(profile));
        session.setSectionOrder(json.write(CvSections.DEFAULT_ORDER));
        session.setConfirmedFacts(json.write(List.of()));
        AgentSession saved = sessions.saveAndFlush(session);
        messages.save(new AgentMessage(saved, AgentMessage.Role.AGENT, greeting(cv, profile), null));
        activity.log(ActivityType.AGENT_SESSION_STARTED, "Started an AI Agent session for " + cv.getFileName());
        return workspace(saved);
    }

    @Transactional(readOnly = true)
    public AgentDto.Workspace get(UUID id) {
        return workspace(find(id));
    }

    @Transactional
    public void delete(UUID id) {
        sessions.delete(find(id));
    }

    // ------------------------------------------------------------------ conversation

    /** The AI loop runs between two short transactions; the session's @Version rejects concurrent edits. */
    public AgentDto.Workspace send(UUID id, String text) {
        String value = text.strip();
        if (!orchestrator.available()) {
            throw ApiException.unprocessable("The AI agent is not configured on this server (GROQ_API_KEY is missing).");
        }
        return runTurn(id, value, null);
    }

    public AgentDto.Workspace regenerate(UUID proposalId) {
        record Target(UUID sessionId, String label) {
        }
        Target target = readTx.execute(status -> {
            AgentProposal p = findProposal(proposalId);
            if (p.getStatus() != ProposalStatus.PENDING) throw ApiException.conflict("Only pending changes can be regenerated.");
            return new Target(p.getSession().getId(), changeOf(p).label());
        });
        String instruction = "Regenerate the proposed change \"" + target.label() + "\" with different, improved wording. "
                + "Keep exactly the same facts.";
        return runTurn(target.sessionId(), instruction, proposalId);
    }

    private AgentDto.Workspace runTurn(UUID id, String userText, UUID regenerating) {
        record Prepared(AgentState state, String language, List<AgentOrchestrator.HistoryItem> history,
                        List<Map<String, String>> pending, long version) {
        }
        AgentIntent intent = intents.classify(userText);
        Prepared prepared = readTx.execute(status -> {
            AgentSession s = find(id);
            List<AgentMessage> history = messages.findBySessionIdOrderByIdAsc(id);
            List<String> userMessages = new ArrayList<>(history.stream()
                    .filter(m -> m.getRole() == AgentMessage.Role.USER).map(AgentMessage::getText).toList());
            userMessages.add(userText);
            List<AgentProposal> open = proposals.findBySessionIdAndStatusOrderByCreatedAtAsc(id, ProposalStatus.PENDING);
            Map<String, String> pendingTargets = new LinkedHashMap<>();
            List<Map<String, String>> pending = new ArrayList<>();
            for (AgentProposal p : open) {
                ProposedChange c = changeOf(p);
                pendingTargets.put(p.getId().toString(), c.targetKey());
                pending.add(Map.of("id", p.getId().toString(), "label", c.label(), "operation", c.operation().value()));
            }
            List<String> statements = userMessages.stream().filter(intents::isSelfStatement).toList();
            AgentState state = new AgentState(working(s), order(s), facts(s), job(s), userMessages, statements, pendingTargets, intent);
            return new Prepared(state, s.getCv().getLanguage(), history.stream().map(m -> new AgentOrchestrator.HistoryItem(
                    m.getRole() == AgentMessage.Role.USER, m.getText())).toList(), pending, s.getVersion());
        });

        AgentOrchestrator.Outcome outcome = orchestrator.run(prepared.state(), prepared.language(), prepared.history(), userText,
                prepared.pending());

        return tx.execute(status -> {
            AgentSession s = find(id);
            if (s.getVersion() != prepared.version()) {
                throw ApiException.conflict("This session changed while the agent was working. Reload and try again.");
            }
            AgentState state = prepared.state();
            s.setConfirmedFacts(json.write(state.getFacts()));
            if (state.getJobTarget() != null) {
                s.setJobTarget(json.write(state.getJobTarget()));
                if (!s.getTitle().startsWith("Tailoring")) s.setTitle(truncate("Tailoring for " + state.getJobTarget().title(), 200));
            }
            // A new proposal for the same item replaces the pending one (and a regenerated one is always replaced).
            Map<String, String> newTargets = outcome.proposals().stream().collect(Collectors.toMap(
                    ProposedChange::targetKey, ProposedChange::targetKey, (a, b) -> a));
            for (AgentProposal old : proposals.findBySessionIdAndStatusOrderByCreatedAtAsc(id, ProposalStatus.PENDING)) {
                boolean replaced = newTargets.containsKey(changeOf(old).targetKey())
                        || (old.getId().equals(regenerating) && !outcome.proposals().isEmpty());
                if (replaced) decide(old, ProposalStatus.SUPERSEDED);
            }
            List<String> createdIds = new ArrayList<>();
            for (ProposedChange change : outcome.proposals()) {
                AgentProposal p = new AgentProposal();
                p.setSession(s);
                p.setStatus(ProposalStatus.PENDING);
                p.setOperation(change.operation());
                p.setChange(json.write(change));
                createdIds.add(proposals.saveAndFlush(p).getId().toString());
            }
            messages.save(new AgentMessage(s, AgentMessage.Role.USER, userText, null));
            TurnMeta meta = new TurnMeta(state.getIntent(), List.copyOf(state.getToolsUsed()),
                    List.copyOf(state.getBlockedClaims()), createdIds);
            messages.save(new AgentMessage(s, AgentMessage.Role.AGENT, outcome.reply(), json.write(meta)));
            s.setUpdatedAt(Instant.now());
            sessions.saveAndFlush(s);
            return workspace(s);
        });
    }

    // ------------------------------------------------------------------ decisions

    /** A stale change is marked STALE and reported (409); that status must survive the error, hence noRollbackFor. */
    @Transactional(noRollbackFor = ApiException.class)
    public AgentDto.Workspace accept(UUID proposalId, AgentDto.AcceptRequest edit) {
        AgentProposal p = findProposal(proposalId);
        AgentSession s = p.getSession();
        if (p.getStatus() != ProposalStatus.PENDING) throw ApiException.conflict("This change was already decided.");
        ProposedChange change = changeOf(p);
        if (edit != null && hasEdit(edit)) {
            change = withEdit(change, edit);
            // The user wrote this text themselves: it is user-confirmed content.
            List<ConfirmedFact> facts = new ArrayList<>(facts(s));
            facts.add(new ConfirmedFact(change.updatedText(), List.of(), Instant.now()));
            s.setConfirmedFacts(json.write(facts));
            p.setChange(json.write(change));
        }
        applyAccepted(s, p, change);
        sessions.saveAndFlush(s);
        return workspace(s);
    }

    @Transactional
    public AgentDto.Workspace acceptAll(UUID sessionId) {
        AgentSession s = find(sessionId);
        for (AgentProposal p : proposals.findBySessionIdAndStatusOrderByCreatedAtAsc(sessionId, ProposalStatus.PENDING)) {
            try {
                applyAccepted(s, p, changeOf(p));
            } catch (ApiException e) {
                decide(p, ProposalStatus.STALE);
            }
        }
        sessions.saveAndFlush(s);
        return workspace(s);
    }

    @Transactional
    public AgentDto.Workspace reject(UUID proposalId) {
        AgentProposal p = findProposal(proposalId);
        if (p.getStatus() != ProposalStatus.PENDING) throw ApiException.conflict("This change was already decided.");
        decide(p, ProposalStatus.REJECTED);
        return workspace(p.getSession());
    }

    /** Writes the working copy into the CV. The only operation that modifies the CV itself. */
    @Transactional
    public AgentDto.Workspace saveToCv(UUID sessionId) {
        AgentSession s = find(sessionId);
        Cv cv = s.getCv();
        cvMapper.setProfile(cv, working(s).withItemIds());
        cv.setExtractionNote(null);
        cvs.save(cv);
        s.setSavedAt(Instant.now());
        sessions.saveAndFlush(s);
        activity.log(ActivityType.AGENT_CHANGES_SAVED, "Saved AI Agent changes to " + cv.getFileName());
        return workspace(s);
    }

    /** Discards every accepted change in this session (the CV itself is untouched unless it was saved). */
    @Transactional
    public AgentDto.Workspace reset(UUID sessionId) {
        AgentSession s = find(sessionId);
        s.setWorkingProfile(s.getBaseProfile());
        s.setSectionOrder(json.write(CvSections.DEFAULT_ORDER));
        for (AgentProposal p : proposals.findBySessionIdAndStatusOrderByCreatedAtAsc(sessionId, ProposalStatus.ACCEPTED)) {
            decide(p, ProposalStatus.STALE);
        }
        for (AgentProposal p : proposals.findBySessionIdAndStatusOrderByCreatedAtAsc(sessionId, ProposalStatus.PENDING)) {
            decide(p, ProposalStatus.STALE);
        }
        sessions.saveAndFlush(s);
        return workspace(s);
    }

    @Transactional(readOnly = true)
    public ExportResult export(UUID sessionId, ExportFormat format) {
        AgentSession s = find(sessionId);
        CvExportService.ExportedFile file = exporter.render(documents.fromProfile(working(s), order(s)), format);
        String base = s.getCv().getFileName().replaceFirst("\\.[^.]+$", "").replaceAll("[^\\p{L}\\p{N}_-]+", "_");
        return new ExportResult(file.content(), file.mimeType(), (base.isEmpty() ? "CV" : base) + "_AI_Agent." + file.extension());
    }

    // ------------------------------------------------------------------ internals

    private void applyAccepted(AgentSession s, AgentProposal p, ProposedChange change) {
        ChangeApplier.Applied applied;
        try {
            applied = applier.apply(working(s), order(s), change);
        } catch (ChangeApplier.StaleException e) {
            decide(p, ProposalStatus.STALE);
            throw ApiException.conflict(e.getMessage());
        }
        s.setWorkingProfile(json.write(applied.profile()));
        s.setSectionOrder(json.write(applied.sectionOrder()));
        decide(p, ProposalStatus.ACCEPTED);
    }

    private static boolean hasEdit(AgentDto.AcceptRequest e) {
        return (e.text() != null && !e.text().isBlank()) || (e.list() != null && !e.list().isEmpty())
                || (e.description() != null && !e.description().isBlank());
    }

    /** Applies the user's manual edit to the proposal's "after" value. */
    static ProposedChange withEdit(ProposedChange c, AgentDto.AcceptRequest e) {
        List<String> list = e.list() == null ? null : e.list().stream().map(String::strip).filter(x -> !x.isEmpty()).toList();
        return switch (c.operation()) {
            case UPDATE_HEADLINE, UPDATE_SUMMARY -> {
                String text = require(e.text(), "text");
                yield copy(c, text, text, c.list(), c.experience(), c.project());
            }
            case UPDATE_SKILLS -> {
                if (list == null || list.isEmpty()) throw ApiException.badRequest("Provide the skills list.");
                yield copy(c, String.join(", ", list), null, list, null, null);
            }
            case ADD_EXPERIENCE, UPDATE_EXPERIENCE -> {
                if (list == null || list.isEmpty()) throw ApiException.badRequest("Provide the bullet points.");
                Experience x = c.experience();
                Experience edited = new Experience(x.id(), x.role(), x.company(), x.location(), x.start(), x.end(), list, x.technologies());
                yield copy(c, ChangeDescriber.experience(edited), null, null, edited, null);
            }
            case ADD_PROJECT, UPDATE_PROJECT -> {
                ProjectItem x = c.project();
                ProjectItem edited = new ProjectItem(x.id(), x.name(), require(e.description(), "description"), x.technologies(), x.link());
                yield copy(c, ChangeDescriber.project(edited), null, null, null, edited);
            }
            default -> throw ApiException.badRequest("This change cannot be edited; accept or reject it.");
        };
    }

    private static ProposedChange copy(ProposedChange c, String updatedText, String text, List<String> list,
                                       Experience experience, ProjectItem project) {
        return new ProposedChange(c.operation(), c.section(), c.targetId(), c.label(), c.originalText(), updatedText,
                c.reason() + " (edited by you)", c.jobRequirements(), c.keywordsAdded(), c.keywordsRemoved(), c.evidence(),
                text, list, experience, project, c.beforeText(), c.beforeList(), c.beforeExperience(), c.beforeProject());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw ApiException.badRequest("Provide the " + field + ".");
        return value.strip();
    }

    private void decide(AgentProposal p, ProposalStatus status) {
        p.setStatus(status);
        p.setDecidedAt(Instant.now());
        proposals.save(p);
    }

    private AgentDto.Workspace workspace(AgentSession s) {
        List<AgentProposal> all = proposals.findBySessionIdOrderByCreatedAtAsc(s.getId());
        CvProfile working = working(s);
        CvProfile current = cvMapper.profileOf(s.getCv());
        List<String> order = order(s);
        return new AgentDto.Workspace(
                summary(s),
                messages.findBySessionIdOrderByIdAsc(s.getId()).stream().map(this::message).toList(),
                all.stream().map(p -> new AgentDto.Proposal(p.getId(), p.getStatus(), changeOf(p), p.getCreatedAt(), p.getDecidedAt())).toList(),
                working,
                json.read(s.getBaseProfile(), CvProfile.class),
                order,
                job(s),
                facts(s),
                new AgentDto.Stats(count(all, ProposalStatus.PENDING), count(all, ProposalStatus.ACCEPTED), count(all, ProposalStatus.REJECTED)),
                !working.equals(current),
                orchestrator.available());
    }

    private AgentDto.SessionSummary summary(AgentSession s) {
        return new AgentDto.SessionSummary(s.getId(), s.getCv().getId(), s.getCv().getFileName(), s.getTitle(), s.getUpdatedAt(), s.getSavedAt());
    }

    private AgentDto.Message message(AgentMessage m) {
        TurnMeta meta = m.getMeta() == null ? null : json.read(m.getMeta(), TurnMeta.class);
        return new AgentDto.Message(String.valueOf(m.getId()), m.getRole() == AgentMessage.Role.USER ? "user" : "agent",
                m.getText(), m.getCreatedAt(), meta);
    }

    private static long count(List<AgentProposal> all, ProposalStatus status) {
        return all.stream().filter(p -> p.getStatus() == status).count();
    }

    private ProposedChange changeOf(AgentProposal p) {
        return json.read(p.getChange(), ProposedChange.class);
    }

    private CvProfile working(AgentSession s) {
        return json.read(s.getWorkingProfile(), CvProfile.class);
    }

    private List<String> order(AgentSession s) {
        return CvSections.normalize(json.read(s.getSectionOrder(), STRINGS));
    }

    private List<ConfirmedFact> facts(AgentSession s) {
        return json.read(s.getConfirmedFacts(), FACTS);
    }

    private JobTarget job(AgentSession s) {
        return s.getJobTarget() == null ? null : json.read(s.getJobTarget(), JobTarget.class);
    }

    private AgentSession find(UUID id) {
        return sessions.findById(id).orElseThrow(() -> ApiException.notFound("Agent session not found."));
    }

    private AgentProposal findProposal(UUID id) {
        return proposals.findById(id).orElseThrow(() -> ApiException.notFound("Proposed change not found."));
    }

    private static String greeting(Cv cv, CvProfile p) {
        StringBuilder text = new StringBuilder("I've read ").append(cv.getFileName()).append(": ")
                .append(p.experiences().size()).append(" experience").append(p.experiences().size() == 1 ? "" : "s").append(", ")
                .append(p.projects().size()).append(" project").append(p.projects().size() == 1 ? "" : "s").append(" and ")
                .append(p.skills().size()).append(" skills.");
        if (p.summary().isBlank()) text.append(" Your CV has no professional summary yet; I can write one from your experience.");
        text.append(" Tell me what to do, for example: \"Tailor my CV for this job\" (then paste the posting), "
                + "\"Rewrite my summary to emphasize backend development\" or \"Optimize my CV for ATS\". "
                + "You can also just tell me facts (\"I used .NET at MajraDEEP\") or instructions (\"replace Spring with .NET\"): "
                + "I apply what you say, but I never invent facts you didn't give me. Every change waits for your approval.");
        return text.toString();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
