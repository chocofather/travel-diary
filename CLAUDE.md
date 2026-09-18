# CLAUDE.md

이 문서는 이 저장소에서 Claude Code가 작업할 때 따라야 할 프로젝트 지침을 정의합니다.

## 프로젝트

Travel Diary는 Spring Boot 3.4.3 / Java 17 기반의 서버 렌더링 웹 애플리케이션입니다.

Thymeleaf를 사용하며 여행 계획, 여행일기/게시글 작성, 여행지 북마크, 관리자 여행정보 관리 등의 기능을 제공합니다.

데이터베이스는 MySQL을 사용하며 JPA가 아닌 MyBatis(XML Mapper)를 사용합니다.  
인증은 Spring Security Form Login + 서버 세션 방식입니다.

## 명령어

```bash
./gradlew compileJava        # 컴파일
./gradlew test               # 전체 테스트 실행 (JUnit 5 / AssertJ)
./gradlew test --tests "com.example.travlediary.service.event.EventServiceAdminTest"   # 특정 테스트 클래스 실행
./gradlew test --tests "*EventFormTest.editFormSplitsExistingLocalDatesIntoYearMonthDayFields"  # 특정 테스트 메서드 실행
./gradlew bootRun            # 로컬 애플리케이션 실행 (로컬 MySQL 필요)
```

별도의 linter는 설정되어 있지 않습니다.  
코드 검증은 `compileJava`와 `test`를 기본으로 합니다.

## 로컬 환경

- MySQL이 로컬에서 실행 중이어야 합니다.
    - 연결 설정: `src/main/resources/application.yml`
    - 기본 연결 예시: `jdbc:mysql://localhost:3306/mydb`
    - 사용자: `localmaster`

- DB 스키마 레퍼런스:
    - `docs/db/travel_diary_schema_reference.md`
    - 실제 DB를 자동 적용하는 파일이 아니라 현재 스키마를 기록한 DDL 레퍼런스입니다.

- 업로드 파일:
    - `application.yml`의 `custom.upload-path` 경로에 저장합니다.
    - `src/main/resources/static/uploads`
    - 저장소 루트의 `uploads/`
    - 위 경로에 있는 샘플 콘텐츠와 실제 업로드 경로를 혼동하지 않습니다.

- 이메일:
    - 회원가입 이메일 인증 및 비밀번호 재설정에 Gmail SMTP를 사용합니다.
    - `MAIL_USERNAME`, `MAIL_PASSWORD`는 환경변수에서 가져옵니다.

- `application.yml`의:
    - `security.user.name`
    - `security.user.password`

  값(`admin` / `admin`)은 Spring Boot 기본 in-memory 사용자 설정이며, 실제 애플리케이션 관리자 계정이 아닙니다.

  실제 사용자와 권한은 DB에서 관리하며 `CustomUserDetails`를 통해 인증합니다.

## 아키텍처

기능별 기본 계층 구조는 다음과 같습니다.

`Controller -> Service -> Mapper Interface -> Mapper XML -> MySQL`

주요 기능 영역:

- `event`
- `post`
- `board`
- `course`
- `destination`
- `bookmark`
- `comment`
- `notice`
- `faq`
- `inquiry`
- `travelinfo`
- `user`
- `search`
- `recommend`
- `amenity`
- `category`
- `info`

각 기능은 일반적으로 다음 위치에 대응되는 구조를 갖습니다.

- `controller/`
- `service/`
- `repository/`
- `src/main/resources/mapper/`

기능을 수정할 때는 필요한 경우 Controller → Service → Mapper Interface → Mapper XML 흐름을 함께 확인합니다.

### MyBatis 사용

이 프로젝트는 **JPA가 아니라 MyBatis를 사용합니다.**

`repository/<feature>/`의 Mapper Interface와 `resources/mapper/`의 XML Mapper가 대응됩니다.

기존 MyBatis 영역에 JPA/Hibernate를 새로 도입하거나 변환하지 않습니다.  
`AGENTS.md`에서도 MyBatis 영역을 JPA로 변환하는 것을 금지하고 있습니다.

### 관리자 / 사용자 영역 분리

