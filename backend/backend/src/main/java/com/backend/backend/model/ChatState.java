package com.backend.backend.model;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Facts learned in the conversation that change scoring. Persisted as JSONB on the analysis. */
@Data
@NoArgsConstructor
public class ChatState {
    /** Lowercased skills the user confirmed they do not have. */
    private Set<String> declined = new LinkedHashSet<>();
    /** Lowercased CV skills already used to describe transferable experience. */
    private Set<String> transferableAdded = new LinkedHashSet<>();
}
