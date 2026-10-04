-- Wikidata 해외 여행지 5단계 + 이미지 출처 분리 6-A: 향후 적용할 제안 DDL.
-- 현재 DB에 적용하지 않았으며, tripbora_schema_reference.md에도 반영하지 않았다.
-- 기준: docs/db/tripbora_schema_reference.md의 destinations,
-- destination_translations, destination_images 정의. 운영 DB와 다르면 이 파일을 그대로 실행하지 않는다.
-- 제공된 개발 DB SHOW CREATE TABLE 결과: destination_translations.short_description은
-- 레퍼런스의 VARCHAR(100)과 달리 VARCHAR(255) NULL이다.
-- 아래 MODIFY는 길이 255·NULL과 제안된 DEFAULT NULL을 유지하고 문자셋만 바꾼다.
-- 개발/운영 DB의 실제 DEFAULT·인덱스를 적용 전 대조하고 다르면 DDL을 조정한다.
-- 실행 순서와 기존 데이터 이전/검증: destination_image_sources_backfill.sql.
-- 기존 이미지 출처 컬럼 제거는 이 파일에 포함하지 않는다.
-- 적용 전 확인 (운영자):
-- 1. SHOW CREATE TABLE로 세 테이블의 실제 타입/길이/NULL/DEFAULT/COMMENT,
--    문자셋·collation, 엔진·ROW_FORMAT, FK/인덱스를 레퍼런스와 대조한다.
-- 2. destinations의 uq_destinations_source_content(source_type, external_content_id),
--    destination_translations의 unique_destination_language와 기존 FK를 확인한다.
--    제안한 두 출처 테이블·FK가 이미 있거나 일부만 적용되었는지도 확인한다.
-- 3. MySQL 버전과 JSON 지원, 문자셋 변환 후 행/인덱스 크기, DDL 잠금 시간,
--    백업·복구 계획, 사용 가능한 저장 공간을 확인한다.
-- 4. 기존 수동/KTO 행 및 이미지의 source_type/default/NULL 값과 향후
--    WIKIDATA + QID 조합의 중복 여부를 확인한다. 특히 이전 버전의 Commons 전용
--    destination_image_sources가 이미 만들어졌으면 이 DDL을 실행하지 말고 조정한다.
-- 5. MySQL DDL은 자동 커밋된다. 별도 백업과 롤백 절차를 준비하고 운영 쓰기
--    중단 또는 애플리케이션 이중 쓰기 준비 후 단계별로 적용한다.

-- 테이블 전체 CONVERT는 다른 컬럼의 정의·인덱스에 영향을 줄 수 있어 사용하지 않는다.
-- 기존 타입·길이·NULL/DEFAULT를 그대로 두고 Wikipedia 설명과 Wikidata 다국어
-- 명칭·간단 설명의 문자셋만 바꾼다. 기존 UNIQUE(language_code,destination_id)는 건드리지 않는다.
-- 후속 저장 로직은 name 255자, 개발 DB short_description 255자, description TEXT의
-- 바이트 한도를 검증하고 초과 값을 자르지 말고 관리자에게 오류를 표시한다.
ALTER TABLE destination_translations
    MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    MODIFY COLUMN description TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    MODIFY COLUMN short_description VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL;

-- 이미지의 image_url/source_type/created_at/is_main/is_slide/order_index/destination_id는
-- destination_images에 남긴다. 기존 출처 컬럼은 코드 전환이 끝날 때까지 유지하며,
-- 새 출처 테이블을 utf8mb4로 만든다. 짧은 기존 utf8mb3 컬럼에는 Commons 원문을
-- 잘라 이중 저장하지 않는다. 새 원문 저장은 새 테이블을 읽는 코드가 배포된 뒤에 시작한다.

-- destinations는 변경하지 않는다. 후속 Wikidata 저장 구현은 source_type='WIKIDATA',
-- external_content_id=검증한 QID로 기존 UNIQUE KEY를 재사용한다.
-- ADMIN/NULL 및 KTO_TOURAPI/contentId 정책은 그대로 유지한다. 중복 사전 조회와
-- UNIQUE 충돌 처리를 모두 구현한다. Wikidata 후보 등록만 QID 없는 ADMIN 행으로
-- 저장하지 않으며 기존 수동 등록은 ADMIN/NULL을 그대로 사용한다.

