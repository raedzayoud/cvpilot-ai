package com.backend.backend.model;

import jakarta.validation.constraints.Size;
import java.util.List;

public record PersonalInfo(
        @Size(max = 200) String fullName,
        @Size(max = 200) String email,
        @Size(max = 60) String phone,
        @Size(max = 200) String location,
        @Size(max = 20) List<@Size(max = 300) String> links) {

    public PersonalInfo {
        fullName = Lists.text(fullName);
        email = Lists.text(email);
        phone = Lists.text(phone);
        location = Lists.text(location);
        links = Lists.strings(links);
    }

    public static PersonalInfo empty() {
        return new PersonalInfo("", "", "", "", List.of());
    }
}
