package com.backend.backend.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Proposed sections plus the keep/highlight/remove plan. Persisted as JSONB on the analysis. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customization {
    private List<SectionProposal> sections = new ArrayList<>();
    private List<CvDecision> decisions = new ArrayList<>();

    public SectionProposal section(String key) {
        return sections.stream().filter(s -> s.getKey().equals(key)).findFirst().orElse(null);
    }

    public boolean readyToExport() {
        return sections.stream().allMatch(s -> s.isLocked() || (s.isApproved() && s.getViolations().isEmpty()));
    }
}