-- 각 언어 설명의 Wikipedia 판본과 출처를 번역 행에 1:1로 연결한다.
-- 원본 해시: QID와 해당 언어/변형의 문서·판본을 재검증한 뒤, 가져온 평문을
-- CRLF/CR -> LF로만 정규화하여 UTF-8 바이트의 SHA-256을 BINARY(32)에 저장한다.
-- 실제 description도 같은 개행 정규화를 적용한다. 저장 전 관리자 편집이 있으면
-- 저장된 description의 동일 방식 해시와 원본 해시를 비교해 content_modified=1로 둔다.
-- 일반 수정 시 원본 해시/판본 ID는 바꾸지 않고 변경 여부만 다시 계산한다.
-- 명시적인 원문 재가져오기와 관리자 재확인 때만 새 판본 ID·원본 해시·URL·라이선스·
-- 확인 시각을 함께 갱신한다. 설명 삭제/출처 분리 시 출처 행도 같은 트랜잭션에서 정리한다.
-- source_revision_id는 실제 가져온 판본의 ID이며 원문 링크는 이 판본을 확인할 수
-- 있어야 한다. 표시 언어가 fallback되면 표시된 번역 행의 출처를 조회한다.
-- 기존 수동/KTO 번역에는 출처 행을 채우지 않는다. 1:1 FK는 신규 Wikipedia 설명에만 쓴다.
CREATE TABLE destination_translation_sources (
    destination_translation_id BIGINT NOT NULL,
    wikidata_qid VARCHAR(16) NOT NULL,
    source_title TEXT NOT NULL,
    source_url TEXT NOT NULL,
    source_revision_id BIGINT UNSIGNED NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    source_variant VARCHAR(10) DEFAULT NULL,
    license_name TEXT NOT NULL,
    license_url TEXT NOT NULL,
    attribution_text TEXT NOT NULL,
    original_content_sha256 BINARY(32) NOT NULL,
    content_modified TINYINT(1) NOT NULL DEFAULT 0,
    source_checked_at DATETIME NOT NULL,
    PRIMARY KEY (destination_translation_id),
    CONSTRAINT fk_translation_sources_translation
        FOREIGN KEY (destination_translation_id)
        REFERENCES destination_translations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사진별 범용 출처: image_id 기준 선택적 1:1. 출처 없는 관리자 업로드는 행이 없다.
-- KTO/Commons/관리자 출처를 동일한 필드에 보존하고 Commons 전용 값은 NULL 허용.
-- source_type은 유입 경로이므로 destination_images에 유지하며 라이선스로 추론하지 않는다.
-- common_source_url = 제공기관/컬렉션 공통 페이지,
-- work_page_url = 해당 사진의 원본 저작물 페이지.
-- legacy_source_url = 기존 source_url의 의미가 확정되지 않은 원문 값.
-- 이전 때 legacy_source_url에만 복사하고 다른 두 URL은 NULL로 둔다.
-- TEXT/JSON은 utf8mb4로 원문을 담되, 후속 저장 코드는 바이트 한도를 검증하고
-- 초과 메타데이터를 임의로 자르지 않아야 한다. HTML은 안전한 텍스트/링크로 분리한다.
-- 기존 데이터는 알 수 없는 값(근거, 수정 여부 등)을 NULL로 둔다.
-- 신규 Commons 저장 시 미리보기에서 제한된 값을 쓰지 말고 메타데이터를 재조회한다.
-- license_url이 없는 퍼블릭 도메인은 고정 판본 근거 URL/설명과 관할 검토가
-- 있어야 하며, 근거가 불완전한 콘텐츠는 자동 저장하지 않는다.
-- 사진 저장/삭제와 출처 행 저장/삭제는 같은 트랜잭션이어야 한다.
CREATE TABLE destination_image_sources (
    destination_image_id BIGINT NOT NULL,
    source_name TEXT NULL,
    external_content_id TEXT NULL,
    source_title TEXT NULL,
    author_text TEXT NULL,
    common_source_url TEXT NULL,
    work_page_url TEXT NULL,
    legacy_source_url TEXT NULL,
    original_image_url TEXT NULL,
    license_type VARCHAR(50) DEFAULT NULL,
    license_name TEXT NULL,
    license_version VARCHAR(30) DEFAULT NULL,
    license_detail TEXT NULL,
    license_url TEXT NULL,
    attribution_text TEXT NULL,
    source_credit TEXT NULL,
    custom_attribution TEXT NULL,
    credit_links JSON NULL,
    license_evidence_url TEXT NULL,
    license_evidence_detail TEXT NULL,
    license_evidence_revision_id BIGINT UNSIGNED DEFAULT NULL,
    license_conditions TEXT NULL,
    license_restrictions TEXT NULL,
    attribution_required TINYINT(1) DEFAULT NULL,
    changes_required TINYINT(1) DEFAULT NULL,
    share_alike_required TINYINT(1) DEFAULT NULL,
    content_modified TINYINT(1) DEFAULT NULL,
    license_checked_at DATETIME DEFAULT NULL,
    wikidata_qid VARCHAR(16) DEFAULT NULL,
    commons_file_title TEXT NULL,
    PRIMARY KEY (destination_image_id),
    CONSTRAINT fk_image_sources_image
        FOREIGN KEY (destination_image_id)
        REFERENCES destination_images (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
