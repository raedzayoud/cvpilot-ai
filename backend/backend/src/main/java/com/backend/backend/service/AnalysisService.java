package com.backend.backend.service;

import com.backend.backend.dto.AnalysisDto;
import com.backend.backend.dto.PageResponse;
import com.backend.backend.entity.Analysis;
import com.backend.backend.entity.ChatMessage;
import com.backend.backend.entity.Cv;
import com.backend.backend.entity.JobPosting;
import com.backend.backend.exception.ApiException;
import com.backend.backend.mapper.AnalysisMapper;
import com.backend.backend.mapper.CvMapper;
import com.backend.backend.mapper.JobMapper;
import com.backend.backend.model.ActivityType;
import com.backend.backend.model.AnalysisInsights;
import com.backend.backend.model.ChatRole;
import com.backend.backend.model.ChatState;
import com.backend.backend.model.Customization;
import com.backend.backend.model.CvDecision;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.DecisionAction;
import com.backend.backend.model.ExportFormat;
import com.backend.backend.model.JobPosition;
import com.backend.backend.model.SectionProposal;
import com.backend.backend.repository.AnalysisRepository;
import com.backend.backend.repository.ChatMessageRepository;
import com.backend.backend.repository.CvRepository;
import com.backend.backend.repository.JobPostingRepository;
import com.backend.backend.service.ai.CoachChatService;
import com.backend.backend.service.export.CvExportService;
import com.backend.backend.service.export.CvDocumentFactory;
import com.backend.backend.service.matching.CustomizationEngine;
import com.backend.backend.service.matching.ScoringEngine;
import com.backend.backend.service.matching.TextMatcher;
import com.backend.backend.util.PageRequests;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalysisService {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "createdAt", "jobTitle", "jobTitle", "score", "score");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    public record ExportResult(byte[] content, String mimeType, String fileName) {
    }

    /** Everything needed to reason about one analysis, loaded in a single read. */
    private record Ctx(Analysis analysis, CvProfile profile, JobPosting job, JobPosition target,
                       Customization customization, ChatState state) {
    }

    private final AnalysisRepository analyses;
    private final CvRepository cvs;
    private final CvDocumentFactory documents;
    private final JobPostingRepository jobs;
    private final ChatMessageRepository messages;
    private final AnalysisMapper mapper;
    private final CvMapper cvMapper;
    private final JobMapper jobMapper;
    private final CustomizationEngine engine;
    private final ScoringEngine scoring;
    private final CoachChatService coach;
    private final CvExportService exporter;
    private final ActivityService activity;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public AnalysisService(AnalysisRepository analyses, CvRepository cvs, CvDocumentFactory documents, JobPostingRepository jobs,
                           ChatMessageRepository messages, AnalysisMapper mapper, CvMapper cvMapper, JobMapper jobMapper,
                           CustomizationEngine engine, ScoringEngine scoring, CoachChatService coach, CvExportService exporter,
                           ActivityService activity, TransactionTemplate tx) {
        this.analyses = analyses;
        this.cvs = cvs;
        this.documents = documents;
        this.jobs = jobs;
        this.messages = messages;
        this.mapper = mapper;
        this.cvMapper = cvMapper;
        this.jobMapper = jobMapper;
        this.engine = engine;
        this.scoring = scoring;
        this.coach = coach;
        this.exporter = exporter;
        this.activity = activity;
        this.tx = tx;
        this.readTx = new TransactionTemplate(tx.getTransactionManager());
        this.readTx.setReadOnly(true);
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional(readOnly = true)
    public PageResponse<AnalysisDto.Summary> list(String search, String status, int page, int size, String sort) {
        boolean exportedOnly = "exported".equalsIgnoreCase(status);
        boolean draftsOnly = "draft".equalsIgnoreCase(status);
        if (status != null && !status.isBlank() && !exportedOnly && !draftsOnly && !"all".equalsIgnoreCase(status)) {
            throw ApiException.badRequest("status must be one of: all, exported, draft.");
        }
        return PageResponse.of(analyses.search(PageRequests.likePattern(search), exportedOnly, draftsOnly,
                PageRequests.of(page, size, sort, SORTS, DEFAULT_SORT)), mapper::toSummary);
    }

    @Transactional
    public AnalysisDto.Response create(AnalysisDto.CreateRequest request) {
        Cv cv = cvs.findById(request.cvId()).orElseThrow(() -> ApiException.notFound("CV not found."));
        JobPosting job = jobs.findById(request.jobId()).orElseThrow(() -> ApiException.notFound("Job not found."));
        if (!job.getCv().getId().equals(cv.getId())) {
            throw ApiException.unprocessable("This job was added for a different CV.");
        }
        Analysis analysis = build(cv, job);
        activity.log(ActivityType.ANALYSIS_CREATED, "Customizing " + cv.getFileName() + " for " + analysis.getJobTitle());
        return mapper.toResponse(analysis);
    }

    /** A fresh analysis from the same CV and job; picks up any profile edits made since. */
    @Transactional
    public AnalysisDto.Response regenerate(UUID id) {
        Analysis source = find(id);
        Analysis analysis = build(source.getCv(), source.getJob());
        activity.log(ActivityType.ANALYSIS_CREATED, "Regenerated " + analysis.getJobTitle() + " for " + source.getCv().getFileName());
        return mapper.toResponse(analysis);
    }

    @Transactional(readOnly = true)
    public AnalysisDto.Response get(UUID id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public void delete(UUID id) {
        Analysis analysis = find(id);
        analyses.delete(analysis);
        activity.log(ActivityType.ANALYSIS_DELETED, "Deleted customization for " + analysis.getJobTitle());
    }

    @Transactional(readOnly = true)
    public AnalysisDto.CustomizationResponse customization(UUID id) {
        Analysis analysis = find(id);
        return mapper.toResponse(analysis, mapper.customizationOf(analysis));
    }

    // ------------------------------------------------------------------ chat

    @Transactional(readOnly = true)
    public AnalysisDto.ChatTurnResponse chat(UUID id) {
        return turn(context(find(id)));
    }

    /**
     * The AI call happens between two short transactions so a slow provider never holds a database
     * connection. The optimistic lock on Analysis rejects a concurrent edit made in the meantime.
     */
    public AnalysisDto.ChatTurnResponse sendChat(UUID id, String text) {
        String value = text.strip();
        if (!coach.available()) {
            throw ApiException.unprocessable("The AI assistant is not configured on this server. You can still review "
                    + "and approve each section by hand.");
        }

        record Prepared(Ctx ctx, CoachChatService.Context context, List<CoachChatService.Turn> history) {
        }
        Prepared prepared = readTx.execute(status -> {
            Ctx ctx = context(find(id));
            List<CoachChatService.Turn> history = messages.findByAnalysisIdOrderByIdAsc(id).stream()
                    .map(m -> new CoachChatService.Turn(m.getRole(), m.getText())).toList();
            return new Prepared(ctx, coachContext(ctx), history);
        });

        CoachChatService.Outcome outcome = coach.reply(prepared.context(), prepared.history(), value);

        return tx.execute(status -> {
            Ctx ctx = context(find(id));
            if (ctx.analysis().getVersion() != prepared.ctx().analysis().getVersion()) {
                throw ApiException.conflict("This customization changed while CVPilot was answering. Reload and try again.");
            }
            String reply = outcome.reply() + apply(ctx, outcome);
            messages.save(new ChatMessage(ctx.analysis(), ChatRole.USER, value));
            messages.save(new ChatMessage(ctx.analysis(), ChatRole.BOT, reply));
            persist(ctx);
            return turn(ctx);
        });
    }

    /** Validates and applies the model's proposed action; returns a note to append when it was refused. */
    private String apply(Ctx ctx, CoachChatService.Outcome outcome) {
        List<String> gaps = gaps(ctx);
        switch (outcome.action()) {
            case DECLINE_SKILL -> {
                String skill = matchIgnoreCase(gaps, outcome.skill());
                if (skill != null) ctx.state().getDeclined().add(skill.toLowerCase(Locale.ROOT));
            }
            case ADD_TRANSFERABLE -> {
                String gap = matchIgnoreCase(gaps, outcome.skill());
                String basedOn = gap == null ? null : matchIgnoreCase(engine.transferableFor(gap, ctx.profile()), outcome.basedOn());
                if (basedOn == null) {
                    return "\n\n(I could not add that line: it has to be based on a skill your CV already shows.)";
                }
                if (ctx.state().getTransferableAdded().add(basedOn.toLowerCase(Locale.ROOT))) {
                    addTransferable(ctx, basedOn);
                }
            }
            case NONE -> {
            }
        }
        return "";
    }

    private void addTransferable(Ctx ctx, String basedOn) {
        SectionProposal summary = ctx.customization().section("summary");
        if (summary == null) return;
        String proposed = (summary.getProposed().strip() + " Also brings transferable experience from " + basedOn
                + ", applying the same concepts in related technologies.").strip();
        summary.setProposed(proposed);
        summary.setApproved(false);
        summary.setViolations(new ArrayList<>(engine.violationsFor(proposed, ctx.profile(), ctx.target())));
    }

    // ------------------------------------------------------------------ review

    @Transactional
    public AnalysisDto.CustomizationResponse approveSection(UUID id, String key, String text) {
        Ctx ctx = context(find(id));
        SectionProposal section = ctx.customization().section(key);
        if (section == null) throw ApiException.notFound("Section not found.");
        if (section.isLocked()) throw ApiException.conflict("This section is locked and stays exactly as in your CV.");

        section.setProposed(text.strip());
        section.setViolations(new ArrayList<>(engine.violationsFor(section.getProposed(), ctx.profile(), ctx.target())));
        section.setApproved(section.getViolations().isEmpty());
        persist(ctx);
        return mapper.toResponse(ctx.analysis(), ctx.customization());
    }

    @Transactional
    public AnalysisDto.CustomizationResponse setDecision(UUID id, String decisionId, DecisionAction action) {
        Ctx ctx = context(find(id));
        CvDecision decision = ctx.customization().getDecisions().stream()
                .filter(d -> d.getId().equals(decisionId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Suggestion not found."));
        List<CvDecision> previousDecisions = ctx.customization().getDecisions().stream()
                .map(d -> new CvDecision(d.getId(), d.getKind(), d.getLabel(), d.getReason(), d.getSuggestion(), d.getAction()))
                .toList();
        decision.setAction(action);
        ctx.customization().setSections(engine.rebuildSections(ctx.profile(), ctx.target(), previousDecisions,
                ctx.customization().getDecisions(), ctx.customization().getSections()));
        // A regenerated summary no longer carries earlier transferable lines; allow them to be added again.
        SectionProposal summary = ctx.customization().section("summary");
        String summaryText = summary == null ? "" : summary.getProposed().toLowerCase(Locale.ROOT);
        ctx.state().getTransferableAdded().removeIf(s -> !summaryText.contains("transferable experience from " + s));
        persist(ctx);
        return mapper.toResponse(ctx.analysis(), ctx.customization());
    }

    @Transactional
    public ExportResult export(UUID id, ExportFormat format) {
        Ctx ctx = context(find(id));
        Analysis analysis = ctx.analysis();
        if (!ctx.customization().readyToExport()) throw ApiException.unprocessable("Every section must be approved before exporting.");
        // Stored violations were computed against the profile at approval time; the CV may have been edited since.
        boolean unsupported = ctx.customization().getSections().stream()
                .filter(s -> !s.isLocked())
                .anyMatch(s -> !engine.violationsFor(s.getProposed(), ctx.profile(), ctx.target()).isEmpty());
        if (unsupported) {
            throw ApiException.unprocessable("A section mentions something your CV no longer shows. Review it before exporting.");
        }

        // Every format uses the same LaTeX résumé template: .tex is its source, PDF is it compiled.
        CvExportService.ExportedFile file = exporter.render(
                documents.build(ctx.profile(), ctx.target(), ctx.customization()), format);

        analysis.setExported(true);
        analysis.setExportCount(analysis.getExportCount() + 1);
        analyses.save(analysis);
        activity.log(ActivityType.ANALYSIS_EXPORTED, "Exported " + format.name() + " for " + analysis.getJobTitle());

        String safe = analysis.getJobTitle().replaceAll("[^\\p{L}\\p{N}_-]+", "_").replaceAll("^_+|_+$", "");
        return new ExportResult(file.content(), file.mimeType(), (safe.isEmpty() ? "CV" : safe) + "_CVPilot." + file.extension());
    }

    // ------------------------------------------------------------------ internals

    private Analysis build(Cv cv, JobPosting job) {
        CvProfile profile = cvMapper.profileOf(cv);
        List<JobPosition> positions = jobMapper.positionsOf(job);
        JobPosition target = engine.mergePositions(positions);
        List<CvDecision> decisions = engine.buildPlan(profile, target);

        Analysis analysis = new Analysis();
        analysis.setCv(cv);
        analysis.setJob(job);
        analysis.setJobTitle(truncate(target.title(), 300));
        analysis.setLanguage(cv.getLanguage());
        analysis.setSourceFormat(cv.getSourceFormat());
        Customization customization =
                new Customization(engine.initialSections(profile, target, decisions), decisions);
        mapper.setCustomization(analysis, customization);
        mapper.setChatState(analysis, new ChatState());
        AnalysisInsights insights = scoring.compute(profile, target, "pending", new ChatState().getDeclined());
        analysis.setScore(insights.score());
        Analysis saved = analyses.saveAndFlush(analysis);

        for (String text : seedMessages(cv, positions, target, profile, insights)) {
            messages.save(new ChatMessage(saved, ChatRole.BOT, text));
        }
        return saved;
    }

    private List<String> seedMessages(Cv cv, List<JobPosition> positions, JobPosition target, CvProfile profile,
                                      AnalysisInsights insights) {
        List<String> out = new ArrayList<>();
        String roles = positions.size() > 1 ? "a posting with " + positions.size() + " roles" : "the job posting";
        out.add("Hi — I read " + cv.getFileName() + " and " + roles + ". I'll stay with you until this CV is the best honest fit for it.");
        out.add("I suggested which items to highlight, keep, lower or remove. Nothing is removed unless you approve it, "
                + "and I never add anything your CV does not support.");
        List<String> topGaps = insights.weaknesses().stream().limit(2).map(AnalysisInsights.Weakness::title).toList();
        out.add("I scored this CV at " + insights.score() + "/100 for " + target.title()
                + (topGaps.isEmpty() ? "" : ", with gaps like " + String.join(" and ", topGaps))
                + ". Ask \"why\" for the breakdown, or open Job fit for skills to develop.");

        List<String> missing = engine.missingSkills(profile, target);
        if (missing.isEmpty()) {
            out.add("Your CV already covers every skill this posting names. I've ordered the most relevant items first — "
                    + "review the suggested changes beside the chat.");
        } else {
            String shown = String.join(", ", missing.stream().limit(8).toList()) + (missing.size() > 8 ? " and " + (missing.size() - 8) + " more" : "");
            out.add("The job asks for these, but your CV does not show them: " + shown
                    + ". I won't add any of them unless your CV supports it.");
            if (coach.available()) {
                String gap = missing.getFirst();
                List<String> alt = engine.transferableFor(gap, profile);
                out.add("Do you have hands-on experience with " + gap + "? Tell me where you used it, or type \"skip\"."
                        + (alt.isEmpty() ? "" : " Tip: your " + alt.getFirst() + " experience is related — I can frame it without naming " + gap + "."));
            }
        }
        if (!coach.available()) {
            out.add("The AI chat is not configured on this server, so I can't answer messages. You can still adjust the "
                    + "plan, edit each section, approve it and export.");
        }
        return out;
    }

    private Ctx context(Analysis analysis) {
        CvProfile profile = cvMapper.profileOf(analysis.getCv());
        JobPosition target = engine.mergePositions(jobMapper.positionsOf(analysis.getJob()));
        return new Ctx(analysis, profile, analysis.getJob(), target, mapper.customizationOf(analysis), mapper.chatStateOf(analysis));
    }

    private AnalysisInsights insights(Ctx ctx) {
        return scoring.compute(ctx.profile(), ctx.target(), ctx.analysis().getId().toString(), ctx.state().getDeclined());
    }

    private void persist(Ctx ctx) {
        mapper.setCustomization(ctx.analysis(), ctx.customization());
        mapper.setChatState(ctx.analysis(), ctx.state());
        ctx.analysis().setScore(insights(ctx).score());
        analyses.saveAndFlush(ctx.analysis());
    }

    private AnalysisDto.ChatTurnResponse turn(Ctx ctx) {
        List<AnalysisDto.ChatMessageResponse> history = messages.findByAnalysisIdOrderByIdAsc(ctx.analysis().getId())
                .stream().map(mapper::toResponse).toList();
        return new AnalysisDto.ChatTurnResponse(history, mapper.toResponse(ctx.analysis(), ctx.customization()), insights(ctx));
    }

    private CoachChatService.Context coachContext(Ctx ctx) {
        List<String> gaps = gaps(ctx);
        Map<String, List<String>> transferable = new LinkedHashMap<>();
        for (String gap : gaps) {
            List<String> alt = engine.transferableFor(gap, ctx.profile());
            if (!alt.isEmpty()) transferable.put(gap, alt);
        }
        SectionProposal summary = ctx.customization().section("summary");
        return new CoachChatService.Context(ctx.analysis().getLanguage(), ctx.profile(), ctx.target(), insights(ctx),
                gaps, List.copyOf(ctx.state().getDeclined()), transferable, summary == null ? "" : summary.getProposed());
    }

    /** Required + optional skills the CV does not mention. */
    private List<String> gaps(Ctx ctx) {
        List<String> gaps = new ArrayList<>(engine.missingSkills(ctx.profile(), ctx.target()));
        String cvText = ctx.profile().searchableText();
        ctx.target().requirements().optional().stream()
                .filter(s -> !TextMatcher.mentions(cvText, s))
                .filter(s -> gaps.stream().noneMatch(g -> g.equalsIgnoreCase(s)))
                .forEach(gaps::add);
        return gaps;
    }

    private Analysis find(UUID id) {
        return analyses.findById(id).orElseThrow(() -> ApiException.notFound("Analysis not found."));
    }

    private static String matchIgnoreCase(List<String> values, String wanted) {
        if (wanted == null || wanted.isBlank()) return null;
        return values.stream().filter(v -> v.equalsIgnoreCase(wanted.trim())).findFirst().orElse(null);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
