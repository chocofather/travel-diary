(function (root) {
  // 해외(Wikidata) 일괄 등록: 검색 → 여러 곳 선택 → 등록 전 검토 → 여행지별 등록 → 결과.
  // 서버는 여행지 한 곳씩 기존 단건 등록 경로로 저장한다. 이 화면은 선택·검토 상태와 진행 순서만 관리한다.

  const DONE = ['SUCCESS', 'DUPLICATE'];
  /** 진행 중: 보내는 중 · 요청 제한으로 대기 중 · 자동 재시도 중 */
  const IN_PROGRESS = ['RUNNING', 'WAITING', 'RETRYING'];

  /**
   * 검토 행 상태. 임의의 기본값을 넣지 않으므로 유형·시즌·지역·한국어 이름이 비면 '확인 필요'다.
   * @returns {{state: 'done'|'loading'|'needs'|'ready', problems?: string[]}}
   */
  function rowState(row) {
    if (DONE.includes(row.result?.status)) return {state: 'done'};
    if (row.reviewState === 'loading' || row.photosState === 'loading') return {state: 'loading'};
    if (row.reviewState === 'error') return {state: 'needs', problems: ['검토 정보 다시 불러오기']};
    const problems = [];
    if (!row.review?.names?.ko && !String(row.koreanName || '').trim()) problems.push('한국어 이름');
    if (!row.review?.autoRegionId && !row.regionId) problems.push('지역');
    if (!row.type) problems.push('유형');
    if (!row.season) problems.push('시즌');
    return problems.length ? {state: 'needs', problems} : {state: 'ready'};
  }

  function summarize(rows) {
    const count = {total: rows.length, ready: 0, needs: 0, loading: 0, fresh: 0,
      queued: 0, running: 0, waiting: 0, success: 0, duplicate: 0, failed: 0};
    for (const row of rows) {
      const {state} = rowState(row);
      if (state !== 'done') count[state]++;
      if (state === 'ready' && !row.result) count.fresh++;
      const status = row.result?.status;
      if (status === 'QUEUED') count.queued++;
      if (IN_PROGRESS.includes(status)) count.running++;
      if (status === 'WAITING') count.waiting++;
      if (status === 'SUCCESS') count.success++;
      if (status === 'DUPLICATE') count.duplicate++;
      if (status === 'FAILED') count.failed++;
    }
    return count;
  }

  /** 등록 요청 본문. 자동 매핑된 지역을 바꾸지 않았으면 regionId 를 보내지 않는다. */
  function registerPayload(row) {
    return {
      qid: row.qid, type: row.type, season: row.season, regionId: row.regionId || null,
      koreanName: String(row.koreanName || '').trim() || null,
      photoFileName: row.photo?.fileName || null
    };
  }

  /** 요청 제한으로 자동 재시도하는 한도(여행지마다). 넘으면 사유와 함께 실패로 두고 관리자가 다시 누른다. */
  const AUTO_RETRY_LIMIT = 5;
  const MAX_TOTAL_WAIT_MS = 5 * 60 * 1000;

  /**
   * 여행지를 concurrency 곳씩 등록한다. 서버가 RATE_LIMITED(외부 API 요청 제한, 저장 안 됨)로 답하면
   * 모든 작업이 그 시간만큼 함께 멈춘 뒤 그 여행지만 다시 보낸다. 성공·이미 등록됨·실패한 여행지는 다시 보내지 않는다.
   *
   * @param options.send        row → Promise<서버 ItemResult>
   * @param options.sleep       ms → Promise (대기)
   * @param options.now         () → 현재 시각(ms)
   * @param options.onChange    상태가 바뀔 때마다 부른다
   * @param options.isCancelled 페이지를 벗어나는 등 더 보내지 말아야 하면 true
   */
  async function runRegistrationQueue(targets, options) {
    const {send, sleep, now, onChange = () => {}, isCancelled = () => false, concurrency = 2,
      retryLimit = AUTO_RETRY_LIMIT, maxWaitMs = MAX_TOTAL_WAIT_MS} = options;
    const queue = [...targets];
    let pauseUntil = 0;
    let pauseService = '';
    for (const row of targets) {
      row.result = {status: 'QUEUED'};
      row.autoRetries = 0;
      row.waitedMs = 0;
    }
    onChange();
    const worker = async () => {
      while (queue.length && !isCancelled()) {
        const row = queue.shift();
        while (!isCancelled()) {
          const wait = pauseUntil - now();
          if (wait > 0) {
            // 다른 여행지가 받은 제한도 함께 지킨다. 같은 API를 계속 불러 제한을 늘리지 않는다.
            row.result = {status: 'WAITING', until: pauseUntil, service: pauseService, attempt: row.autoRetries};
            onChange();
            await sleep(wait);
            continue;
          }
          row.result = {status: row.autoRetries ? 'RETRYING' : 'RUNNING', attempt: row.autoRetries};
          onChange();
          let result;
          try {
            result = await send(row);
          } catch (error) {
            result = {status: 'FAILED', message: error.message};
          }
          if (result.status !== 'RATE_LIMITED') {
            row.result = {status: result.status, message: result.message, destinationId: result.destinationId};
            onChange();
            break;
          }
          const waitMs = Math.max(1000, (Number(result.retryAfterSeconds) || 1) * 1000);
          if (row.autoRetries >= retryLimit || row.waitedMs + waitMs > maxWaitMs) {
            row.result = {status: 'FAILED', message: `${result.message || '외부 API 요청 제한'} `
                + `자동 재시도 ${row.autoRetries}회 후에도 풀리지 않아 멈췄습니다. 잠시 뒤 '실패한 여행지 재시도'를 눌러 주세요.`};
            onChange();
            break;
          }
          row.autoRetries++;
          row.waitedMs += waitMs;
          if (now() + waitMs > pauseUntil) {
            pauseUntil = now() + waitMs;
            pauseService = result.service || '';
          }
        }
      }
    };
    await Promise.all(Array.from({length: concurrency}, worker));
    if (isCancelled()) {
      for (const row of targets) {
        if (row.result?.status === 'QUEUED' || IN_PROGRESS.includes(row.result?.status)) {
          row.result = {status: 'FAILED', message: '페이지를 벗어나 등록을 멈췄습니다.'};
        }
      }
    }
  }

  root.TripBoraWikidataBulkPlanner = {rowState, summarize, registerPayload, runRegistrationQueue};
  root.TravelDiaryWikidataBulkPlanner = root.TripBoraWikidataBulkPlanner; // legacy alias (P10a 제거)
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {rowState, summarize, registerPayload, runRegistrationQueue, AUTO_RETRY_LIMIT};
  }
  if (typeof document === 'undefined') return;

  document.addEventListener('DOMContentLoaded', () => {
    const page = document.querySelector('[data-wikidata-bulk]');
    if (!page) return;
    const apiBase = '/admin/api/wikidata/bulk';
    const domesticRootId = page.dataset.domesticRootId || '';
    const picker = window.TripBoraCommonsPhotoPicker || window.TravelDiaryCommonsPhotoPicker; // legacy fallback (P10a 제거)
    const $ = selector => page.querySelector(selector);
    const searchForm = $('[data-bulk-search-form]');
    const keywordInput = $('[data-bulk-keyword]');
    const searchButton = $('[data-bulk-search]');
    const status = $('[data-bulk-status]');
    const resultCount = $('[data-bulk-result-count]');
    const resultRows = $('[data-bulk-rows]');
    const moreButton = $('[data-bulk-more]');
    const selectedCount = $('[data-bulk-selected-count]');
    const clearButton = $('[data-bulk-clear]');
    const pageSelectButton = $('[data-bulk-page-select]');
    const pageClearButton = $('[data-bulk-page-clear]');
    const pageToggle = $('[data-bulk-page-toggle]');
    const pageCount = $('[data-bulk-page-count]');
    const {pageSelection, applyPageSelection} =
        window.TripBoraBulkPageSelection || window.TravelDiaryBulkPageSelection; // legacy fallback (P10a 제거)
    const openReviewButton = $('[data-bulk-open-review]');
    const chips = $('[data-bulk-chips]');
    const review = $('[data-bulk-review]');
    const reviewRows = $('[data-bulk-review-rows]');
    const summary = $('[data-bulk-summary]');
    const registerButton = $('[data-bulk-register]');
    const retryButton = $('[data-bulk-retry]');
    const registerStatus = $('[data-bulk-register-status]');
    const commonType = $('[data-bulk-common-type]');
    const commonSeason = $('[data-bulk-common-season]');
    const photoTemplate = $('[data-bulk-photo-template]');
    const typeOptions = Array.from(commonType.options).filter(option => option.value)
      .map(option => [option.value, option.textContent]);
    const seasonOptions = Array.from(commonSeason.options).filter(option => option.value)
      .map(option => [option.value, option.textContent]);

    /** 검색어를 바꿔도 남는 선택. qid → 행 상태. */
    const rows = new Map();
    let results = [];
    let search = {query: null, nextOffset: null, generation: 0, excluded: 0, total: 0, limited: false};
    let running = false;
    /** 페이지를 벗어나면 새 등록 요청을 보내지 않는다. 이미 저장된 여행지는 그대로 남는다. */
    let leaving = false;
    const reviewQueue = limiter(3);
    const regionCache = new Map();

    function element(tag, className, text) {
      const node = document.createElement(tag);
      if (className) node.className = className;
      if (text != null) node.textContent = text;
      return node;
    }

    function limiter(max) {
      let active = 0;
      const queue = [];
      const next = () => {
        if (active >= max || !queue.length) return;
        active++;
        const {task, resolve, reject} = queue.shift();
        task().then(resolve, reject).finally(() => { active--; next(); });
      };
      return task => new Promise((resolve, reject) => { queue.push({task, resolve, reject}); next(); });
    }

    function csrfHeaders(method) {
      if (method === 'GET') return {};
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      const header = document.querySelector('meta[name="_csrf_header"]')?.content;
      return token && header ? {[header]: token} : {};
    }

    async function requestJson(url, {method = 'GET', body} = {}) {
      let response;
      try {
        response = await fetch(url, {
          method,
          headers: {Accept: 'application/json', ...(body ? {'Content-Type': 'application/json'} : {}),
            ...csrfHeaders(method)},
          body: body ? JSON.stringify(body) : undefined
        });
      } catch (_) {
        throw new Error('서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
      if (response.redirected && new URL(response.url).pathname.startsWith('/login')) {
        throw new Error('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.');
      }
      const payload = await response.json().catch(() => null);
      if (!response.ok) {
        if (response.status === 403) throw new Error('요청 권한 또는 보안 토큰을 확인할 수 없습니다. 새로고침한 뒤 다시 시도해 주세요.');
        throw new Error(payload?.message || `요청을 처리하지 못했습니다. (HTTP ${response.status})`);
      }
      if (payload == null) throw new Error('서버 응답을 확인하지 못했습니다.');
      return payload;
    }

    function safeImage(url, className, alt) {
      try {
        const parsed = new URL(url);
        if (parsed.protocol === 'https:' && ['upload.wikimedia.org', 'thumb.wikimedia.org', 'commons.wikimedia.org']
          .includes(parsed.hostname)) {
          const image = element('img', className);
          image.src = parsed.href;
          image.alt = alt;
          image.loading = 'lazy';
          image.addEventListener('error', () => image.replaceWith(element('span', `${className} is-empty`, '없음')));
          return image;
        }
      } catch (_) {
        // 잘못된 주소는 그리지 않는다.
      }
      return element('span', `${className} is-empty`, '없음');
    }

    // ───────────── 1. 검색과 선택 ─────────────

    function showStatus(text, isError = false) {
      status.textContent = text;
      status.classList.toggle('is-error', isError);
    }

    // 키워드 검색과 지역별 탐색이 같은 후보 표·선택 목록을 쓴다. query: {mode:'keyword', keyword} | {mode:'region', regionId, cityQid, label}
    function queryUrl(query, offset) {
      return query.mode === 'region'
        ? `${apiBase}/regions/candidates?regionId=${encodeURIComponent(query.regionId)}`
          + (query.cityQid ? `&cityQid=${encodeURIComponent(query.cityQid)}` : '') + `&offset=${offset}`
        : `${apiBase}/search?keyword=${encodeURIComponent(query.keyword)}&offset=${offset}`;
    }

    function setBusy(busy) {
      searchButton.disabled = busy;
      regionSearchButton.disabled = busy || !resolved?.qid;
      moreButton.disabled = busy;
    }

    function resultMessage() {
      const {query, excluded, total, limited, nextOffset} = search;
      if (query.mode === 'region') {
        if (!total) return `${query.label}: Wikidata의 행정구역·분류로 확인된 여행지가 없습니다. 여행지 검색으로 찾아 추가해 주세요.`;
        return `${query.label}: 여행지 ${total}곳${limited ? '(상한까지)' : ''} 중 ${results.length}곳 표시`
          + (excluded ? ` · 장소 정보를 확인하지 못한 ${excluded}곳 제외` : '') + '. 등록할 여행지를 고르세요.';
      }
      return results.length
        ? `장소로 확인된 후보 ${results.length}건${excluded ? ` · 장소가 아닌 결과 ${excluded}건 제외` : ''}. 등록할 여행지를 고르세요.`
        : nextOffset != null ? '이 범위에는 장소 후보가 없습니다. 검색 결과 더 보기로 이어서 찾아보세요.'
          : '장소로 확인된 검색 결과가 없습니다. 다른 한국어 또는 영어 명칭으로 검색해 주세요.';
    }

    async function runQuery(query, append) {
      const generation = append ? search.generation : ++search.generation;
      const offset = append ? search.nextOffset : 0;
      if (!append) {
        results = [];
        renderResults();
        moreButton.hidden = true;
      }
      setBusy(true);
      showStatus(append ? '후보를 더 불러오는 중입니다.'
        : query.mode === 'region' ? `${query.label}의 여행지를 Wikidata에서 찾는 중입니다. 처음 조회하는 지역은 10초 이상 걸릴 수 있습니다.`
          : 'Wikidata에서 검색하고 장소인지 확인하는 중입니다.');
      try {
        const data = await requestJson(queryUrl(query, offset));
        // 지역·검색어를 바꾼 뒤 도착한 이전 요청의 응답은 버린다.
        if (generation !== search.generation) return;
        if (!append) search = {query, nextOffset: null, generation, excluded: 0, total: 0, limited: false};
        for (const candidate of data.candidates || []) {
          if (!results.some(item => item.qid === candidate.qid)) results.push(candidate);
        }
        search.nextOffset = data.nextOffset ?? null;
        search.excluded += data.excludedCount || 0;
        search.total = data.total ?? 0;
        search.limited = Boolean(data.limited);
        renderResults();
        showStatus(resultMessage());
      } catch (error) {
        if (generation === search.generation) showStatus(error.message, true);
      } finally {
        if (generation === search.generation) {
          setBusy(false);
          moreButton.hidden = search.nextOffset == null;
          moreButton.textContent = search.query?.mode === 'region' ? '여행지 더 보기' : '검색 결과 더 보기';
        }
      }
    }

    // ───────────── 지역별 탐색 ─────────────

    const continentSelect = $('[data-bulk-continent]');
    const countrySelect = $('[data-bulk-country]');
    const areaSelect = $('[data-bulk-area]');
    const citySelect = $('[data-bulk-city]');
    const regionSearchButton = $('[data-bulk-region-search]');
    const regionInfo = $('[data-bulk-region-info]');
    let resolved = null;
    let resolveGeneration = 0;

    function resetSelect(select, label) {
      select.replaceChildren(new Option(label, ''));
      select.disabled = true;
    }

    function fillSelect(select, list) {
      for (const item of list) select.append(new Option(item.regionName, String(item.id)));
      select.disabled = !list.length;
    }

    function showRegionInfo(text, isError = false) {
      regionInfo.textContent = text;
      regionInfo.classList.toggle('is-error', isError);
    }

    /** 지역을 바꾸면 매핑 결과와 진행 중인 지역 조회를 무효로 한다. 선택 목록은 그대로 둔다. */
    function invalidateRegion() {
      resolved = null;
      resolveGeneration++;
      regionSearchButton.disabled = true;
      if (search.query?.mode === 'region') {
        search.generation++;
        search.query = null;
        results = [];
        renderResults();
        moreButton.hidden = true;
        setBusy(false);
      }
    }

    async function resolveArea(regionId) {
      const generation = ++resolveGeneration;
      showRegionInfo('Wikidata 행정구역과 연결하는 중입니다.');
      try {
        const data = await requestJson(`${apiBase}/regions/resolve?regionId=${encodeURIComponent(regionId)}`);
        if (generation !== resolveGeneration) return;
        if (!data.qid) {
          showRegionInfo(`${data.message || 'Wikidata 행정구역을 확정하지 못했습니다.'} 이 지역은 여행지 검색으로 찾아 주세요.`, true);
          return;
        }
        resolved = data;
        for (const area of data.municipalities || []) citySelect.append(new Option(area.name, area.qid));
        citySelect.disabled = !(data.municipalities || []).length;
        showRegionInfo(`Wikidata ${data.wikidataName || data.qid} (${data.qid}) · ${data.basis}`
          + ((data.municipalities || []).length ? ` · 시 ${data.municipalities.length}곳으로 좁혀 볼 수 있습니다.` : ''));
        void runQuery(regionQuery(), false);
      } catch (error) {
        if (generation === resolveGeneration) showRegionInfo(error.message, true);
      }
    }

    function regionQuery() {
      const city = citySelect.value;
      return {mode: 'region', regionId: resolved.regionId, cityQid: city || null,
        label: city ? citySelect.selectedOptions[0].textContent : `${areaSelect.selectedOptions[0].textContent} 전체`};
    }

    loadRegions(null).then(list => fillSelect(continentSelect,
      list.filter(item => String(item.id) !== domesticRootId)));
    continentSelect.addEventListener('change', () => {
      resetSelect(countrySelect, '국가 선택');
      resetSelect(areaSelect, '지역 선택');
      resetSelect(citySelect, '지역 전체');
      invalidateRegion();
      showRegionInfo('국가를 선택해 주세요.');
      if (continentSelect.value) loadRegions(continentSelect.value).then(list => fillSelect(countrySelect, list));
    });
    countrySelect.addEventListener('change', () => {
      resetSelect(areaSelect, '지역 선택');
      resetSelect(citySelect, '지역 전체');
      invalidateRegion();
      if (!countrySelect.value) return;
      showRegionInfo('국가 전체는 범위가 넓어 탐색하지 않습니다. 지역을 선택해 주세요.');
      loadRegions(countrySelect.value).then(list => {
        fillSelect(areaSelect, list);
        if (!list.length) showRegionInfo('이 국가에는 등록된 하위 지역이 없습니다. 여행지 검색으로 찾아 주세요.', true);
      });
    });
    areaSelect.addEventListener('change', () => {
      resetSelect(citySelect, '지역 전체');
      invalidateRegion();
      if (areaSelect.value) void resolveArea(areaSelect.value);
    });
    citySelect.addEventListener('change', () => {
      if (!resolved) return;
      search.generation++;
      void runQuery(regionQuery(), false);
    });
    regionSearchButton.addEventListener('click', () => {
      if (resolved?.qid) void runQuery(regionQuery(), false);
    });

    for (const tab of page.querySelectorAll('[data-bulk-mode]')) {
      tab.addEventListener('click', () => {
        for (const other of page.querySelectorAll('[data-bulk-mode]')) {
          const active = other === tab;
          other.classList.toggle('active', active);
          other.setAttribute('aria-pressed', String(active));
        }
        for (const panel of page.querySelectorAll('[data-bulk-panel]')) {
          panel.hidden = panel.dataset.bulkPanel !== tab.dataset.bulkMode;
        }
      });
    }

    function isLocked(qid) {
      return running || DONE.includes(rows.get(qid)?.result?.status);
    }

    function renderResults() {
      resultRows.replaceChildren();
      resultCount.textContent = results.length ? `검색 결과 ${results.length}건` : '검색 결과';
      for (const candidate of results) {
        const done = DONE.includes(rows.get(candidate.qid)?.result?.status);
        const registered = candidate.registered || done;
        const tr = element('tr', registered ? 'is-registered' : '');
        const check = element('td', 'is-check');
        const box = element('input');
        box.type = 'checkbox';
        box.checked = rows.has(candidate.qid) && !registered;
        box.disabled = registered || isLocked(candidate.qid);
        box.setAttribute('aria-label', `${candidate.name || candidate.qid} (${candidate.qid}) 선택`);
        box.addEventListener('change', () => {
          if (box.checked) addRow(candidate); else removeRow(candidate.qid);
        });
        check.append(box);
        const thumb = element('td', 'is-thumb');
        thumb.append(candidate.imageUrl ? safeImage(candidate.imageUrl, 'admin-kto-import-thumb', `${candidate.name} 이미지`)
          : element('span', 'admin-kto-import-thumb is-empty', '없음'));
        const name = element('td');
        name.append(element('strong', '', candidate.name || '명칭 없음'), element('span', 'admin-wikidata-qid', candidate.qid));
        const place = element('td', '', [candidate.country, candidate.region].filter(Boolean).join(' · ') || '확인 필요');
        const description = element('td', 'admin-wikidata-bulk-description', candidate.shortDescription || '');
        const state = element('td');
        state.append(element('span', `admin-kto-import-badge ${registered ? 'is-registered' : 'is-new'}`,
          registered ? '등록 완료' : '미등록'));
        tr.append(check, thumb, name, place, description, state);
        resultRows.append(tr);
      }
      renderSelection();
    }

    /** 현재 페이지 = 화면에 보이는 후보(더 보기로 붙은 후보 포함). 등록 완료·등록 중 잠긴 후보는 대상이 아니다. */
    function currentPageSelection() {
      return pageSelection(results, candidate => !candidate.registered && !isLocked(candidate.qid),
        candidate => rows.has(candidate.qid));
    }

    /** 현재 페이지 후보만 고르거나 푼다. 다른 검색어·지역에서 고른 여행지는 그대로 둔다. */
    function selectCurrentPage(select) {
      for (const candidate of currentPageSelection().targets) {
        if (select) addRow(candidate, false); else removeRow(candidate.qid, false);
      }
      refreshAll();
      renderResults();
    }

    function renderSelection() {
      const all = [...rows.values()];
      const registrable = all.filter(row => !DONE.includes(row.result?.status)).length;
      selectedCount.textContent = `전체 선택 ${all.length}건 · 등록 가능 ${registrable}건`;
      clearButton.disabled = running || !all.length;
      applyPageSelection(currentPageSelection(),
        {toggle: pageToggle, selectButton: pageSelectButton, clearButton: pageClearButton, count: pageCount});
      openReviewButton.disabled = !all.length;
      chips.hidden = !all.length;
      chips.replaceChildren();
      if (!all.length) return;
      chips.append(element('span', 'admin-kto-import-count', '선택한 여행지'));
      for (const row of all) {
        const chip = element('span', 'admin-wikidata-bulk-chip');
        chip.append(element('span', '', `${row.candidate.name || row.qid} · ${row.qid}`));
        const remove = element('button', '', '×');
        remove.type = 'button';
        remove.disabled = isLocked(row.qid);
        remove.setAttribute('aria-label', `${row.candidate.name || row.qid} 선택 해제`);
        remove.addEventListener('click', () => {
          removeRow(row.qid);
          renderResults();
        });
        chip.append(remove);
        chips.append(chip);
      }
    }

    /** refresh=false 는 여러 곳을 한 번에 바꾼 뒤 한 번만 다시 그릴 때 쓴다. */
    function addRow(candidate, refresh = true) {
      if (rows.has(candidate.qid) || candidate.registered) return;
      const row = {qid: candidate.qid, candidate, review: null, reviewState: 'idle', photosState: 'idle',
        photoIndex: new Map(), photo: null, koreanName: '', type: commonType.value, season: commonSeason.value,
        regionId: null, regionEditing: false, result: null, element: null, photoRow: null};
      rows.set(candidate.qid, row);
      if (!review.hidden) mountRow(row);
      if (refresh) refreshAll();
    }

    function removeRow(qid, refresh = true) {
      const row = rows.get(qid);
      if (!row || running) return;
      row.element?.remove();
      row.photoRow?.remove();
      rows.delete(qid);
      if (refresh) refreshAll();
    }

    // ───────────── 2. 등록 전 검토 ─────────────

    function openReview() {
      review.hidden = false;
      for (const row of rows.values()) if (!row.element) mountRow(row);
      refreshAll();
      review.scrollIntoView?.({behavior: 'smooth', block: 'start'});
    }

    function mountRow(row) {
      const tr = element('tr');
      // 고칠 수 있는 칸은 fieldset 으로 감싸, 등록 중·완료 때 한 번에 잠그고 풀어도 각 입력의 원래 상태가 남게 한다.
      row.fieldsets = [];
      const editable = className => {
        const td = element('td', className);
        const box = element('fieldset', 'admin-wikidata-bulk-field');
        td.append(box);
        row.fieldsets.push(box);
        tr.append(td);
        return box;
      };
      const place = element('td', 'admin-wikidata-bulk-place');
      tr.append(place);
      const koreanName = editable('');
      const region = editable('');
      const type = editable('');
      const season = editable('');
      const photo = editable('admin-wikidata-bulk-photo');
      const state = element('td', 'admin-wikidata-bulk-state');
      const remove = element('td');
      tr.append(state, remove);
      row.element = tr;
      row.cells = {place, koreanName, region, type, season, photo, state, remove};
      reviewRows.append(tr);

      type.append(choiceSelect(row, 'type', typeOptions, '유형'));
      season.append(choiceSelect(row, 'season', seasonOptions, '시즌'));
      const removeButton = element('button', 'admin-btn is-small', '제외');
      removeButton.type = 'button';
      removeButton.setAttribute('aria-label', `${row.candidate.name || row.qid} 제외`);
      removeButton.addEventListener('click', () => {
        removeRow(row.qid);
        renderResults();
      });
      remove.append(removeButton);
      row.removeButton = removeButton;
      loadReview(row);
    }

    function choiceSelect(row, key, options, label) {
      const select = element('select', 'admin-select');
      select.setAttribute('aria-label', `${row.candidate.name || row.qid} ${label}`);
      select.append(new Option(`${label} 선택`, ''));
      for (const [value, text] of options) select.append(new Option(text, value));
      select.value = row[key] || '';
      select.addEventListener('change', () => {
        row[key] = select.value;
        refreshAll();
      });
      row[`${key}Select`] = select;
      return select;
    }

    function loadReview(row) {
      row.reviewState = 'loading';
      row.photosState = 'loading';
      refreshRow(row);
      reviewQueue(async () => {
        try {
          const data = await requestJson(`${apiBase}/review?qid=${encodeURIComponent(row.qid)}`);
          if (rows.get(row.qid) !== row) return;
          row.review = data;
          row.reviewState = 'ready';
          if (data.registered) row.result = {status: 'DUPLICATE', message: '이미 등록된 여행지입니다.'};
          renderPlace(row);
          renderKoreanName(row);
          renderRegion(row);
        } catch (error) {
          if (rows.get(row.qid) !== row) return;
          row.reviewState = 'error';
          row.reviewError = error.message;
        }
        refreshAll();
        if (row.reviewState === 'ready') await loadPhotos(row);
        else row.photosState = 'idle';
        refreshAll();
      });
    }

    function renderPlace(row) {
      const data = row.review;
      const cell = row.cells.place;
      cell.replaceChildren();
      cell.append(element('strong', '', data.names?.ko || row.candidate.name || row.qid),
        element('span', 'admin-wikidata-qid', row.qid));
      const languages = Object.keys(data.names || {}).length;
      const facts = [`제목 ${languages}개 언어`, `Wikipedia 설명 ${data.wikipediaLanguages?.length || 0}개 언어`];
      if (data.homepageUrl) facts.push('홈페이지');
      if (data.contactNumber) facts.push('전화번호');
      cell.append(element('small', 'admin-wikidata-bulk-facts', `${data.country || '국가 확인 필요'} · ${facts.join(' · ')}`));
      for (const note of data.notes || []) cell.append(element('small', 'admin-wikidata-bulk-note', note));
    }

    function renderKoreanName(row) {
      const cell = row.cells.koreanName;
      cell.replaceChildren();
      if (row.review?.names?.ko) {
        cell.append(element('span', '', row.review.names.ko));
        return;
      }
      const input = element('input', 'admin-input');
      input.type = 'text';
      input.maxLength = 255;
      input.placeholder = '한국어 이름 입력';
      input.value = row.koreanName;
      input.setAttribute('aria-label', `${row.candidate.name || row.qid} 한국어 이름`);
      input.addEventListener('input', () => {
        row.koreanName = input.value;
        refreshAll();
      });
      cell.append(input);
    }

    // 지역: 자동 매핑이 확정되면 경로만 보여주고, 없거나 '변경'을 누르면 기존 지역 목록에서 고른다.
    function renderRegion(row) {
      const cell = row.cells.region;
      cell.replaceChildren();
      const data = row.review;
      if (data.autoRegionId && !row.regionEditing) {
        cell.append(element('span', '', data.regionPath?.length ? data.regionPath.join(' › ') : '자동 선택됨'));
        const change = element('button', 'admin-btn is-small', '변경');
        change.type = 'button';
        change.addEventListener('click', () => {
          row.regionEditing = true;
          renderRegion(row);
          refreshAll();
        });
        cell.append(change);
        return;
      }
      const help = element('small', 'admin-wikidata-bulk-note',
        data.autoRegionId ? '다른 지역으로 바꿉니다.' : (data.regionMessage || '지역을 자동으로 찾지 못했습니다.'));
      const selects = element('div', 'admin-wikidata-bulk-region');
      cell.append(selects, help);
      const setRegion = (id, label) => {
        row.regionId = id ? Number(id) : null;
        row.regionLabel = label || '';
        refreshAll();
      };
      if (data.countryId) {
        const city = element('select', 'admin-select');
        city.setAttribute('aria-label', `${row.candidate.name || row.qid} 도시·지역`);
        city.append(new Option('도시·지역 선택', ''), new Option(`${data.country || '국가'} 전체(국가 단위)`, String(data.countryId)));
        city.addEventListener('change', () => setRegion(city.value, city.selectedOptions[0]?.textContent));
        loadRegions(data.countryId).then(list => list.forEach(item => city.append(new Option(item.regionName, String(item.id)))));
        selects.append(city);
      } else {
        const continent = element('select', 'admin-select');
        const country = element('select', 'admin-select');
        const city = element('select', 'admin-select');
        continent.setAttribute('aria-label', `${row.candidate.name || row.qid} 대륙`);
        country.setAttribute('aria-label', `${row.candidate.name || row.qid} 국가`);
        city.setAttribute('aria-label', `${row.candidate.name || row.qid} 도시·지역`);
        const reset = (select, label) => select.replaceChildren(new Option(label, ''));
        reset(continent, '대륙 선택');
        reset(country, '국가 선택');
        reset(city, '도시·지역(선택)');
        country.disabled = true;
        city.disabled = true;
        loadRegions(null).then(list => list.filter(item => String(item.id) !== domesticRootId)
          .forEach(item => continent.append(new Option(item.regionName, String(item.id)))));
        continent.addEventListener('change', () => {
          reset(country, '국가 선택');
          reset(city, '도시·지역(선택)');
          country.disabled = !continent.value;
          city.disabled = true;
          setRegion(null);
          if (continent.value) loadRegions(continent.value).then(list => list.forEach(item => country.append(new Option(item.regionName, String(item.id)))));
        });
        country.addEventListener('change', () => {
          reset(city, '도시·지역(선택)');
          city.disabled = !country.value;
          setRegion(country.value, country.selectedOptions[0]?.textContent);
          if (country.value) loadRegions(country.value).then(list => list.forEach(item => city.append(new Option(item.regionName, String(item.id)))));
        });
        city.addEventListener('change', () => setRegion(city.value || country.value,
          (city.value ? city : country).selectedOptions[0]?.textContent));
        selects.append(continent, country, city);
      }
      if (data.autoRegionId) {
        const cancel = element('button', 'admin-btn is-small', '자동 지역 사용');
        cancel.type = 'button';
        cancel.addEventListener('click', () => {
          row.regionEditing = false;
          row.regionId = null;
          renderRegion(row);
          refreshAll();
        });
        cell.append(cancel);
      }
    }

    function loadRegions(parentId) {
      const key = parentId == null ? '' : String(parentId);
      if (!regionCache.has(key)) {
        const request = requestJson(`/api/regions${key ? `?parentId=${encodeURIComponent(key)}` : ''}`)
          .then(list => (Array.isArray(list) ? list : []))
          .catch(() => { regionCache.delete(key); return []; });
        regionCache.set(key, request);
      }
      return regionCache.get(key);
    }

    // 대표 사진: 첫 묶음에서 저장 가능한 첫 사진(보통 Wikidata 대표 이미지)을 미리 골라 두고, 바꾸거나 뺄 수 있다.
    async function loadPhotos(row) {
      row.photosState = 'loading';
      refreshRow(row);
      try {
        const data = await fetchPhotos(row, null);
        if (rows.get(row.qid) !== row) return;
        row.firstPhotos = data;
        const first = (data.photos || []).find(photo => photo.savable);
        if (!row.photoTouched) row.photo = first ? {fileName: first.fileName} : null;
        row.photosState = 'ready';
      } catch (error) {
        if (rows.get(row.qid) !== row) return;
        row.photosState = 'error';
        row.photoError = error.message;
      }
      renderPhoto(row);
    }

    async function fetchPhotos(row, cursor) {
      const data = await requestJson(`/admin/api/wikidata/destinations/commons-photos?qid=${encodeURIComponent(row.qid)}`
        + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''));
      if (data.qid !== row.qid) throw new Error('다른 여행지의 사진 응답입니다. 다시 불러와 주세요.');
      for (const photo of data.photos || []) row.photoIndex.set(photo.fileName, photo);
      return data;
    }

    function renderPhoto(row) {
      const cell = row.cells.photo;
      cell.replaceChildren();
      if (row.photosState === 'loading') {
        cell.append(element('small', 'admin-wikidata-bulk-note', '사진 확인 중'));
        return;
      }
      if (row.photosState === 'error') {
        cell.append(element('small', 'admin-wikidata-bulk-note', '사진을 불러오지 못했습니다'));
        const retry = element('button', 'admin-btn is-small', '다시 불러오기');
        retry.type = 'button';
        retry.addEventListener('click', () => loadPhotos(row).then(refreshAll));
        cell.append(retry);
        return;
      }
      const chosen = row.photo && row.photoIndex.get(row.photo.fileName);
      cell.append(chosen ? safeImage(chosen.thumbnailUrl, 'admin-kto-import-thumb', chosen.fileName)
        : element('span', 'admin-kto-import-thumb is-empty', '사진 없음'));
      const toggle = element('button', 'admin-btn is-small', row.photoRow ? '닫기' : '사진 선택');
      toggle.type = 'button';
      toggle.disabled = !row.firstPhotos?.photos?.length && !row.firstPhotos?.nextCursor;
      toggle.addEventListener('click', () => togglePhotoPicker(row));
      cell.append(toggle);
    }

    function togglePhotoPicker(row) {
      if (row.photoRow) {
        row.photoRow.remove();
        row.photoRow = null;
        renderPhoto(row);
        refreshRow(row);
        return;
      }
      const fragment = photoTemplate.content.cloneNode(true);
      row.photoRow = fragment.querySelector('tr');
      row.element.after(row.photoRow);
      picker.render(row.photoRow.querySelector('[data-bulk-photo-picker]'), row.firstPhotos, {
        mainMode: 'auto',
        selectionLimit: 1,
        selection: row.photo ? [{fileName: row.photo.fileName, main: true}] : [],
        heading: `대표 사진 · ${row.review?.names?.ko || row.candidate.name || row.qid}`,
        intro: '여행지당 대표 사진 1장만 함께 저장합니다. 등록할 때 서버가 출처·라이선스를 다시 확인합니다. 추가 사진은 등록 후 이미지 관리 화면에서 더할 수 있습니다.',
        selectedLabel: '대표 사진',
        emptySelectionText: '선택한 사진 없음 · 사진 없이 등록됩니다.',
        actionLabel: '대표 사진으로 선택',
        loadMore: cursor => fetchPhotos(row, cursor),
        onChange: photos => {
          row.photoTouched = true;
          row.photo = photos[0] ? {fileName: photos[0].fileName} : null;
          renderPhoto(row);
          refreshRow(row);
        }
      });
      renderPhoto(row);
      refreshRow(row);
    }

    function statusText(row) {
      const {state, problems} = rowState(row);
      const result = row.result;
      if (result?.status === 'SUCCESS') return ['등록 성공', 'is-success'];
      if (result?.status === 'DUPLICATE') return ['이미 등록됨', 'is-registered'];
      if (result?.status === 'RUNNING') return ['등록 중…', 'is-running'];
      if (result?.status === 'RETRYING') return [`자동 재시도 중 (${result.attempt}/${AUTO_RETRY_LIMIT})`, 'is-running'];
      if (result?.status === 'WAITING') {
        const seconds = Math.max(0, Math.ceil((result.until - Date.now()) / 1000));
        return [`요청 제한으로 대기 중 · ${seconds}초 후 자동 재시도`
          + (result.service ? ` (${result.service})` : ''), 'is-waiting'];
      }
      if (result?.status === 'QUEUED') return ['등록 대기', 'is-running'];
      if (row.reviewState === 'loading') return ['검토 정보 불러오는 중', ''];
      if (row.reviewState === 'error') return [`확인 필요 · ${row.reviewError}`, 'is-needs'];
      if (state === 'loading') return ['사진 확인 중', ''];
      const failure = result?.status === 'FAILED' ? `실패 · ${result.message}` : null;
      if (state === 'needs') return [[failure, `확인 필요: ${problems.join(', ')}`].filter(Boolean).join(' / '), 'is-needs'];
      return [failure || '등록 가능', failure ? 'is-failed' : 'is-ready'];
    }

    function refreshRow(row) {
      if (!row.element) return;
      const [text, className] = statusText(row);
      const cell = row.cells.state;
      cell.replaceChildren(element('span', `admin-wikidata-bulk-status ${className}`, text));
      if (row.result?.destinationId) {
        const link = element('a', '', '수정');
        link.href = `/admin/destinations/edit/${encodeURIComponent(row.result.destinationId)}`;
        cell.append(link);
      }
      if (row.reviewState === 'error') {
        const retry = element('button', 'admin-btn is-small', '다시 불러오기');
        retry.type = 'button';
        retry.disabled = running;
        retry.addEventListener('click', () => loadReview(row));
        cell.append(retry);
      }
      // 등록이 끝났거나 진행 중인 행은 고칠 수 없다.
      const locked = running || DONE.includes(row.result?.status);
      row.element.classList.toggle('is-done', DONE.includes(row.result?.status));
      for (const fieldset of row.fieldsets) fieldset.disabled = locked;
      row.removeButton.disabled = running;
      if (row.typeSelect && row.typeSelect.value !== (row.type || '')) row.typeSelect.value = row.type || '';
      if (row.seasonSelect && row.seasonSelect.value !== (row.season || '')) row.seasonSelect.value = row.season || '';
    }

    function refreshAll() {
      const all = [...rows.values()];
      all.forEach(refreshRow);
      renderSelection();
      const count = summarize(all);
      const progress = count.success + count.duplicate + count.failed + count.running + count.queued;
      summary.textContent = `선택 ${count.total}곳 · 등록 가능 ${count.ready}곳 · 확인 필요 ${count.needs}곳`
        + (count.loading ? ` · 확인 중 ${count.loading}곳` : '')
        + (progress ? ` | 진행 ${count.success + count.duplicate + count.failed}/${progress} · 성공 ${count.success}`
          + ` · 이미 등록됨 ${count.duplicate} · 실패 ${count.failed}`
          + (count.waiting ? ` · 요청 제한 대기 ${count.waiting}곳` : '') : '');
      registerButton.textContent = running ? '등록 중…' : `등록 가능 ${count.fresh}곳 등록`;
      registerButton.disabled = running || !count.fresh || count.needs > 0 || count.loading > 0;
      const retryable = all.filter(row => row.result?.status === 'FAILED' && rowState(row).state === 'ready').length;
      retryButton.hidden = !all.some(row => row.result?.status === 'FAILED');
      retryButton.disabled = running || !retryable;
      retryButton.textContent = `실패한 여행지 재시도 (${retryable}곳)`;
      commonType.disabled = running;
      commonSeason.disabled = running;
      $('[data-bulk-apply-common]').disabled = running;
      if (!running) {
        registerStatus.textContent = count.needs
          ? `확인 필요 ${count.needs}곳을 보완하거나 제외해야 등록할 수 있습니다.`
          : count.loading ? '검토 정보를 불러오는 중입니다.' : '';
      }
    }

    // ───────────── 3. 여행지별 등록 ─────────────

    async function registerRows(targets) {
      if (running || !targets.length) return;
      running = true;
      // 보낸 뒤에는 사진을 바꿀 수 없으므로 열린 사진 선택 영역을 닫는다.
      for (const row of rows.values()) if (row.photoRow) togglePhotoPicker(row);
      targets.forEach(row => { row.result = {status: 'QUEUED'}; });
      refreshAll();
      renderResults();
      registerStatus.textContent = `${targets.length}곳을 차례로 등록합니다. 여행지마다 따로 저장하므로 일부가 실패해도 나머지는 계속 진행하고, `
        + '외부 API 요청 제한이 걸리면 자동으로 기다렸다가 이어서 등록합니다.';
      // 대기 중 남은 시간을 1초마다 다시 그린다.
      const ticker = setInterval(refreshAll, 1000);
      try {
        // 서버도 동시 등록 수를 묶지만, 화면에서도 두 곳씩만 보낸다.
        await runRegistrationQueue(targets, {
          send: row => requestJson(`${apiBase}/register`, {method: 'POST', body: registerPayload(row)}),
          sleep: ms => new Promise(resolve => setTimeout(resolve, ms)),
          now: () => Date.now(),
          isCancelled: () => leaving,
          onChange: () => {
            for (const row of targets) {
              if (!DONE.includes(row.result?.status)) continue;
              const candidate = results.find(item => item.qid === row.qid);
              if (candidate) candidate.registered = true;
            }
            refreshAll();
          }
        });
      } finally {
        clearInterval(ticker);
      }
      running = false;
      refreshAll();
      renderResults();
      const count = summarize([...rows.values()]);
      registerStatus.textContent = `등록을 마쳤습니다. 성공 ${count.success} · 이미 등록됨 ${count.duplicate} · 실패 ${count.failed}`
        + (count.failed ? ' · 실패 사유를 확인해 고친 뒤 재시도할 수 있습니다.' : '');
    }

    searchForm.addEventListener('submit', event => {
      event.preventDefault();
      const keyword = keywordInput.value.trim();
      if (keyword.length < 2 || keyword.length > 100) {
        showStatus('검색어를 2~100자로 입력해 주세요.', true);
        return;
      }
      void runQuery({mode: 'keyword', keyword}, false);
    });
    moreButton.addEventListener('click', () => {
      if (search.query && search.nextOffset != null) void runQuery(search.query, true);
    });
    clearButton.addEventListener('click', () => {
      for (const qid of [...rows.keys()]) removeRow(qid);
      renderResults();
    });
    pageSelectButton.addEventListener('click', () => selectCurrentPage(true));
    pageClearButton.addEventListener('click', () => selectCurrentPage(false));
    pageToggle.addEventListener('change', () => selectCurrentPage(pageToggle.checked));
    openReviewButton.addEventListener('click', openReview);
    $('[data-bulk-apply-common]').addEventListener('click', () => {
      for (const row of rows.values()) {
        if (DONE.includes(row.result?.status)) continue;
        if (commonType.value) row.type = commonType.value;
        if (commonSeason.value) row.season = commonSeason.value;
      }
      refreshAll();
    });
    registerButton.addEventListener('click', () => registerRows([...rows.values()]
      .filter(row => !row.result && rowState(row).state === 'ready')));
    retryButton.addEventListener('click', () => registerRows([...rows.values()]
      .filter(row => row.result?.status === 'FAILED' && rowState(row).state === 'ready')));
    window.addEventListener('beforeunload', event => {
      if (running) event.preventDefault();
    });
    window.addEventListener('pagehide', () => { leaving = true; });
  });
})(typeof window !== 'undefined' ? window : globalThis);
