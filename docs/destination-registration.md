# 여행지 JSON 생성 작업 지침 (AI용)

이 문서는 Claude·Codex 같은 AI가 **여행지 이름 목록을 받아 TripBora JSON 일괄등록용 JSON을 만들 때** 따르는 작업 규칙이다.
사람용 사용설명서가 아니다. 관리자 화면 사용법은 다루지 않는다.

기준은 두 가지다.

- 현재 구현된 JSON 일괄등록 기능 (`/admin/destinations/json-import`, `service/destinationimport/`)
- 관리자 화면에서 내보낸 최신 `destination-import-master.json`

둘이 이 문서와 다르면 **master 파일과 구현이 우선**이다. 차이를 발견하면 작업 결과에 짧게 알린다.

---

## 1. 작업 목표

사용자가 여행지 이름만 준다.

```
경복궁
창덕궁
후쿠오카 타워
오사카성
```

AI는 다음을 모두 수행해 **바로 붙여넣거나 파일로 올릴 수 있는 JSON 하나**를 만든다.

1. 대상 여행지 조사 (어떤 실제 장소인지 식별)
2. 국내/해외 판단, type 판단
3. 실제 소재 지역 확인
4. 사실정보 확인 (좌표·운영정보·외부 ID 등)
5. 5개 언어 데이터 작성
6. `destination-import-master.json`과 대조
7. TripBora JSON 계약에 맞는 JSON 생성

**이미지는 생성 대상이 아니다.** 이미지는 등록 후 운영자가 관리자 화면에서 직접 추가한다.

등록 시점에 시스템이 다시 검사하는 항목은 다음과 같다. AI의 JSON은 이 검사를 처음부터 통과하는 것을 목표로 한다.

- 형식 검사: 모르는 필드, 길이, 지역·카테고리·편의시설 이름, 외부 ID 형식
- 중복 판별
- 국내 TourAPI contentId 확인

---

## 2. 작업 전에 읽을 파일

반드시:

1. `docs/destination-registration.md` (이 문서)
2. 최신 `destination-import-master.json`

필요할 때:

3. `docs/db/tripbora_schema_reference.md` (컬럼 길이·문자셋을 직접 확인해야 할 때)

master 파일 다루기:

- 사용자가 첨부했거나 경로를 알려준 파일을 쓴다. **없으면 작업을 시작하지 말고 사용자에게 요청한다.**
  - 관리자 → 여행지 관리 → JSON 일괄 등록 → 「마스터 데이터 내보내기」에서 받는다.
- `generatedAt`을 확인한다. 오래된 파일이거나 지역·카테고리가 바뀌었다고 들었다면 새로 내보내 달라고 요청한다.
- 지역명, 카테고리명, amenity code는 **기억이나 추측으로 쓰지 않는다.** master에 실제로 있는 문자열만 그대로 복사한다.
- **DB PK(숫자 ID)는 JSON 어디에도 쓰지 않는다.** master에도 ID는 없다.

### master 파일 구조

| 경로 | 내용 |
|---|---|
| `contract.types` / `contract.seasons` | 허용 type·season |
| `contract.languages` | `ko`, `en`, `ja`, `zh-CN`, `zh-TW` |
| `contract.infoTranslationLanguages` | `en`, `ja`, `zh-CN`, `zh-TW` (info 번역 언어. ko 없음) |
| `contract.limits`, `contract.rules` | 길이 제한, 외부 ID 형식, 국내·해외 규칙 |
| `contract.infoFields.<TYPE>[]` | `name`, `kind`, `maxLength`, `maxBytes`, `translatable`, `fact` |
| `regions.domestic[]` | 대한민국 → 시·도 → 시·군·구 (`name`, `nameEn`, `children`) |
| `regions.overseas[]` | 대륙 → 국가 → 도시·지역 |
| `categoriesByType.<TYPE>[]` | 그 type에서 쓸 수 있는 카테고리 이름 |
| `amenitiesByType.<TYPE>[]` | 그 type에서 쓸 수 있는 편의시설 `{code, name}` |

---

## 3. 작업 단위

- 권장: 한 번에 **10~20개**
- 최대: **30개**. 가져오기 기능의 상한이고, 파일 크기도 1MB 이하여야 한다.
- 요청이 30개를 넘으면 여러 JSON 파일로 나눈다. 예: `destinations-20261008-01.json`, `-02.json`

