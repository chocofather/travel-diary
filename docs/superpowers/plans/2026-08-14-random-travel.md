# 랜덤 여행 지역 추천 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 region 랜덤 API를 보존하면서 scope API와 `/random-travel`을 지역 결과 및 최대 8개 destination 카드 구조로 변경한다.

**Architecture:** 실제 국가는 기존 `getCourseCountries()` 계층 규칙으로 판별하고, `RandomRecommendMapper`의 재귀 CTE가 여행지가 있는 국가·직계 지역과 선택 지역의 descendant를 조회한다. 서비스는 국내 1단계, 해외 국가→지역 2단계 선택을 조합하고 기존 `findRandomByRegionIds`로 카드 데이터를 조회한다.

**Tech Stack:** Java 17, Spring Boot 3.4.3, Spring Security, MyBatis XML, Thymeleaf, CSS, Vanilla JavaScript, JUnit 5, MockMvc, AssertJ

## Global Constraints

- 기존 `GET /api/random-recommend/{regionId}?size={size}`의 배열 응답과 동작을 변경하지 않는다.
- 대한민국, 대륙, 국가 ID 및 `depth` 숫자로 후보 단계를 하드코딩하지 않는다.
- 신규 scope descendant 조회는 재귀 CTE로 수행하고 destination별 추가 조회를 만들지 않는다.
- 추천 카드는 최대 8개다.
- DB 접속, SQL 실행, schema 변경, 새 라이브러리, Git commit/push를 하지 않는다.

---

### Task 1: 지역 선택 서비스 계약

**Files:**
- Create: `src/main/java/com/example/travlediary/dto/RandomTravelRegionDto.java`
- Create: `src/main/java/com/example/travlediary/dto/RandomTravelResultDto.java`
- Modify: `src/test/java/com/example/travlediary/service/recommend/RandomRecommendServiceTest.java`
- Modify: `src/main/java/com/example/travlediary/service/recommend/RandomRecommendService.java`

**Interfaces:**
- Preserve: `List<RandomDestinationDto> getRandomDestinationsByRegion(Long regionId, int limit)`
- Produce: `Optional<RandomTravelResultDto> getRandomTravelByScope(String scope, Long excludeRegionId)`
- Consume: `findRandomEligibleCountry`, `findRandomEligibleChildRegion`, `findAllVisibleRegionIdsUnder`, `findRandomByRegionIds`

- [x] **Step 1: 실패 테스트 작성** — 기존 region 위임 계약, 대한민국 직계 지역 선택, 해외 실제 국가→직계 지역 선택, 해외 국가 fallback, 빈 후보, 잘못된 scope, 카드 제한 8을 각각 검증한다.
- [x] **Step 2: RED 확인** — `./gradlew test --tests 'com.example.travlediary.service.recommend.RandomRecommendServiceTest'`
- [x] **Step 3: 최소 구현** — `getCourseCountries()`에서 `parentId == null` 국내 국가와 `parentId != null` 해외 국가를 나누고, 선택된 `RandomTravelRegionDto`와 기존 카드 DTO 목록으로 `RandomTravelResultDto`를 만든다.
- [x] **Step 4: GREEN 확인** — 같은 서비스 테스트를 다시 실행한다.

### Task 2: 재귀 CTE Mapper와 scope API

**Files:**
- Modify: `src/main/java/com/example/travlediary/repository/recommend/RandomRecommendMapper.java`
- Modify: `src/main/resources/mapper/RandomRecommendMapper.xml`
- Modify: `src/test/java/com/example/travlediary/repository/RandomRecommendMapperContractTest.java`
- Modify: `src/main/java/com/example/travlediary/controller/recommend/RandomRecommendController.java`
- Modify: `src/test/java/com/example/travlediary/controller/recommend/RandomRecommendControllerTest.java`

