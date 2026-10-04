package com.tripbora.dto;

import com.tripbora.model.TravelInfo;
import com.tripbora.model.TravelInfoContentFormat;
import com.tripbora.model.TravelInfoContentType;
import com.tripbora.model.TravelInfoScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TravelInfoFormTest {

    @Test
    void contentFormatDefaultsToQuillAndEditFormKeepsStoredFormat() {
        assertThat(new TravelInfo().getContentFormat()).isEqualTo(TravelInfoContentFormat.QUILL);
        assertThat(new TravelInfoForm().getContentFormat()).isEqualTo(TravelInfoContentFormat.QUILL);

        TravelInfo quill = new TravelInfo();
        quill.setContent("<p>본문</p>");
        TravelInfoForm quillForm = TravelInfoForm.from(quill, List.of());
        assertThat(quillForm.getContentFormat()).isEqualTo(TravelInfoContentFormat.QUILL);
        assertThat(quillForm.getStructuredContent()).isNull();

        TravelInfo structured = new TravelInfo();
        structured.setContentFormat(TravelInfoContentFormat.STRUCTURED);
        structured.setStructuredContent("{\"version\":1,\"blocks\":[]}");
        TravelInfoForm structuredForm = TravelInfoForm.from(structured, List.of());
        assertThat(structuredForm.getContentFormat()).isEqualTo(TravelInfoContentFormat.STRUCTURED);
        assertThat(structuredForm.getStructuredContent()).isEqualTo("{\"version\":1,\"blocks\":[]}");
    }

    @Test
    void contentSectionIsSavedAsTheExistingContentTypeAndScope() {
        TravelInfoForm domestic = new TravelInfoForm();
        domestic.setContentSection("GENERAL_DOMESTIC");
        assertThat(domestic.getContentType()).isEqualTo(TravelInfoContentType.GENERAL);
        assertThat(domestic.getScope()).isEqualTo(TravelInfoScope.DOMESTIC);

        TravelInfoForm international = new TravelInfoForm();
        international.setContentSection("GENERAL_INTERNATIONAL");
        assertThat(international.getContentType()).isEqualTo(TravelInfoContentType.GENERAL);
        assertThat(international.getScope()).isEqualTo(TravelInfoScope.INTERNATIONAL);

        TravelInfoForm guide = new TravelInfoForm();
        guide.setContentSection("GUIDE");
        assertThat(guide.getContentType()).isEqualTo(TravelInfoContentType.GUIDE);

        // 축제·행사는 국내/해외를 따로 고르므로 콘텐츠 구분이 범위를 바꾸지 않는다.
        TravelInfoForm festival = new TravelInfoForm();
        festival.setScope(TravelInfoScope.INTERNATIONAL);
        festival.setContentSection("FESTIVAL");
        assertThat(festival.getContentType()).isEqualTo(TravelInfoContentType.FESTIVAL);
        assertThat(festival.getScope()).isEqualTo(TravelInfoScope.INTERNATIONAL);

        TravelInfoForm unknown = new TravelInfoForm();
        unknown.setContentSection("SOMETHING_ELSE");
        assertThat(unknown.getContentType()).isNull();
    }

    @Test
    void editFormRestoresTheContentSectionFromStoredContentTypeAndScope() {
        assertThat(sectionOf(TravelInfoContentType.GENERAL, TravelInfoScope.DOMESTIC))
                .isEqualTo("GENERAL_DOMESTIC");
        assertThat(sectionOf(TravelInfoContentType.GENERAL, TravelInfoScope.INTERNATIONAL))
                .isEqualTo("GENERAL_INTERNATIONAL");
        assertThat(sectionOf(TravelInfoContentType.GUIDE, null)).isEqualTo("GUIDE");
        assertThat(sectionOf(TravelInfoContentType.FESTIVAL, TravelInfoScope.DOMESTIC))
                .isEqualTo("FESTIVAL");
        assertThat(sectionOf(TravelInfoContentType.FESTIVAL, TravelInfoScope.INTERNATIONAL))
                .isEqualTo("FESTIVAL");
    }

    private String sectionOf(TravelInfoContentType contentType, TravelInfoScope scope) {
        TravelInfo travelInfo = new TravelInfo();
        travelInfo.setContentType(contentType);
        travelInfo.setScope(scope);
        return TravelInfoForm.from(travelInfo, List.of()).getContentSection();
    }
}
