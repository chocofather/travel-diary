# 관리자 진입 및 Dashboard 연결 설계

## 목표

사용자 사이트의 프로필 dropdown을 유지하면서 header action의 우측 여백을 개선하고, 관리자 홈을 세부 관리 버튼만 사용하는 2 × 2 카드 구조로 정리한다.

## 사용자 사이트 Header

- 로그인 사용자의 프로필 이미지는 `button` 안에 배치하고 dropdown toggle로 사용한다.
- USER에게는 마이페이지와 기존 POST 로그아웃 form만 렌더링한다.
- ADMIN에게는 Thymeleaf Security 조건으로 `/admin` 메뉴를 추가한다.
- 기존 우측 독립 Admin 링크와 독립 로그아웃 버튼은 제거한다.
- dropdown은 재클릭 toggle, 외부 클릭 닫기, Escape 닫기와 toggle focus 복원을 지원한다.
- `aria-expanded`, `aria-controls`, `aria-haspopup`와 `hidden` 상태를 동기화한다.
- 기존 검색, navigation, logout action 및 redirect hidden input은 유지한다.
- 검색·프로필 action wrapper에 반응형 우측 padding을 적용하고 중앙 navigation 위치는 변경하지 않는다.

## 관리자 Home

- 기존 세부 링크 `/admin/destinations`, `/admin/categories`, `/admin/region-categories`, `/admin/amenities/list`, `/admin/travel-info`, `/admin/info-categories`, `/admin/event/list`를 유지한다.
- 카드 제목의 화살표 대표 링크와 카드 전체 클릭 기능은 사용하지 않는다.
- 고객지원 카드에 `/admin/notices`, `/admin/faqs`, `/admin/inquiries` 세부 버튼을 추가한다.
- 네 카드는 desktop 2 × 2 grid와 기존 blue 계열·버튼 스타일을 유지한다.
- sidebar와 관리자 topbar의 `사이트 보기 → /`, POST 로그아웃은 변경하지 않는다.

## Security 및 테스트

- `/admin/** hasRole("ADMIN")` 설정은 변경하지 않는다.
- 익명·USER·ADMIN별 header 렌더링, dropdown JS 동작 계약, header 우측 padding, USER 차단과 ADMIN 접근, dashboard 10개 세부 버튼·sidebar·사이트 보기 URL, logout form과 navigation 회귀를 검증한다.
- 관리자 통계, 집계, 차트, 신규 관리 기능은 추가하지 않는다.

## 제약

DB 접속, SQL 실행, schema 변경, 새 라이브러리, 관련 없는 리팩터링, Git commit/push는 하지 않는다.
