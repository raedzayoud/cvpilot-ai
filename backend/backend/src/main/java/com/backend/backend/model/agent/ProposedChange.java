package com.backend.backend.model.agent;

import com.backend.backend.model.Experience;
import com.backend.backend.model.ProjectItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One explainable edit. Holds the human-readable comparison (original/updated text, reason, job
 * requirements, keyword changes, evidence) and the structured before/after values used to apply it.
 * {@code before*} values let the server refuse to apply a change whose target was edited meanwhile.
 *
 * @param section  section key: header, summary, skills, experience, projects, layout
 * @param targetId id of the experience/project for item operations, else null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProposedChange(
        AgentOperation operation,
        String section,
        String targetId,
        String label,
        String originalText,
        String updatedText,
        String reason,
        List<String> jobRequirements,
        List<String> keywordsAdded,
        List<String> keywordsRemoved,
        List<AgentFacts.Evidence> evidence,
        // structured "after" values (one is set, depending on the operation)
        String text,
        List<String> list,
        Experience experience,
        ProjectItem project,
        // structured "before" values
        String beforeText,
        List<String> beforeList,
        Experience beforeExperience,
        ProjectItem beforeProject) {

    public ProposedChange {
        jobRequirements = jobRequirements == null ? List.of() : List.copyOf(jobRequirements);
        keywordsAdded = keywordsAdded == null ? List.of() : List.copyOf(keywordsAdded);
        keywordsRemoved = keywordsRemoved == null ? List.of() : List.copyOf(keywordsRemoved);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    /** Same target = same section and item; a newer proposal for it supersedes the older one. */
    public String targetKey() {
        return section + ":" + (targetId == null ? "" : targetId) + ":" + (operation.isRemoval() ? "remove" : "edit");
    }
}
