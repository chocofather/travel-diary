-- 편의시설(amenity) 번역 언어 코드 정리용 SQL
--
-- 실행은 사람이 직접 한다. 애플리케이션 코드는 이 파일을 읽지 않는다.
-- 화면이 쓰는 canonical 언어 코드는 ko / en / ja / zh-CN / zh-TW 다섯 개다.
-- (amenity_translations.language_code 는 varchar(5) 이므로 'zh-CN', 'zh-TW' 가 그대로 들어간다.)
--
-- 지금 코드는 번역이 없으면 요청 언어 → 한국어 → 남은 언어 → amenities.code 순서로 내려가므로,
-- 아래 정리를 하지 않아도 화면이 비거나 깨지지는 않는다. 다만 중국어 사용자는 한국어 이름을 보게 된다.
-- 간체(zh-CN)와 번체(zh-TW)는 서로 대체하지 않는다.

-- ---------------------------------------------------------------------------
-- 1) 현재 상태 확인 (먼저 실행)
-- ---------------------------------------------------------------------------

-- 언어별 번역 행 수
SELECT language_code,
       COUNT(*)                 AS translation_rows,
       COUNT(DISTINCT amenity_id) AS amenities_covered
FROM amenity_translations
GROUP BY language_code
ORDER BY language_code;

-- 전체 편의시설 수와 언어별 채움 정도를 한 줄로
SELECT (SELECT COUNT(*) FROM amenities)                            AS amenities_total,
       SUM(language_code = 'ko')                                   AS ko_rows,
       SUM(language_code = 'en')                                   AS en_rows,
       SUM(language_code = 'ja')                                   AS ja_rows,
       SUM(language_code = 'zh')                                   AS zh_legacy_rows,
       SUM(language_code = 'zh-CN')                                AS zh_cn_rows,
       SUM(language_code = 'zh-TW')                                AS zh_tw_rows,
       SUM(language_code NOT IN ('ko', 'en', 'ja', 'zh', 'zh-CN', 'zh-TW')) AS other_rows
FROM amenity_translations;

-- canonical 코드가 비어 있는 편의시설 목록
SELECT a.id,
       a.code,
       MAX(t.language_code = 'ko')    AS has_ko,
       MAX(t.language_code = 'en')    AS has_en,
       MAX(t.language_code = 'ja')    AS has_ja,
       MAX(t.language_code = 'zh-CN') AS has_zh_cn,
       MAX(t.language_code = 'zh-TW') AS has_zh_tw
FROM amenities a
         LEFT JOIN amenity_translations t ON t.amenity_id = a.id
GROUP BY a.id, a.code
ORDER BY a.id;

-- 기존 'zh' 행이 간체인지 번체인지 눈으로 확인 (예: 停车场=간체 / 停車場=번체)
SELECT t.amenity_id, a.code, t.name
FROM amenity_translations t
         JOIN amenities a ON a.id = t.amenity_id
WHERE t.language_code = 'zh'
ORDER BY t.amenity_id;

-- ---------------------------------------------------------------------------
-- 2) 'zh' 행 정리 (1번 결과를 보고 하나만 선택해서 실행)
--    어느 쪽 글자인지 확인하기 전에는 실행하지 말 것.
-- ---------------------------------------------------------------------------

-- (A) 기존 'zh' 텍스트가 간체였다면
-- UPDATE amenity_translations SET language_code = 'zh-CN' WHERE language_code = 'zh';

-- (B) 기존 'zh' 텍스트가 번체였다면
-- UPDATE amenity_translations SET language_code = 'zh-TW' WHERE language_code = 'zh';

-- 남은 한쪽(번체 또는 간체)은 실제 번역을 새로 넣어야 한다.
-- 같은 문장을 복사하면 반대쪽 화면에 잘못된 글자가 그대로 보이므로 복사는 권하지 않는다.
-- 한 건씩 채우는 예시:
-- INSERT INTO amenity_translations (amenity_id, language_code, name)
-- VALUES (1, 'zh-TW', '停車場')
-- ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ---------------------------------------------------------------------------
-- 3) 정리 후 확인
-- ---------------------------------------------------------------------------

SELECT language_code, COUNT(*) AS translation_rows
FROM amenity_translations
GROUP BY language_code
ORDER BY language_code;

-- canonical 다섯 코드 밖의 행이 남아 있는지
SELECT *
FROM amenity_translations
WHERE language_code NOT IN ('ko', 'en', 'ja', 'zh-CN', 'zh-TW')
ORDER BY amenity_id, language_code;