---

## 4. 판단 규칙

### type

허용값: `ATTRACTION`, `ACCOMMODATION`, `RESTAURANTS`, `CAFE`, `ACTIVITY`, `SHOP`

- 조사한 뒤 가장 적절한 type **하나**를 고른다.
- 두 type 사이에서 애매하면 임의로 확정하지 않는다. JSON에서 빼고 "type 확인 필요"(후보 type과 이유)로 보고한다.
- `CAFE`는 내부적으로 `restaurant_info` / `restaurant_amenities`를 `RESTAURANTS`와 함께 쓴다. 그래도 JSON에는 `"type": "CAFE"`로 정확히 쓴다.
  - 카테고리·편의시설·info 필드는 master의 `CAFE` 항목을 그대로 따른다.

### season

허용값: `SPRING`, `SUMMER`, `FALL`, `WINTER`, `ALL_SEASONS`

- 특정 계절에만 맞는다는 명확한 근거가 없으면 `ALL_SEASONS`를 먼저 검토한다.
- 계절성이 분명한 곳은 그 계절을 고른다. 예: 겨울 한정 스키장, 특정 계절에만 여는 시설.

---

## 5. 지역 (`region`)

`region`은 `{ "country", "city", "district" }`이다. 값은 master `regions` 트리의 `name`을 **글자 그대로** 쓴다.

- **국내**: `country`는 `대한민국`, `city`는 시·도, `district`는 시·군·구다.
  - 예: `{"country": "대한민국", "city": "서울", "district": "종로구"}`
- **해외**: 대륙은 넣지 않는다. 국가부터 쓴다.
  - 예: `{"country": "일본", "city": "후쿠오카", "district": null}`
- master에서 하위 지역(`children`)이 있는 단계는 **반드시 그 아래까지** 쓴다.
- `children`이 비어 있으면 그 단계에서 멈추고 나머지는 `null`이다. 하위가 없는데 값을 넣으면 오류다.
- 가져오기는 최대 3단계(country → city → district)까지만 받는다. master에서 district 아래에 지역이 더 있으면 JSON으로 등록할 수 없으니 보고한다.
- 비슷한 이름으로 맞춰 주지 않는다. "서울특별시"처럼 master에 없는 표기를 쓰지 말고, master에 있는 이름(`서울`)을 쓴다.

**master에 없는 지역은 만들지 않는다.** 실제 장소는 맞지만 그 지역이 master에 없으면 가까운 지역에 억지로 넣지 않는다. JSON에서 빼고 **"지역 마스터 추가 필요"**(국가·도시명)로 보고한다.

---

## 6. 카테고리와 편의시설

### categories / mainCategory

- `categories`는 **1개 이상 필수**다. master `categoriesByType.<TYPE>`에 있는 문자열만 쓴다.
- 없는 카테고리를 만들지 않는다. 같은 값을 두 번 넣지 않는다.
- 여행지 성격과 직접 관련된 것만 고른다. 태그처럼 많이 붙이지 않는다(보통 1~3개).
- `mainCategory`
  - 가장 핵심적인 카테고리가 분명하면 지정한다. 애매하면 `null`이고, 시스템이 기존 규칙으로 대표를 정한다.
  - 지정하면 반드시 `categories` 안에 있어야 한다.

### amenities

- master `amenitiesByType.<TYPE>[].code` 값만 쓴다. 예: `PARKING`, `WIFI`, `TOILET`.
- **실제로 확인된 편의시설만** 넣는다. "관광지니까 화장실이 있겠지", "호텔이니까 와이파이가 있겠지" 같은 추측은 금지한다.
- 확인되지 않으면 넣지 않는다. 빈 배열 `[]`이나 `null`도 된다.

---

## 7. 텍스트 (`translations`)

`translations`의 키는 `ko`, `en`, `ja`, `zh-CN`, `zh-TW`이고, 각 값은 `{ "name", "shortDescription", "description" }`이다.

- 다섯 언어를 모두 채우는 것이 기본이다.
- `ko.name`은 필수다.
- 다른 언어에 `shortDescription`이나 `description`을 넣었다면 그 언어의 `name`도 반드시 넣는다.
- 고유명사는 그 언어에서 실제로 널리 쓰이는 공식·통용 표기를 우선한다. 공식 사이트의 다국어 페이지, 관광청 표기를 참고한다.
- 한국어를 기준으로 쓰고, 다른 언어는 직역투가 아니라 여행 서비스에서 자연스럽게 읽히도록 옮긴다. 원문에 없는 사실은 덧붙이지 않는다.
- 이모지는 쓰지 않는다.

