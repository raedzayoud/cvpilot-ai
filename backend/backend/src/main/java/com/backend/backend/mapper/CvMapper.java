package com.backend.backend.mapper;

import com.backend.backend.dto.CvDto;
import com.backend.backend.entity.Cv;
import com.backend.backend.model.CvProfile;
import com.backend.backend.util.JsonCodec;
import org.springframework.stereotype.Component;

@Component
public class CvMapper {

    private final JsonCodec json;

    public CvMapper(JsonCodec json) {
        this.json = json;
    }

    public CvProfile profileOf(Cv cv) {
        return json.read(cv.getProfile(), CvProfile.class);
    }

    public void setProfile(Cv cv, CvProfile profile) {
        cv.setProfile(json.write(profile));
    }

    public CvDto.Response toResponse(Cv cv) {
        return new CvDto.Response(cv.getId(), cv.getFileName(), cv.getMimeType(), cv.getSourceFormat(), cv.getSizeBytes(),
                cv.getLanguage(), cv.getUpdatedAt(), cv.getExtractionNote(), profileOf(cv));
    }

    public CvDto.Summary toSummary(Cv cv) {
        return new CvDto.Summary(cv.getId(), cv.getFileName(), cv.getSourceFormat(), cv.getSizeBytes(), cv.getLanguage(), cv.getUpdatedAt());
    }
}
