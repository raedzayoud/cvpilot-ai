package com.backend.backend.model;

import java.util.List;

public record JobRequirements(
        List<String> technical,
        List<String> optional,
        Integer yearsExperience,
        String education,
        List<String> responsibilities,
        List<String> tools,
        List<String> softSkills) {

    public JobRequirements {
        technical = Lists.strings(technical);
        optional = Lists.strings(optional);
        yearsExperience = yearsExperience == null || yearsExperience < 0 || yearsExperience > 50 ? null : yearsExperience;
        education = education == null || education.isBlank() ? null : education.trim();
        responsibilities = Lists.strings(responsibilities);
        tools = Lists.strings(tools);
        softSkills = Lists.strings(softSkills);
    }

    public static JobRequirements empty() {
        return new JobRequirements(List.of(), List.of(), null, null, List.of(), List.of(), List.of());
    }
}
