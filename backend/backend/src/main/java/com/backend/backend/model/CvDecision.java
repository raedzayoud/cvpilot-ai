package com.backend.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CvDecision {
    private String id;
    private DecisionKind kind;
    private String label;
    private String reason;
    private Relevance suggestion;
    private DecisionAction action;
}
