# 랜덤 여행 지역 추천 설계

## 목표

기존 `/api/random-recommend/{regionId}` 배열 응답과 동작은 그대로 보존하면서, `/random-travel`의 scope 추천 단위를 destination 한 개에서 여행 지역과 최대 8개의 여행지 카드로 변경한다.

## 지역 선택

- 실제 국가는 기존 `CountryCategoryService#getCourseCountries()` 결과로 판별한다.
- 국내는 부모가 없는 실제 국가인 대한민국을 동적으로 찾고, 그 직계 자식 중 자신 또는 보이는 descendant에 한국어 여행지가 존재하는 지역만 후보로 삼는다.
- 해외는 부모가 있는 실제 국가 중 여행지가 존재하는 국가를 균등하게 먼저 뽑고, 그 국가의 직계 자식 중 여행지가 존재하는 지역을 다시 뽑는다.
- 해외 국가에 사용 가능한 직계 하위 지역이 없을 때만 국가 자체를 결과 지역으로 사용한다.
- 국내·해외 모두 `depth`나 국가·대륙 숫자 ID에 의존하지 않는다.
- `excludeRegionId`가 속한 후보는 다른 후보보다 뒤로 보내 직전 지역 반복을 가능한 범위에서 피한다.

## 조회 구조

- `RandomRecommendMapper`에 여행지가 있는 실제 국가 및 직계 하위 지역을 고르는 조회를 추가한다.
- 후보 존재 여부와 선택 지역 descendant ID는 MySQL 재귀 CTE로 한 번에 확인한다.
- 신규 scope 흐름은 선택 지역 ID 목록을 기존 `findRandomByRegionIds(regionIds, 8)`에 전달해 카드 데이터를 조회한다.
- 기존 region API가 사용하는 Java 계층 재귀는 이번 작업에서 교체하지 않는다.
- 보이지 않는 category 경로와 한국어 번역이 없는 destination은 기존 카드 조회 정책대로 제외한다.

## API

`GET /api/random-recommend?scope=domestic|overseas&excludeRegionId={id}`는 다음 의미의 객체를 반환한다.

- `scope`
- `countryId`, `countryName`
- `regionId`, `regionName`
- `recommendedDestinations[]`

각 destination은 기존 `RandomDestinationDto`의 대표 이미지, 이름, 실제 짧은 설명, 실제 지역명, `/destinations/{id}` 상세 URL을 사용한다. 결과가 없으면 204, 잘못된 scope는 400이다.

## 화면과 navigation

- 기존 상단 Hero와 국내·해외 선택 및 약 1.2초 연출은 유지한다.
- 결과 영역은 지역 Hero, 지역명 기반 섹션 제목, 최대 8개의 반응형 카드 grid, 다시 뽑기로 구성한다.
- API는 뽑기당 한 번만 호출하며 애니메이션 최종값은 응답의 `regionName`이다.
- reduced motion에서는 이름 전환을 생략한다.
- navigation은 국내 / 해외 / 여행 커뮤니티 / 여행정보 / 여행기록 / 고객센터 / 이벤트 7열로 맞추고, 여행기록 submenu에는 `/random-travel` 링크 하나만 둔다.

## 제약

DB 접속, SQL 직접 실행, schema 변경, 새 라이브러리, 메인 랜덤 section 복원, 관련 없는 리팩터링, Git commit/push는 하지 않는다.