### shortDescription

- 목록·상단에서 장소의 특징을 바로 알 수 있는 **1문장**, **255자 이내**.
- "꼭 가봐야 할", "최고의", "인생 여행지" 같은 과장 표현을 쓰지 않는다. 장소의 핵심 특징을 구체적으로 쓴다.

### description

- 상세페이지 본문이다. 한두 문장이 아니라 여행자가 장소를 이해할 수 있을 만큼 쓴다. 보통 2~4문단이고, 한도는 65,535바이트다.
- 담을 내용:
  - 어떤 장소인지
  - 대표적인 특징
  - 무엇을 볼 수 있는지
  - 역사·문화·자연적 의미
  - 방문했을 때 경험할 수 있는 것
- 금지:
  - 확인하지 않은 운영시간·요금·휴무일을 사실처럼 쓰기
  - 출처에 없는 수치
  - 홍보 문구
  - 다른 여행지 설명을 복사해 이름만 바꾸기

---

## 8. 사실정보와 AI 작성정보

| 구분 | 항목 | 규칙 |
|---|---|---|
| AI가 작성 가능 | `shortDescription`, `description`, 번역, `guide`, `etc`, type·season 후보 | 자연어로 작성. 단 그 안에도 확인 안 된 사실(시간·요금·수치)은 넣지 않는다 |
| 반드시 외부 확인 | 좌표, 운영시간, 휴무일, 입장료, 전화번호, 홈페이지, 주차·반려동물·예약 등 boolean, 좌석 수·객실 수 등 숫자, 체크인·체크아웃, 주요 메뉴, 가격대, `tourApiContentId`, `wikidataQid`, `googlePlaceId`, 지역 | 확인한 값만 넣는다. **확인 못 하면 `null`** |

master `contract.infoFields`의 `fact: true` 필드는 모두 "반드시 외부 확인" 대상이다.

- boolean은 `false`도 사실값이다. "모르면 false"가 아니라 **모르면 `null`**이다.

---

## 9. 외부 ID (`external`)

`external`은 `{ "tourApiContentId", "wikidataQid", "googlePlaceId" }`이다. 모르면 각각 `null`이다.

| | `tourApiContentId` | `wikidataQid` | `googlePlaceId` |
|---|---|---|---|
| 국내 | 허용 | **금지 (오류)** | **금지 (오류)** |
| 해외 | **금지 (오류)** | 허용 | 허용 |

- **`tourApiContentId`**
  - 숫자 문자열이다. 예: `"126508"`
  - TourAPI에서 그 여행지임을 실제로 확인한 경우에만 넣는다.
  - TourAPI를 직접 조회할 수 없으면 `null`로 둔다. API 키를 문서나 JSON에 쓰지 않는다.
  - 미리보기가 TourAPI로 존재 여부를 확인한다. 없는 contentId는 오류, 이름이 다르면 경고다.
- **`wikidataQid`**
  - 형식은 `Q` 뒤에 숫자다. 예: `"Q123456"`
  - 실제 Wikidata 엔티티 페이지를 열어 같은 장소인지 확인한 경우에만 넣는다.
  - 등록할 때 시스템이 Wikidata를 다시 확인한다.
- **`googlePlaceId`**
  - 실제 조회로 확인한 경우에만 넣는다.
  - 시스템은 형식만 확인하므로 미리보기에 "외부 검증되지 않은 Place ID" 경고가 항상 뜬다. 정상이다.
- **ID를 형식에 맞춰 지어내는 것은 절대 금지다.**

---

## 10. 좌표

- `latitude`, `longitude`는 따옴표 없는 숫자다. 예: `37.579617`
- 실제로 확인한 값만 쓴다. 공식 사이트, 공공데이터, Wikidata 좌표 등.
- 둘 중 하나만 넣지 않는다(오류). 확인이 어려우면 **둘 다 `null`**이다.
  - 좌표가 없으면 경고만 뜨고 등록은 된다.