**Interfaces:**
- Produce: `RandomTravelRegionDto findRandomEligibleCountry(List<Long> countryIds, Long excludeRegionId)`
- Produce: `RandomTravelRegionDto findRandomEligibleChildRegion(Long countryId, Long excludeRegionId)`
- Produce: `List<Long> findAllVisibleRegionIdsUnder(Long regionId)`
- Produce: `GET /api/random-recommend?scope=...&excludeRegionId=...` returning `RandomTravelResultDto`

- [x] **Step 1: Mapper·Controller 실패 테스트 작성** — 실제 국가 ID 목록만 국가 후보가 되는지, 직계 자식이 지역 anchor인지, destination 존재 조건·visible 경로·재귀 descendant·8개 응답·204·400을 검증하고 기존 path API 배열 계약을 고정한다.
- [x] **Step 2: RED 확인** — Mapper contract 및 Controller 테스트를 실행한다.
- [x] **Step 3: 최소 Mapper 구현** — 후보별 visible subtree를 만드는 재귀 CTE와 한국어 destination 존재 `EXISTS`를 사용한다. 국가 쿼리는 실제 국가 ID 목록의 고유 행에서, 지역 쿼리는 `parent_id = #{countryId}`인 직계 자식에서만 무작위 선택한다.
- [x] **Step 4: 최소 Controller 구현** — path 없는 scope 메서드만 새 결과 DTO와 `excludeRegionId`로 변경하고 path 메서드는 수정하지 않는다.
- [x] **Step 5: GREEN 확인** — 서비스·Mapper contract·Controller 테스트를 함께 실행한다.

### Task 3: navigation과 지역 결과 UI

**Files:**
- Modify: `src/main/resources/templates/fragments/header.html`
- Modify: `src/main/resources/static/css/style.css`
- Modify: `src/main/resources/templates/random-travel.html`
- Modify: `src/main/resources/static/css/random-travel.css`
- Modify: `src/main/resources/static/js/random-travel.js`
- Modify: `src/test/java/com/example/travlediary/controller/recommend/RandomTravelControllerTest.java`
- Modify: `src/test/java/com/example/travlediary/controller/RandomTravelPageContractTest.java`

**Interfaces:**
- Consume: scope 결과의 `countryName`, `regionName`, `recommendedDestinations[]`
- Request: `/api/random-recommend?scope={scope}[&excludeRegionId={regionId}]`
- Navigation: 7개 1차 메뉴와 여행기록 submenu의 랜덤 여행 링크 하나

- [x] **Step 1: HTML/JS/CSS 실패 테스트 작성** — 7개 메뉴 순서, 여행기록의 단일 링크, 한 번의 fetch, region 반복 제외, 지역 Hero, 카드 반복 렌더링, 상세 URL, 이미지 fallback, empty/error/중복 요청/reduced motion, desktop 3열·tablet 2열·mobile 1열을 검증한다.
- [x] **Step 2: RED 확인** — 두 페이지 계약 테스트를 실행한다.
- [x] **Step 3: 최소 UI 구현** — 기존 상단 Hero는 유지하고 결과 영역만 지역 Hero와 최대 8개 카드 grid로 교체한다. 모든 서버 문자열은 DOM API와 `textContent`로 출력한다.
- [x] **Step 4: GREEN 확인** — 페이지 계약 테스트를 다시 실행한다.

### Task 4: 전체 검증

**Files:**
- Verify only: all changed files

- [x] **Step 1: 관련 테스트 실행** — category, service, mapper, controller, page contract 테스트를 실행한다.
- [ ] **Step 2: 전체 검증 실행** — `./gradlew compileJava`, `./gradlew test`, `git diff --check`, `git status --short`를 순서대로 실행한다.
- [ ] **Step 3: 브라우저 확인** — 가능한 경우 `/random-travel`을 desktop/mobile에서 열어 dropdown 정렬, 지역 Hero, 8개 이하 카드, 다시 뽑기, reduced motion, 빈 결과와 이미지 fallback을 확인한다.