대부분의 기능은 사용자용 Controller와 관리자용 Controller를 분리합니다.

예:

- 사용자:
    - `controller/<feature>/`

- 관리자:
    - `controller/admin/Admin<Feature>Controller`

두 Controller가 동일한 Service/Mapper를 사용하는 경우도 있습니다.

템플릿도 다음처럼 분리합니다.

- 관리자:
    - `templates/admin/<feature>/`

- 사용자:
    - `templates/<feature>/`

보안 기본 정책:

- `/admin/**`
    - `ADMIN` 권한 필요

- `/mypage/**`
    - 로그인 사용자만 접근 가능

관련 기준은 `AGENTS.md`도 함께 따릅니다.

### Security 설정

`config/SecurityConfig.java`는 다음 접근 정책의 기준입니다.

- 공개 경로
- 로그인 필요 경로
- 관리자 전용 경로

또한 필요한 경우 CSRF 처리 대상 POST/DELETE 경로도 regex matcher로 관리합니다.

새로운 상태 변경 endpoint를 추가할 경우:

- `authorizeHttpRequests`
- CSRF matcher

설정이 필요한지 확인합니다.

기본 정책은 `anyRequest().authenticated()`입니다.

### 국가 / 카테고리 데이터

국가 카테고리는 시작 시:

`resources/json/country_categories.json`

에서 읽어옵니다.

`config/CountryCategoryLoader`

가 `@PostConstruct`에서 데이터를 읽고 tree 구조를 flat list로 변환한 뒤 `CountryCategoryMapper`를 통해 처리합니다.

국가 카테고리 ID를 코드에 하드코딩하지 않습니다.

필요한 ID는 Mapper 또는 Service를 통해 조회합니다.

이 규칙은 `AGENTS.md`에서도 명시되어 있습니다.

### 전역 설정

다음 클래스들은 애플리케이션 전반에서 사용되는 공통 기능입니다.

- `config/GlobalModelAttributes`
    - 공통 Thymeleaf model attribute 제공

- `config/GlobalRequestControllerAdvice`
    - Controller Advice 수준의 공통 처리

- `config/WebSecurityIgnoreConfig`
    - static/resource 경로를 Security Filter Chain 자체에서 제외
    - `SecurityConfig`의 `permitAll`과는 다른 개념이므로 구분할 것

- `config/CustomLoginSuccessHandler`
    - 로그인 성공 후 이동 처리

- `config/CustomLogoutSuccessHandler`
    - 로그아웃 성공 후 이동 처리

### Validation 패턴

이벤트 기능을 예로 들면 form DTO:

`dto/EventForm`

이 MyBatis model:

`model/Event`

과 변환됩니다.

Service에서는 단순히 Bean Validation에만 의존하지 않고:

`service/event/EventValidationException`

같은 기능 전용 예외를 사용하는 패턴도 존재합니다.

다른 관리자 CRUD 기능을 확장할 경우 기존 기능의 패턴을 먼저 확인하고 일관되게 구현합니다.

### 파일 업로드

리치 텍스트 에디터 이미지 업로드는:

`api/EditorImageUploadApi`

에서 처리합니다.

게시글/커뮤니티 에디터 등의 이미지 업로드에 사용됩니다.

공용 파일 저장 관련 기능은:

`service/file/`

아래에 있습니다.

사용자가 작성한 HTML 콘텐츠는 저장/렌더링 전에 Jsoup 기반 sanitizing을 거칩니다.

## 작업 규칙

다음 규칙은 `AGENTS.md`의 작업 기준도 포함합니다.

- 작업 시작 전에 `git status --short`로 현재 작업 트리를 확인합니다.
- 사용자가 이미 작업 중인 변경사항을 되돌리지 않습니다.
- 필요한 최소 범위만 수정합니다.
- 수정 대상 파일의 기존 구조와 스타일을 우선 따릅니다.
- 불필요하게 새로운 패턴을 도입하지 않습니다.
- DB에 직접 접속하거나 SQL을 직접 실행하지 않습니다.
- 명시적인 요청 없이 DB 스키마를 변경하지 않습니다.
- DB 구조가 필요하면 `docs/db/travel_diary_schema_reference.md`를 확인합니다.
- Git commit / push를 임의로 수행하지 않습니다.
- 기존 변경사항을 버리기 위한 `git reset`, `git restore`, `git checkout`을 사용하지 않습니다.
- 구현 후 가능하면 관련된 가장 작은 테스트부터 먼저 실행합니다.
- 광범위한 리팩터링으로 기존 기능에 위험을 만들지 않습니다.

