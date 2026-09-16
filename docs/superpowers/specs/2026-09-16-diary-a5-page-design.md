# A5 다이어리 내지 전환 설계

## 목표

다이어리 내지를 기존 41:38에서 A5 세로 비율 148:210으로 전환한다. 읽기, 편집, 비회원 체험이 같은 종이 규격과 상대좌표를 사용하며, 본문과 자유배치 요소는 화면 크기에 따라 함께 축소된다.

## 단일 페이지 규격

- 기준 폭: 576px
- 기준 높이: `576px * 210 / 148 = 817.297...px`
- CSS 기준: `--diary-page-width`, `--diary-page-ratio`, `--diary-page-height`, `--diary-line`, `--diary-lines`
- 읽기와 편집은 기존 `.diary-sheet`를 그대로 공유한다.
- 모바일은 종이 폭을 viewport에 맞추고 `aspect-ratio`로 높이를 계산한다.

## 본문과 줄노트

현재 줄 간격 `4.75cqw`를 유지한다. `cqw`는 종이의 content box를 기준으로 계산되며, 576px A5 writing area에는 26개 완전한 줄이 들어간다. 줄 그림, Quill line-height, writing layer 높이는 모두 같은 변수에서 계산한다.

작게/보통/크게와 custom font는 line-height를 바꾸지 않는다. 기존 size별 `top` 보정과 글꼴별 `vertical-align`을 유지해 잉크 위치만 보정한다. 입력 제한도 기존 마지막 Quill block의 `offsetTop + offsetHeight` 검사로 유지한다.

## 자유배치 좌표

DB 값은 종이 전체 기준 0~1 상대좌표다. 가로 폭을 576px로 유지한 채 종이만 세로로 길어지므로, 기존 요소의 화면상 가로값과 실제 픽셀 크기를 보존하려면 세로값만 변환한다.

```text
legacy height / width = 38 / 41
A5 height / width     = 210 / 148

vertical factor
  = (38 / 41) / (210 / 148)
  = 2812 / 4305
  ≈ 0.65319

new position_y = old position_y * 2812 / 4305
new height     = old height     * 2812 / 4305
```

`position_x`, `width`, `rotation`, `z_index`는 바꾸지 않는다. 이 방식은 기존 요소의 왼쪽 위 위치와 크기를 같은 기준 폭에서 그대로 보존하므로 사진과 스티커가 찌그러지지 않고 회전 중심도 유지된다. 늘어난 A5 영역은 기존 페이지 아래쪽에 추가된다.

좌표 버전 컬럼이 없으므로 런타임에서 old/new 좌표를 섞어 판별하지 않는다. 운영 DB에는 제공된 SQL을 배포 시 정확히 한 번 실행해야 한다. 스키마 변경은 없다.

비회원 localStorage 초안은 schemaVersion을 올리고 페이지 요소만 같은 공식으로 한 번 변환한다. 표지 요소는 3:4 표지 좌표이므로 변환하지 않는다.

## 새 요소

새 요소는 A5 좌표로 바로 저장한다. 같은 기준 폭에서 기존 시각 크기를 유지하도록 스티커, 테이프, 라벨, 메모의 기본 높이는 위 세로 계수로 환산한다. 사진 높이는 A5 canvas aspect를 사용해 원본 비율에서 계산한다. 드래그와 resize는 실제 canvas 폭/높이로 정규화하는 기존 로직을 그대로 사용한다.

## 범위

- 변경: 내지 CSS, 페이지용 요소 기본 크기, 페이지 사진 비율, 비회원 페이지 초안 마이그레이션, 관련 테스트와 수동 DB 변환 SQL
- 유지: 표지 3:4 규격과 표지 요소, Quill matcher/Enter/autosave 로직, 저장 endpoint와 권한, DB 스키마
- 제외: PDF export와 PDF MediaBox, bleed/재단선, 표지 인쇄 규격
