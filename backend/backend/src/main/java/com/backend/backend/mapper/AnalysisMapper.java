package com.backend.backend.mapper;

import com.backend.backend.dto.AnalysisDto;
import com.backend.backend.dto.DashboardDto;
import com.backend.backend.entity.Activity;
import com.backend.backend.entity.Analysis;
import com.backend.backend.entity.ChatMessage;
import com.backend.backend.model.ChatState;
import com.backend.backend.model.Customization;
import com.backend.backend.util.JsonCodec;
import org.springframework.stereotype.Component;

@Component
public class AnalysisMapper {

    private final JsonCodec json;

    public AnalysisMapper(JsonCodec json) {
        this.json = json;
    }

    public Customization customizationOf(Analysis analysis) {
        return json.read(analysis.getCustomization(), Customization.class);
    }

    public void setCustomization(Analysis analysis, Customization customization) {
        analysis.setCustomization(json.write(customization));
    }

    public ChatState chatStateOf(Analysis analysis) {
        return json.read(analysis.getChatState(), ChatState.class);
    }

    public void setChatState(Analysis analysis, ChatState state) {
        analysis.setChatState(json.write(state));
    }

    public AnalysisDto.Response toResponse(Analysis a) {
        return new AnalysisDto.Response(a.getId(), a.getCv().getId(), a.getJob().getId(), a.getJobTitle(), a.getLanguage(),
                a.getSourceFormat(), a.getScore(), a.isExported(), a.getCreatedAt());
    }

    public AnalysisDto.Summary toSummary(Analysis a) {
        return new AnalysisDto.Summary(a.getId(), a.getJobTitle(), a.getCv().getFileName(), a.getSourceFormat(), a.getScore(),
                a.isExported(), a.getCreatedAt());
    }

    public AnalysisDto.CustomizationResponse toResponse(Analysis analysis, Customization customization) {
        return new AnalysisDto.CustomizationResponse(analysis.getId(), analysis.getLanguage(), customization.getSections(),
                customization.getDecisions());
    }

    public AnalysisDto.ChatMessageResponse toResponse(ChatMessage message) {
        return new AnalysisDto.ChatMessageResponse(String.valueOf(message.getId()), message.getRole(), message.getText(),
                message.getCreatedAt());
    }

    public DashboardDto.ActivityResponse toResponse(Activity activity) {
        return new DashboardDto.ActivityResponse(String.valueOf(activity.getId()), activity.getType(), activity.getLabel(),
                activity.getCreatedAt());
    }
}
