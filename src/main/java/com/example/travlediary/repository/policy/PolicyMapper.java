package com.example.travlediary.repository.policy;

import com.example.travlediary.model.PolicyTranslation;
import com.example.travlediary.model.PolicyVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * policy_versions / policy_translations 읽기 전용 조회.
 * 정책 본문 등록과 활성화는 DB 작업이라 여기에서 쓰지 않는다.
 */
@Mapper
public interface PolicyMapper {

    /**
     * 요청한 policy_type 들의 현재 사용 버전. 타입당 최대 한 행만 돌려준다.
     *
     * <p>is_active = 1, published_at IS NOT NULL, effective_at <= currentTime 을 모두 만족하는
     * 버전만 대상이다. 아직 활성화하지 않은 정책은 한 행도 나오지 않는다.
     *
     * @param policyTypes 조회할 policy_type 이름
     * @param currentTime 시행일 판정 기준 시각
     */
    List<PolicyVersion> findActiveVersions(
            @Param("policyTypes") List<String> policyTypes,
            @Param("currentTime") LocalDateTime currentTime);

    /**
     * 주어진 버전들의 번역 중 요청 locale 과 대체 locale 것만. 어느 쪽을 쓸지는 서비스가 고른다.
     *
     * @param policyVersionIds 대상 policy_versions.id
     * @param locale 화면 locale
     * @param fallbackLocale 요청 locale 번역이 없을 때 쓸 locale
     */
    List<PolicyTranslation> findTranslations(
            @Param("policyVersionIds") List<Long> policyVersionIds,
            @Param("locale") String locale,
            @Param("fallbackLocale") String fallbackLocale);
}