구현 완료를 선언하기 전에 기본적으로 다음을 실행합니다.

```bash
./gradlew compileJava
./gradlew test
git diff --check
git status --short
```

## 개발 작업 방식

작업은 작고 검토 가능한 단위로 진행합니다.

- 한 번에 지나치게 많은 파일과 기능을 구현하지 않습니다.
- 여러 단계로 구성된 작업이면 현재 요청받은 단계까지만 완료하고 멈춥니다.
- 사용자가 각 단계를 확인하거나 테스트할 수 있도록 한 번에 하나의 논리적 변경을 우선합니다.
- 사용자의 명시적인 요청 없이 다음 기능이나 추가 개선 작업으로 자동 진행하지 않습니다.

## 분석과 구현 구분

사용자가 다음과 같이 요청한 경우:

- 확인
- 분석
- 원인 조사
- 검토
- 설계
- 계획 제안

파일을 수정하지 않습니다.

분석 단계에서는 답변에 필요한 파일만 확인합니다.

실제 코드 수정은 사용자의 명확한 구현 요청이 있을 때만 진행합니다.

계획이나 설계안을 제시했다는 이유만으로 구현 승인을 받은 것으로 판단하지 않습니다.

## 구현 승인 규칙

사용자가 특정 변경사항을 명확하게 구현해달라고 요청한 경우, 그 요청 자체를 구현 승인으로 간주합니다.

이미 범위가 확정된 구현 작업에서 별도의 brainstorming 또는 설계 승인 절차를 반복해서 요구하지 않습니다.

요청 범위가 명확하다면:

1. 필요한 코드만 확인
2. 현재 구조에 맞게 구현
3. 테스트 및 검증

순서로 바로 진행합니다.

다음 경우에만 추가 질문합니다.

- 핵심 요구사항이 실제로 모호한 경우
- 구현을 위해 사용자가 승인하지 않은 아키텍처 변경이 필요한 경우
- DB 스키마 변경이 새롭게 필요한 경우
- 서로 충돌하는 요구사항 때문에 임의 판단이 위험한 경우

## 컨텍스트 및 토큰 효율

불필요하게 저장소 전체를 반복해서 탐색하지 않습니다.

- 요청과 직접 관련된 파일부터 확인합니다.
- 실제 의존 관계가 필요할 때만 탐색 범위를 넓힙니다.
- 현재 세션에서 이미 확인된 정보를 반복해서 다시 읽지 않습니다.
- 작업과 관계없는 파일을 대량으로 조사하지 않습니다.
- 확인한 파일 목록이나 변경하지 않은 코드에 대한 긴 보고를 하지 않습니다.

구현 완료 보고는 간결하게 유지합니다.

기본적으로 다음 정도만 보고합니다.

- 변경 파일
- 핵심 동작
- 검증 결과
- 남은 문제 또는 사용자가 확인해야 할 사항

이 문서에 이미 정의된 저장소 공통 규칙은 작업 프롬프트에서 반복해서 요구하지 않아도 됩니다.

예:

- Git 제한
- DB 직접 실행 금지
- 기본 검증 명령
- 단계별 작업 규칙

현재 작업에 예외가 필요한 경우에만 별도로 언급합니다.

## 검증 순서

사용자가 별도의 검증 방식을 요청하지 않은 경우 다음 순서를 따릅니다.

1. 현재 요청된 작은 변경만 구현합니다.
2. 가능하면 가장 관련 있는 작은 테스트부터 실행합니다.
3. 변경 diff를 확인하여 의도하지 않은 수정이 없는지 점검합니다.
4. 완료 전 다음을 실행합니다.