- 도시 중심 좌표나 추정 좌표를 대신 넣지 않는다.
- 국내인데 대한민국 범위를 벗어나면 경고가 뜬다. 위도·경도가 뒤바뀌지 않았는지 확인한다.

---

## 11. 유형별 운영정보 (`info`)

`info`는 master `contract.infoFields.<TYPE>`에 있는 필드만 쓴다. 다른 type의 필드를 넣으면 오류다.

```json
"info": {
  "openingHours": "...",
  "parkingAvailable": null,
  "translations": {
    "en": { "openingHours": "..." }
  }
}
```

- 확인하지 못한 필드는 `null`로 두거나 아예 생략한다.
- 한국어 값은 `info` 바로 아래 필드에 쓴다.

필드 규칙은 master의 각 필드 정의를 그대로 따른다.

| 속성 | 의미 |
|---|---|
| `kind` | `TEXT` 문자열 · `LONG_TEXT` 긴 문자열 · `URL` http(s) 주소 · `BOOLEAN` true/false · `INTEGER` 0 이상 정수 · `RATING` 0.0~5.0, 소수 첫째 자리까지 |
| `maxLength` | **글자 수** 상한 (한글·영문 모두 1자) |
| `maxBytes` | **UTF-8 바이트** 상한 (한글 1자 = 3바이트) |
| `translatable` | `true`면 `info.translations`에 번역을 넣을 수 있다 |
| `fact` | `true`면 실제 출처 확인이 필요하다. `false`(`guide`, `etc`)는 AI가 자연어로 쓸 수 있다 |

주의할 점:

- **RESTAURANTS·CAFE는 한도가 짧다.** 예: `mainMenu` 64자, `openingHours` 64자, `priceRange`·`breakTime`·`closedDays` 32자.
  - 길면 요약하되 의미를 바꾸지 않는다. 요약할 수 없으면 핵심만 남기고 나머지는 `etc`(255자)나 `null`로 처리한다.
- `info`의 한국어 원문 필드에는 이모지 등 4바이트 문자를 넣을 수 없다(원문 테이블이 utf8mb3, 오류).
- `homepageUrl`은 `http://` 또는 `https://`로 시작하고 255자 이하다.

### info.translations

- 언어 키는 `en`, `ja`, `zh-CN`, `zh-TW`만 쓴다. `ko`를 넣으면 오류다.
- `translatable: true`인 필드만 번역한다. `contactNumber`, `homepageUrl`, boolean, 숫자 필드는 번역하지 않는다.
- 번역은 표현만 옮긴다. 시간·금액·요일 같은 값의 의미를 바꾸지 않는다. 예: `09:00~18:00` → `9:00 AM–6:00 PM`은 되지만 시간 자체를 바꾸면 안 된다.
- 번역 길이도 같은 `maxLength`/`maxBytes`를 지킨다.

---

## 12. 근거 (`evidence`)

사실정보를 채웠다면 가능한 한 근거를 남긴다.

```json
"evidence": [
  { "url": "https://example.org/official-visitor-info", "fields": ["openingHours", "admissionFee", "homepageUrl"] },
  { "url": "https://example.org/public-data-page", "fields": ["latitude", "longitude"] }
]
```

- `url`은 필수이고 http(s) 주소, 2,000자 이하다.
- `fields`에는 그 출처로 확인한 JSON 필드 이름을 쓴다. 예: `latitude`, `longitude`, `tourApiContentId`, `wikidataQid`, `googlePlaceId`, `info` 필드 이름.
- evidence는 **DB에 저장되지 않는다.** 미리보기에서 관리자가 검수할 때만 쓴다.
- 사실값이 있는데 evidence가 비어 있으면 경고가 뜬다.
- **실제로 연 페이지의 주소만 쓴다.** 존재하지 않는 출처를 만들지 않는다.

출처 우선순위:

1. 공식 홈페이지
2. 정부·관광청·공공기관
3. TourAPI(한국관광공사)
4. Wikidata / Wikipedia / Wikimedia Commons
5. 신뢰할 수 있는 기타 자료

블로그·리뷰 같은 사용자 작성 콘텐츠만으로 중요한 사실값을 확정하지 않는다.

### 출처끼리 값이 다를 때

- 임의로 하나를 고르지 않는다.
- 가장 공식적이고 최신인 출처를 우선한다.
- 그래도 판단하기 어려우면 그 필드는 `null`로 두고 "확인 필요"로 보고한다.

