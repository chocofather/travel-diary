# 관리자 진입 및 Dashboard 연결 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 프로필 dropdown을 유지하면서 header 우측 여백을 개선하고 관리자 홈을 세부 버튼만 사용하는 2 × 2 카드 구조로 정리한다.

**Architecture:** 공통 header의 검색·프로필 wrapper에 반응형 우측 padding만 추가한다. 관리자 home은 제목 화살표 링크를 제거하고 기존 실제 관리 URL을 사용하는 고객지원 카드를 네 번째 grid 항목으로 추가한다.

**Tech Stack:** Java 17, Spring Boot 3.4.3, Spring Security, Thymeleaf, Vanilla JavaScript, CSS, JUnit 5, MockMvc, Jsoup

## Global Constraints

- `/admin/**`의 기존 ADMIN 권한 제한과 POST logout redirect 정책을 유지한다.
- ADMIN 메뉴는 서버 측 권한 조건으로만 렌더링한다.
- 실제 Controller가 없는 URL, 통계, 집계, 차트, 새 라이브러리를 추가하지 않는다.
- DB 접속, SQL 실행, schema 변경, Git commit/push를 하지 않는다.

---

### Task 1: 권한별 Profile Dropdown

**Files:**
- Create: `src/test/java/com/example/travlediary/controller/HeaderProfileMenuTest.java`
- Modify: `src/main/resources/templates/fragments/header.html`
- Modify: `src/main/resources/static/js/main.js`
- Modify: `src/main/resources/static/css/style.css`
- Modify: `src/test/java/com/example/travlediary/repository/NoticeUiContractTest.java`

**Interfaces:**
- Toggle: `#profile-menu-toggle[aria-controls="profile-menu"]`
- Menu: `#profile-menu[hidden]`
- USER links: `/mypage`, POST `/logout`
- ADMIN-only link: `/admin`
- Header spacing: `.search-box`의 `padding-right: clamp(8px, 2vw, 24px)`

- [x] **Step 1: 권한별 렌더링과 동작 실패 테스트 작성** — 익명은 profile/admin 메뉴가 없고, USER는 마이페이지·로그아웃만, ADMIN은 `/admin`을 추가로 받는지 검증한다. JS 계약은 재클릭, 외부 클릭, Escape, focus 복원과 `aria-expanded` 갱신을 검증한다.
- [x] **Step 2: RED 확인** — `./gradlew test --tests 'com.example.travlediary.controller.HeaderProfileMenuTest' --tests 'com.example.travlediary.repository.NoticeUiContractTest'`
- [x] **Step 3: 최소 구현** — 기존 이미지와 logout form을 `profile-menu` 안으로 이동하고 ADMIN 링크에 `#authorization.expression('hasRole(''ADMIN'')')`를 적용한다. `main.js`는 `setProfileMenuOpen(isOpen, restoreFocus)`로 상태를 동기화한다.
- [x] **Step 4: GREEN 확인** — 같은 테스트를 다시 실행한다.

### Task 2: 관리자 Home 세부 버튼과 고객지원 카드

**Files:**
- Create: `src/test/java/com/example/travlediary/controller/admin/AdminDashboardControllerTest.java`
- Modify: `src/main/resources/templates/admin/index.html`
- Modify: `src/main/resources/static/css/admin-layout.css`

**Interfaces:**
- Remove: `.admin-dashboard-entry`, `.admin-dashboard-entry-arrow`
- Preserve: 기존 dashboard 세부 링크 7개, sidebar 11개, topbar site link `/`
- Add: `/admin/notices`, `/admin/faqs`, `/admin/inquiries`

- [x] **Step 1: 접근권한과 DOM 실패 테스트 작성** — USER `/admin` 403, ADMIN 200, 대표 링크 제거, 4개 카드와 세부 링크 10개, nested anchor 없음, 사이트 보기와 sidebar URL을 검증한다.
- [x] **Step 2: RED 확인** — `./gradlew test --tests 'com.example.travlediary.controller.admin.AdminDashboardControllerTest'`
- [x] **Step 3: 최소 구현** — 제목·화살표 대표 anchor를 일반 `h2`로 되돌리고 고객지원 카드와 세부 버튼 3개를 추가한다. 기존 2열 grid와 blue 버튼 스타일을 유지한다.
- [x] **Step 4: GREEN 확인** — 같은 테스트를 다시 실행한다.

### Task 3: 회귀 및 최종 검증

**Files:**
- Verify only: all changed files

- [x] **Step 1: 관련 회귀 실행** — header navigation, logout security, 관리자 CRUD Controller 테스트를 실행한다.
- [ ] **Step 2: 필수 검증 실행** — `./gradlew compileJava`, `./gradlew test`, `git diff --check`, `git status --short`를 순서대로 실행한다.
- [ ] **Step 3: 브라우저 확인 항목 정리** — header action 우측 여백, 좁은 화면 overflow, dropdown 위치, dashboard 2 × 2 카드와 고객지원 버튼을 확인 대상으로 보고한다.
