# Diary PDF Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 본인 다이어리의 화면 디자인을 보존한 PDF를 내려받게 하되, 먼저 읽기 화면의 복잡한 내지 한 장을 한 페이지 PDF로 정확히 출력하는 Phase 1을 검증한다.

**Architecture:** 서버가 이미 소유권을 확인해 렌더한 읽기 화면의 `.diary-sheet`를 유일한 렌더 소스로 삼는다. 브라우저에서 대상 시트를 고정 크기 off-screen clone으로 복제하고, 폰트·이미지·마스킹테이프 준비를 확인한 뒤 `html-to-image`로 PNG를 만들고 `jsPDF`의 41:38 사용자 정의 페이지에 비율을 유지해 배치한다. Phase 1 결과가 시각 검증을 통과한 뒤 같은 캡처 함수에 표지와 전체 내지 순회만 추가한다.

**Tech Stack:** Java 17, Spring Boot 3.4.3, Thymeleaf, 기존 diary DOM/CSS, WebJars, html-to-image 1.11.13, jsPDF 3.0.1, JUnit 5, AssertJ

**Spec:** 2026-09-16 대화에서 승인된 다이어리 PDF 설계와 본 문서의 Phase 1 범위

## Global Constraints

- DB와 스키마는 변경하지 않는다. 기존 `diaries`, `diary_pages`, `diary_elements` 데이터만 읽기 화면을 통해 재사용한다.
- 새 PDF 서버 endpoint, 외부 PDF 변환 서비스, CDN 런타임 의존성, Node/npm 빌드 체인을 만들지 않는다.
- 기존 editor/drag/save 로직과 표지 NOTE 불일치 영역은 수정하지 않는다.
- PDF 전용 페이지 렌더러를 복제하지 않는다. 현재 `.diary-sheet`와 기존 CSS를 캡처한다.
- Phase 1에서는 표지, 전체 페이지 병합, 진행률 UI, 저용량 재시도를 구현하지 않는다.
- 저장소 규칙에 따라 commit/push를 하지 않는다. 아래 각 작업은 테스트와 diff 검토로 경계를 확인한다.

---

## Phase 1 — 복잡한 내지 1페이지 PDF 출력 검증

### Task 1: PDF 클라이언트 자산 계약을 테스트로 고정

**Files:**
- Create: `src/test/java/com/example/travlediary/controller/diary/DiaryPdfExportAssetTest.java`
- Inspect: `src/main/resources/templates/diary/detail.html`
- Inspect: `src/main/resources/static/js/diary-tape-repeat.js`

- [ ] 읽기 모드에만 나타나는 페이지별 `PDF 테스트` 버튼이 대상 page order를 가진다는 실패 테스트를 작성한다.
- [ ] 템플릿이 로컬 WebJar 경로의 `html-to-image`, `jsPDF`와 `/js/diary-pdf.js`를 순서대로 로드한다는 실패 테스트를 작성한다.
- [ ] `diary-pdf.js`가 현재 `.diary-sheet`를 선택하고, 기준 폭 576px의 off-screen clone을 사용하며, 원본 DOM을 직접 스타일 변경하지 않는다는 실패 테스트를 작성한다.
- [ ] `document.fonts.ready`, 사용 글꼴 확인, 이미지 load/decode, 마스킹테이프 완성 확인, 실패 시 다운로드 중단 계약을 테스트한다.
- [ ] `./gradlew test --tests "*DiaryPdfExportAssetTest"`를 실행해 구현 전 실패를 확인한다.

### Task 2: 로컬 WebJar 라이브러리 도입

**Files:**
- Modify: `build.gradle`
- Create: `docs/licenses/diary-pdf-third-party.md`

- [ ] `org.webjars.npm:html-to-image:1.11.13`과 `org.webjars.npm:jspdf:3.0.1`을 고정 버전으로 추가한다.
- [ ] 두 MIT 라이브러리의 이름, 버전, 원본 저장소, 배포 경로와 라이선스 위치를 기록한다.
- [ ] JAR 내용을 확인해 실제 UMD 파일 경로와 동봉 라이선스를 검증한다.
- [ ] WebJar 경로만으로 자산이 제공되고 CDN URL을 추가하지 않았음을 테스트한다.

