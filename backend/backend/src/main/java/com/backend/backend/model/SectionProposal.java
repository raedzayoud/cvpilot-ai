package com.backend.backend.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectionProposal {
    private String key;
    private String label;
    private String original;
    private String proposed;
    private List<SourceRef> sources = new ArrayList<>();
    private List<String> violations = new ArrayList<>();
    private boolean approved;
    private boolean locked;

    public record SourceRef(int line, String text) {
    }
}
