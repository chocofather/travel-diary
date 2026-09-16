-- 다이어리 내지 좌표계를 41:38에서 A5(148:210)로 바꾸는 1회성 데이터 변환.
-- 애플리케이션의 A5 내지 배포와 같은 시점에 정확히 한 번만 실행한다.
-- Codex는 이 SQL을 실행하지 않는다.
--
-- 가로 기준 폭은 유지하므로 position_x, width, rotation, z_index는 그대로 둔다.
-- 세로 픽셀 위치와 크기를 보존하는 계수:
--   (38 / 41) / (210 / 148) = 2812 / 4305 ≈ 0.65319

START TRANSACTION;

UPDATE diary_elements
SET position_y = ROUND(position_y * 2812 / 4305, 5),
    height = ROUND(height * 2812 / 4305, 5);

COMMIT;