### Task 3: 현재 페이지 DOM에 PDF 대상을 연결

**Files:**
- Modify: `src/main/resources/templates/diary/detail.html`

- [ ] 읽기 화면의 각 실제 내지 `.diary-sheet`에 page order 식별자를 추가한다.
- [ ] 각 페이지의 기존 action 영역에 개발 단계임을 알리는 `PDF 테스트` 버튼을 추가한다.
- [ ] 버튼과 페이지 root를 안정적으로 연결하되 툴바, 페이지 이동 화살표, 편집 버튼은 `.diary-sheet` 밖에 유지한다.
- [ ] 다이어리 제목과 page order만 DOM dataset으로 전달하고 별도 데이터 조회 endpoint는 추가하지 않는다.
- [ ] 템플릿 계약 테스트를 실행해 버튼이 편집 화면과 표지에 나타나지 않는지 확인한다.

### Task 4: 한 페이지 캡처와 PDF 생성 구현

**Files:**
- Create: `src/main/resources/static/js/diary-pdf.js`

- [ ] 클릭한 버튼이 가리키는 현재 `.diary-sheet`를 찾아 즉시 중복 실행을 막고 상태 문구를 표시한다.
- [ ] 대상 DOM을 화면 밖 컨테이너에 clone하고 폭 576px, 높이 `576 * 38 / 41`로 고정해 viewport와 독립시킨다.
- [ ] clone의 마스킹테이프가 기존 `window.diaryTape.render` 결과를 포함하는지 확인하고 누락 시 같은 렌더러를 호출한다.
- [ ] `document.fonts.ready` 후 clone에서 실제 쓰는 `font-family`, `font-weight`, `font-style` 조합을 수집해 `document.fonts.check`로 검증한다.
- [ ] clone 안의 모든 `<img>`에 대해 완료 여부를 확인하고 `decode()` 또는 load/error를 기다린다. 자연 크기가 없는 이미지나 decode 실패가 하나라도 있으면 생성을 중단한다.
- [ ] 한 animation frame을 추가로 기다려 gradient, pseudo-element, 반복 tape 레이아웃이 계산된 뒤 캡처한다.
- [ ] `htmlToImage.getFontEmbedCSS`를 한 번 계산해 `toPng`에 전달하고 `pixelRatio: 2`, 고정 width/height, cache bust 비활성으로 PNG를 생성한다.
- [ ] jsPDF 사용자 정의 페이지 `[200, 200 * 38 / 41]`mm에 PNG를 여백 없이 동일 비율로 배치해 crop/stretch 없이 한 페이지 PDF로 저장한다.
- [ ] 파일명을 `{정리된_다이어리제목}_page_{pageOrder}.pdf`로 만들고 Windows/macOS 금지 문자, 제어문자, 끝 점·공백을 정리한다.
- [ ] 성공·실패 뒤 off-screen clone과 버튼 상태를 항상 복구하고, 실패 시 깨진 파일을 저장하지 않고 사용자에게 원인을 알린다.
- [ ] 자산 계약 테스트를 실행해 통과를 확인한다.

### Task 5: 실제 브라우저와 PDF 시각 검증

**Files:**
- Create locally for evidence: `output/pdf/{sanitized-title}_page_{pageOrder}.pdf`
- Create locally for evidence: `output/pdf/phase1-source-page.png`
- Create locally for evidence: `output/pdf/phase1-pdf-page.png`

