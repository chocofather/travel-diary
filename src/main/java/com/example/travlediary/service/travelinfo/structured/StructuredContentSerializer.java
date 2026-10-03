package com.example.travlediary.service.travelinfo.structured;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.stereotype.Component;

/**
 * 서버가 읽고 검사한 typed model 을 저장용 JSON 으로 다시 쓴다.
 *
 * <p>클라이언트가 보낸 문자열을 그대로 저장하지 않는다. 공백·키 순서·빈 칸 같은 표현 차이는 사라지고,
 * model 에 없는 값은 애초에 남을 수 없다. 읽을 때와 같은 {@link StructuredJson} 설정을 써서
 * 여기서 쓴 JSON 은 언제나 {@link StructuredContentParser} 가 다시 읽을 수 있다.
 */
@Component
public class StructuredContentSerializer {

    private static final ObjectWriter CONTENT_WRITER =
            StructuredJson.MAPPER.writerFor(StructuredContent.class);
    private static final ObjectWriter TEXT_WRITER =
            StructuredJson.MAPPER.writerFor(StructuredText.class);

    public String write(StructuredContent content) {
        return write(CONTENT_WRITER, content);
    }

    public String write(StructuredText text) {
        return write(TEXT_WRITER, text);
    }

    private String write(ObjectWriter writer, Object value) {
        try {
            return writer.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            // 검사를 마친 record 만 쓰므로 일어나지 않는다. 일어나면 코드 오류다.
            throw new IllegalStateException("구조화 콘텐츠를 JSON 으로 쓰지 못했습니다.", exception);
        }
    }
}