---

## 13. 이미지

JSON에 이미지 관련 값을 넣지 않는다. 계약에 필드가 없으므로 넣으면 오류다.

- 넣지 않는 것: 이미지 URL, KTO 이미지, Commons 이미지, 대표·슬라이드 이미지, 이미지 라이선스 정보
- 이미지는 등록 후 관리자가 직접 추가한다.

---

## 14. JSON 생성 규칙

항상 **하나의 JSON 객체**를 만든다.

```json
{
  "version": 1,
  "meta": { "generator": "Claude", "generatedAt": "2026-10-08T15:00:00+09:00", "note": "궁궐 2곳, 일본 2곳" },
  "destinations": [ ... ]
}
```

- 최상위 필드는 `version`(항상 `1`), `meta`(선택), `destinations`(1~30개)뿐이다.
- `meta` 필드는 `generator`, `generatedAt`, `note`(모두 문자열)뿐이다.
- 여행지 한 건에 쓸 수 있는 필드:
  - `key`, `type`, `season`, `region`, `latitude`, `longitude`, `external`
  - `categories`, `mainCategory`, `amenities`, `translations`, `info`, `evidence`
- **계약에 없는 필드는 오류다.** 메모·주석·이미지 필드를 임의로 추가하지 않는다. 같은 필드를 두 번 쓰지 않는다.
- 모르는 값은 빈 문자열 `""` 대신 `null`을 쓴다(빈 문자열도 `null`로 처리된다).
- JSON 문법이 정확해야 한다. 주석·끝 쉼표·작은따옴표를 쓰지 않는다.
- 사용자가 "JSON 파일 만들어줘"라고 하면 실제 `.json` 파일로 만든다.
  - UTF-8로 저장한다.
  - 사용자가 위치를 정하지 않았으면 `src/` 밖에 만들고 경로를 알려준다.
  - git에 추가하지 않는다.
- JSON 내용을 바로 응답할 때는 JSON 블록 하나만 주고, JSON 안이나 바로 뒤에 설명문을 붙이지 않는다.

### key

`key`는 DB에 저장되지 않는 작업 행 식별자다. 미리보기·등록 결과에 표시된다.

- 영문·숫자·`.`·`_`·`-`만 쓰고 64자 이하로 한다. 소문자 slug 형태를 권장한다.
  - 예: `gyeongbokgung`, `fukuoka-tower`, `osaka-castle`
- **파일 안에서 중복되지 않게 한다.** 같은 key가 다시 나오면 뒤 행이 오류(INVALID)가 된다. key가 없는 행끼리는 비교하지 않는다.
- 시스템에서는 선택값이지만 이 지침에서는 **항상 쓴다.**

### 한 건 예시 (국내, 구조 확인용)

카테고리·편의시설·지역 이름은 반드시 master 값으로 바꾼다.
`<...>` 부분은 자리 표시이므로 그대로 쓰면 안 된다.

```json
{
  "key": "gyeongbokgung",
  "type": "ATTRACTION",
  "season": "ALL_SEASONS",
  "region": { "country": "대한민국", "city": "서울", "district": "종로구" },
  "latitude": null,
  "longitude": null,
  "external": { "tourApiContentId": null, "wikidataQid": null, "googlePlaceId": null },
  "categories": ["<master ATTRACTION 카테고리명>"],
  "mainCategory": null,
  "amenities": [],
  "translations": {
    "ko":    { "name": "경복궁", "shortDescription": "...", "description": "..." },
    "en":    { "name": "Gyeongbokgung Palace", "shortDescription": "...", "description": "..." },
    "ja":    { "name": "景福宮", "shortDescription": "...", "description": "..." },
    "zh-CN": { "name": "景福宫", "shortDescription": "...", "description": "..." },
    "zh-TW": { "name": "景福宮", "shortDescription": "...", "description": "..." }
  },
  "info": {
    "openingHours": null,
    "closedDays": null,
    "admissionFee": null,
    "contactNumber": null,
    "homepageUrl": null,
    "guide": "...",
    "translations": { "en": { "guide": "..." } }
  },
  "evidence": []
}
```

해외는 `region`이 `{ "country": "일본", "city": "후쿠오카", "district": null }`처럼 바뀌고, `external`에는 확인한 `wikidataQid`나 `googlePlaceId`만 넣는다.