```bash
./gradlew compileJava
./gradlew test
git diff --check
git status --short
```

5. UI 또는 브라우저 동작이 필요한 기능은 사용자가 직접 최종 확인합니다.
6. 명시적인 요청 없이 commit 또는 push하지 않습니다.
7. 사용자가 commit/push를 요청하더라도 Git write 작업을 자동으로 실행하지 않습니다.
    - 기본적으로 필요한 Git 명령을 사용자에게 제공합니다.
    - 사용자가 Claude Code에게 직접 실행하라고 명확히 요청한 경우에만 실행합니다.

## 브라우저 / 수동 확인

다음과 같은 기능은 자동 테스트만으로 완전히 검증됐다고 주장하지 않습니다.

- UI
- JavaScript
- WebSocket
- 실시간 기능
- 인증 / 세션
- 기타 브라우저 상태에 의존하는 기능

자동 검증이 성공한 후에는 자동 테스트로 확인하기 어려운 핵심 동작만 짧게 알려줍니다.

불필요하게 길거나 당연한 브라우저 테스트 체크리스트를 반복하지 않습니다.

사용자가 브라우저에서 정상 동작한다고 확인했다면, 별도의 미해결 문제가 없는 한 해당 단계는 완료된 것으로 봅니다.

브라우저 확인이 끝났다는 이유로 다음 기능을 임의로 시작하지 않습니다.  
사용자의 다음 지시를 기다립니다.

## 비밀정보 및 인증정보

다음 정보를 소스 코드, 문서, 테스트, 프롬프트 또는 Git 추적 파일에 기록하지 않습니다.

- 비밀번호
- API Key
- SMTP 비밀번호
- Access Token
- Refresh Token
- Secret Key
- Private Key
- 기타 인증정보

비밀정보는 환경변수 또는 Git에서 제외된 로컬 설정을 사용합니다.

작업에 필요하지 않은 경우 사용자에게 실제 secret 값을 붙여넣도록 요청하지 않습니다.

명령 출력이나 설정 파일에서 secret 값이 발견되더라도 완료 보고에 그대로 다시 작성하지 않습니다.

## 소스 파일 수정 방식

소스 파일 수정 시 Python/Perl 스크립트, `sed` 또는 기타 대량 문자열 치환 명령을 사용하지 않습니다.

수정 대상 파일을 직접 편집하고, 각 변경을 작고 검토 가능한 범위로 유지합니다.

## DB 및 스키마 변경 규칙

DB 구조와 관련된 작업을 할 때는 코드만 보고 현재 스키마를 추측하지 않습니다.

다음 파일을 현재 DB 구조의 기준 레퍼런스로 사용합니다.

`docs/db/travel_diary_schema_reference.md`

다음 작업을 수행하기 전에는 관련 테이블 섹션을 반드시 확인합니다.

- Model 변경
- Mapper Interface / Mapper XML 변경
- JOIN 또는 SQL 작성
- 신규 컬럼 사용
- FK / INDEX / CHECK 제약 관련 작업
- 신규 테이블 설계
- DB 저장 구조 변경

스키마 변경이 필요한 경우 다음 순서를 따릅니다.

1. 현재 `travel_diary_schema_reference.md`와 관련 코드를 확인합니다.
2. 필요한 DDL을 `.sql` 파일로 작성하거나 사용자에게 제안합니다.
3. Claude Code가 DB에 직접 접속하거나 SQL을 실행하지 않습니다.
4. 사용자가 MySQL Workbench에서 직접 SQL을 실행합니다.
5. 사용자가 실제 DB 적용 완료를 확인한 뒤에만
   `travel_diary_schema_reference.md`를 최종 적용된 DDL에 맞게 갱신합니다.
6. 그 이후 Java / Mapper / UI 구현을 진행합니다.

사용자가 DB 적용 완료를 확인하기 전에
스키마 레퍼런스를 실제 적용된 것처럼 미리 수정하지 않습니다.

코드와 스키마 레퍼런스가 서로 충돌하거나
현재 구조를 확실히 판단할 수 없는 경우 임의로 추측하지 말고 사용자에게 알립니다.

