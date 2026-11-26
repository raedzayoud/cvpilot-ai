package com.backend.backend.model;

import jakarta.validation.constraints.Size;

public record CertificationItem(
        @Size(max = 64) String id,
        @Size(max = 300) String name,
        @Size(max = 200) String issuer,
        @Size(max = 20) String year) {

    public CertificationItem {
        id = Lists.text(id);
        name = Lists.text(name);
        issuer = Lists.text(issuer);
        year = Lists.text(year);
    }
}