- [ ] 로컬 애플리케이션의 본인 다이어리 읽기 화면에서 줄노트, Quill 서식, custom font, PHOTO, STICKER, MASKING_TAPE, 회전, z-index, 페이지 장식·번호가 함께 있는 페이지를 연다.
- [ ] 원본 대상 `.diary-sheet`를 기준 크기로 캡처하고 `PDF 테스트` 버튼으로 실제 PDF를 생성한다.
- [ ] Poppler가 있으면 `pdftoppm`, 없으면 운영체제 PDF 렌더러로 PDF 첫 페이지를 PNG로 렌더하고 원본 캡처와 나란히 확인한다.
- [ ] custom font fallback, 한글 깨짐, 사진/스티커/테이프 위치·크기·회전·겹침, 줄 baseline, 페이지 번호·배경, pseudo-element/mask/gradient, 흐림·잘림을 확인한다.
- [ ] html-to-image가 재현하지 못하는 필수 CSS가 있으면 Phase 2를 시작하지 않고 해당 요소와 브라우저를 기록한다.

### Task 6: Phase 1 검증 마무리

**Files:**
- Review: all Phase 1 changes

- [ ] `./gradlew compileJava`를 실행한다.
- [ ] `./gradlew test`를 실행한다.
- [ ] `git diff --check`를 실행한다.
- [ ] `git status --short`와 `git diff --stat`으로 범위가 Phase 1에 한정됐는지 확인한다.
- [ ] 계획 문서와 구현에서 미완성 표시, 생략 placeholder, 미구현 분기를 검색한다.
- [ ] 실제 PDF 경로, 캡처 DOM, WebJar 관리 방식, 폰트·이미지 대기 방식, 시각 차이, Phase 2 진입 가능 여부를 결과로 남긴다.

---

## Phase 2 — 표지와 전체 내지 PDF로 확장

> Phase 1의 필수 시각 요소가 실제 브라우저 검증을 통과한 뒤 별도 요청으로 수행한다.

### Task 7: 표지와 모든 내지의 순서 정의

**Files:**
- Modify: `src/main/resources/templates/diary/detail.html`
- Modify: `src/main/resources/static/js/diary-pdf.js`
- Test: `src/test/java/com/example/travlediary/controller/diary/DiaryPdfExportAssetTest.java`

- [ ] 기존 표지 DOM을 캡처 가능한 root로 표시하고 기존 표지 CSS를 그대로 재사용한다.
- [ ] 현재 펼침만 DOM에 있는 구조에서 전체 페이지를 순서대로 준비할 최소 방식을 정한다. 기존 Thymeleaf fragment 재사용으로 충분한 경우에만 소유권 보호가 적용된 내부 렌더 경로를 추가한다.
- [ ] 표지 1장 뒤에 `page_order` 오름차순 내지를 한 장씩 배치하는 순서를 테스트한다.

### Task 8: 순차 캡처와 다페이지 병합

**Files:**
- Modify: `src/main/resources/static/js/diary-pdf.js`
- Modify: `src/main/resources/templates/diary/detail.html`

- [ ] 한 번에 한 페이지만 clone·캡처하고 즉시 bitmap 참조를 해제해 최대 메모리를 제한한다.
- [ ] 표지와 내지 비율을 각 PDF page에 유지하고 전체를 한 파일로 병합한다.
- [ ] `PDF 만드는 중... 3/8` 진행 상태와 취소 불가능 구간을 명확히 표시한다.
- [ ] 개별 페이지 리소스 실패 시 전체 다운로드를 중단하고 실패 page order를 표시한다.

### Task 9: 다페이지 품질·메모리 검증

**Files:**
- Test: `src/test/java/com/example/travlediary/controller/diary/DiaryPdfExportAssetTest.java`
- Create locally for evidence: `output/pdf/{sanitized-title}.pdf`

- [ ] 짧은 다이어리와 사진이 많은 긴 다이어리에서 페이지 순서, 최대 메모리, 생성 시간, PDF 용량을 측정한다.
- [ ] 데스크톱 Chrome과 모바일 Safari/Chrome에서 다운로드 동작과 메모리 실패를 확인한다.
- [ ] 기본 pixelRatio 2 결과가 과도하게 큰 경우에만 사용자 승인 후 저용량 재시도 정책을 별도 단계로 설계한다.
- [ ] 전체 검증 명령과 PDF 렌더 이미지 비교를 반복한 뒤 기능 공개 여부를 결정한다.
