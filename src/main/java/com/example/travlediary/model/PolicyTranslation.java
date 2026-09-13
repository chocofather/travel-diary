package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/** policy_translations 한 행. 같은 버전 안에서 locale 별 제목/본문을 담는다. */
@Data
@NoArgsConstructor
public class PolicyTranslation {

    private Long policyVersionId;
    private String locale;
    private String title;
    private String content;
}
