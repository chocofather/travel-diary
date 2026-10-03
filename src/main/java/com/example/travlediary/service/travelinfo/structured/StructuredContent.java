package com.example.travlediary.service.travelinfo.structured;

import java.util.List;

/**
 * {@code travel_info.structured_content} 의 최상위 구조.
 *
 * <pre>
 * { "version": 1, "blocks": [ { "id": "...", "type": "SECTION_TITLE", ... }, ... ] }
 * </pre>
 *
 * <p>blocks 순서가 곧 화면 순서다. 읽기는 {@link StructuredContentParser}, 규칙 검사는
 * {@link StructuredContentValidator} 가 맡는다.
 */
public record StructuredContent(Integer version, List<StructuredBlock> blocks) {

    /** 지금 읽고 쓰는 구조 버전. */
    public static final int CURRENT_VERSION = 1;

    public StructuredContent {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