## UI / UX 디자인 원칙

Travel Diary의 UI는 실제 운영 서비스 수준의 깔끔한 사용자 경험을 목표로 합니다.

기본 방향:

- 흰색 / 밝은 neutral 배경 중심
- 과도한 갈색 테마 사용 금지
- 과도한 그림자, gradient, animation 사용 금지
- 버튼은 compact하게 유지
- hover 효과는 약하게 사용
- 모바일 반응형 필수
- 정보 위계와 여백을 명확하게 구성
- 기능이 많더라도 관리자 도구처럼 난잡하게 보이지 않게 구성
- "개인 포트폴리오 작품 전시" 같은 과장된 디자인보다 실제 상용 서비스 UI를 우선

기존 화면을 수정할 때는 새로운 디자인 시스템을 임의로 만들기보다
현재 사이트의 공통 header, button, card, form, spacing 스타일을 우선 재사용합니다.

사용자가 제공한 스크린샷이나 구체적인 UI 요구가 있으면
기존 화면보다 해당 요구를 우선합니다.

## 검색 UX 규칙

일반 사용자용 검색은 상단 header의 통합검색(`/search`)을 기본으로 합니다.

`/destinations`, `/travel-info` 등 개별 목록 페이지에
별도의 검색창을 임의로 추가하지 않습니다.

다국어 검색 기능도 가능하면 기존 통합검색 경로를 확장합니다.

관리자 전용 관리 화면이나 기능상 별도 검색이 반드시 필요한 경우는 예외로 할 수 있지만,
일반 사용자 화면에는 중복 검색 UI를 만들지 않습니다.

## 테스트 작성 기준

작업 위험도에 따라 테스트 범위를 조절합니다.

### 단순 UI / CSS / 표시 수정

- 신규 테스트를 억지로 추가하지 않습니다.
- 기존 관련 테스트 유지
- `compileJava`
- 전체 테스트
- 사용자의 브라우저 확인

위주로 검증합니다.

### Controller / JavaScript / 상태 분기 등 중간 수준 변경

- 핵심 회귀 테스트 1~2개 정도를 우선합니다.
- 모든 세부 동작을 과도하게 테스트하지 않습니다.

### DB 저장 / 트랜잭션 / 파일 업로드 / fallback / 인증 / 보안 변경

- 핵심 동작을 테스트로 먼저 고정하는 것을 우선합니다.
- 실패 시 rollback, 권한, 소유권, 데이터 정합성 같은 위험 경계를 반드시 검증합니다.

테스트 개수를 늘리는 것 자체를 목표로 하지 않습니다.
실제 회귀 위험이 있는 동작을 보호하는 테스트를 우선합니다.

## 기존 구조 재사용

새 기능을 구현하기 전에 같은 목적의 기존 구현이 있는지 관련 범위 안에서 먼저 확인합니다.

가능하면 기존:

- Service
- Mapper
- Fragment
- Thymeleaf component
- CSS class
- JavaScript module
- 파일 저장 Service
- preview / renderer

를 재사용합니다.

비슷한 기능을 새로 복제하거나
같은 데이터를 처리하는 두 번째 구현을 만들지 않습니다.

단, 재사용을 위해 광범위한 리팩터링이 필요한 경우에는
현재 작업 범위를 벗어나므로 먼저 사용자에게 알립니다.

## 응답 언어

사용자와의 모든 대화와 작업 완료 보고는 한국어로 작성합니다.

- 분석 결과
- 구현 진행 상황
- 오류 원인 설명
- 변경 파일 설명
- 테스트 및 검증 결과
- 사용자에게 필요한 수동 확인 안내

모두 한국어로 작성합니다.

단, 다음은 실제 코드와 프로젝트 표기를 그대로 유지합니다.

- 클래스명
- 메서드명
- 변수명
- DB 테이블/컬럼명
- URL
- 파일 경로
- 상태값 및 Enum 값
- 명령어
- 오류 메시지를 정확히 인용해야 하는 경우

영어로 된 기술 용어를 억지로 번역할 필요는 없지만,
설명 문장 자체는 한국어로 작성합니다.