---

## 15. 중복

- "이미 등록돼 있을 것 같다"는 판단으로 여행지를 빼지 않는다. **요청받은 여행지는 모두 JSON에 넣는다.** 중복은 시스템이 판별한다.
  - `DestinationDuplicateService`가 기존 DB와 파일 안 중복을 모두 본다.
- 같은 요청 목록에 같은 장소가 명백히 두 번 있으면 한 건만 만들고 사용자에게 알린다. 예: "경복궁"과 "Gyeongbokgung Palace".

---

## 16. 정보가 부족할 때

- 정보가 부족하다는 이유로 여행지 전체를 빼지 않는다. 확인한 범위까지 쓰고, 확인 못 한 사실값은 `null`로 둔다.
- 다만 등록에 꼭 필요한 값을 정할 수 없으면 억지로 JSON을 만들지 않는다. 그 여행지는 빼고 이유와 함께 보고한다.
  - 어떤 실제 장소인지 식별할 수 없다. 이름이 같은 장소가 여러 곳이면 후보를 제시한다.
  - 국가·지역을 판단할 수 없거나, 그 지역이 master에 없다("지역 마스터 추가 필요").
  - type을 판단할 수 없다(후보 type 제시).
  - master에서 쓸 카테고리를 하나도 정할 수 없다.
- 요청 수 = JSON에 넣은 수 + 제외하고 보고한 수가 되어야 한다.

---

## 17. 최종 응답

정상 생성이면 짧게 답한다.

> 총 12개 여행지 JSON을 생성했습니다. 사실정보를 확인하지 못한 필드는 null로 두었습니다.

그리고 JSON 파일 경로나 JSON 내용을 준다. 확인이 필요한 항목이 있으면 JSON과 따로 짧게 목록으로 알린다.

- 제외한 여행지와 이유: 지역 마스터 추가 필요, type 확인 필요, 장소 식별 불가 등
- 출처끼리 값이 달라 `null`로 둔 필드
- 요청 목록 안의 중복

---

## 18. 금지사항 요약

- DB ID 사용
- master에 없는 지역·카테고리·amenity code 생성
- contentId·QID·Place ID 추측 또는 형식 맞춰 생성
- 좌표 추측(도시 중심 좌표 대입 포함)
- 운영시간·휴무일·입장료·연락처 추측
- boolean·숫자 운영정보 추측(모르면 `false`가 아니라 `null`)
- 이미지 관련 값 넣기
- 존재하지 않는 출처 만들기
- 등록을 통과시키려고 데이터를 임의로 바꾸기
- JSON 계약에 없는 필드 추가

---

## 작업 체크리스트

JSON을 내기 전에 확인한다.

- [ ] 최신 `destination-import-master.json`을 읽었다 (`generatedAt` 확인)
- [ ] 요청 수 = JSON 건수 + 제외 보고 건수, 파일당 30건 이하
- [ ] `type`·`season`이 허용값이다
- [ ] `region`이 master 트리에 있고, 하위 지역이 있는 단계까지 썼다 (해외는 대륙 없이 국가부터)
- [ ] `categories` 1개 이상, 모두 그 type의 master 목록에 있다
- [ ] `mainCategory`는 `null`이거나 `categories` 안에 있다
- [ ] `amenities`는 그 type의 master code이고, 실제로 확인된 것만 넣었다
- [ ] 국내는 `tourApiContentId`만, 해외는 `wikidataQid`·`googlePlaceId`만 넣었고 모두 실제 확인한 값이다
- [ ] 좌표는 둘 다 있거나 둘 다 `null`이다
- [ ] `info`에는 그 type 필드만 있고, `maxLength`·`maxBytes`를 지켰다 (RESTAURANTS·CAFE 특히)
- [ ] `fact: true` 값은 모두 출처에서 확인했고 `evidence`에 남겼다. 모르면 `null`
- [ ] `info.translations`에는 `translatable: true` 필드만, `ko` 없이 넣었다
- [ ] 5개 언어 `name`이 있고 `ko.name`이 비어 있지 않다
- [ ] 이미지 필드와 계약에 없는 필드가 없다. `key`는 파일 안에서 중복되지 않는다
- [ ] JSON 문법이 올바르고, JSON 뒤에 설명문이 없다
