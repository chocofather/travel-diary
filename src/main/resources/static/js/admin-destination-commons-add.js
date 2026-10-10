// 해외 여행지의 이미지 관리 화면에서 Commons 사진을 골라 한 번에 추가한다.
// QID가 있으면 그 QID 후보(P18·카테고리)를, 없거나 후보가 없으면 관리자가 검색한 결과를 보여준다.
// 후보 표시는 등록폼과 같은 카드(TripBoraCommonsPhotoPicker)를 쓰되 저장할 수 없는 사진은 숨긴다.
// 서버로는 QID(또는 검색 표시)·파일명·대표 여부만 보낸다.
document.addEventListener('DOMContentLoaded', () => {
  const root = document.querySelector('[data-commons-add]');
  const picker = window.TripBoraCommonsPhotoPicker || window.TravelDiaryCommonsPhotoPicker; // legacy fallback (P10a 제거)
  if (!root || !picker) return;

  const qid = root.dataset.qid || '';
  const loadButton = root.querySelector('[data-commons-add-load]');
  const searchForm = root.querySelector('[data-commons-search-form]');
  const searchInput = root.querySelector('[data-commons-search-query]');
  const searchButton = searchForm?.querySelector('button');
  const message = root.querySelector('[data-commons-add-message]');
  const candidates = root.querySelector('[data-commons-add-candidates]');
  const form = root.querySelector('[data-commons-add-form]');
  const selectionField = root.querySelector('[data-commons-add-selection]');
  const submitButton = root.querySelector('[data-commons-add-submit]');
  const submitStatus = root.querySelector('[data-commons-add-status]');
  const submitLabel = submitButton.textContent;
  const registeredFileNames = Array.from(root.querySelectorAll('[data-registered-commons-file]'))
    .map(node => node.textContent.trim()).filter(Boolean);
  let photos = [];
  // 지금 보이는 후보의 출처. 'qid' 또는 'search'. 출처를 바꾸면 선택을 비운다.
  let mode = null;

  function showMessage(text, isError = false) {
    message.hidden = !text;
    message.textContent = text || '';
    message.classList.toggle('is-warning', isError);
  }

  function updateSelection(next) {
    photos = next;
    selectionField.value = !photos.length ? ''
      : mode === 'search' ? JSON.stringify({source: 'SEARCH', photos}) : picker.serializeSelection(qid, photos);
    submitButton.disabled = form.dataset.submitting === 'true' || photos.length === 0;
  }

  // 같은 위치(cursor)의 묶음은 한 번만 받는다. 실패한 요청은 남기지 않아 다시 시도할 수 있다.
  const pages = new Map();

  function pageUrl(requestMode, query, cursor) {
    const base = requestMode === 'search'
      ? `/admin/api/wikidata/destinations/commons-search?query=${encodeURIComponent(query)}`
      : `/admin/api/wikidata/destinations/commons-photos?qid=${encodeURIComponent(qid)}`;
    return base + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '');
  }

  function fetchPage(requestMode, query, cursor = null) {
    const key = `${requestMode}|${query}|${cursor || ''}`;
    if (pages.has(key)) return pages.get(key);
    const request = (async () => {
      const response = await fetch(pageUrl(requestMode, query, cursor), {headers: {Accept: 'application/json'}});
      if (response.redirected && new URL(response.url).pathname.startsWith('/login')) {
        throw new Error('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.');
      }
      const data = await response.json().catch(() => null);
      if (!response.ok || !data) throw new Error(data?.message || 'Commons 사진을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      if (requestMode === 'qid' && data.qid !== qid) throw new Error('이 여행지의 QID와 다른 응답입니다. 다시 불러와 주세요.');
      return data;
    })();
    pages.set(key, request);
    request.catch(() => pages.delete(key));
    return request;
  }

  function setBusy(busy) {
    if (loadButton) loadButton.disabled = busy;
    if (searchButton) searchButton.disabled = busy;
  }

  async function load(requestMode, query = '') {
    setBusy(true);
    showMessage(requestMode === 'search' ? 'Commons에서 사진과 라이선스 정보를 검색하는 중입니다.'
      : '사진과 라이선스 정보를 불러오는 중입니다.');
    // 다시 불러오기·새 검색은 처음부터 새로 받는다.
    pages.clear();
    try {
      const data = await fetchPage(requestMode, query);
      mode = requestMode;
      updateSelection([]);
      const search = requestMode === 'search';
      picker.render(candidates, data, {
        mainMode: 'explicit',
        registeredFileNames,
        hideBlocked: true,
        heading: search ? `Commons 검색 결과 · ${query}` : `Commons 사진 후보 · ${qid}`,
        intro: (search
          ? '검색 결과는 이 여행지와 관련이 없을 수 있습니다. 사진을 직접 확인하고 고르세요. '
          : 'Wikidata 대표 이미지(P18)와 연결된 Commons 카테고리 사진입니다. ')
          + '사용할 수 있는 라이선스 사진만 보여주며, 이미 이 여행지에 있는 사진은 다시 추가할 수 없습니다. '
          + '대표로 지정하지 않으면 현재 대표 사진을 그대로 둡니다.',
        selectedLabel: '추가할 사진',
        emptySelectionText: '선택한 사진 없음',
        actionLabel: '추가할 사진으로 선택',
        loadMore: cursor => fetchPage(requestMode, query, cursor),
        onChange: updateSelection
      });
      // QID 후보가 없거나 더 볼 후보가 없으면 검색으로 이어서 찾게 안내한다.
      // (예: Q243 의 Commons 카테고리는 하위 카테고리만 있어 P18 한 장만 후보가 된다.)
      const noCandidates = !search && data.status === 'NO_PHOTOS';
      showMessage(!search && searchForm && (noCandidates || (data.status === 'AVAILABLE' && !data.nextCursor))
        ? (noCandidates ? '연결된 Commons 사진 후보가 없습니다. 아래 검색으로 사진을 찾아보세요.'
          : 'Wikidata에 연결된 Commons 사진 후보를 모두 표시했습니다. 더 찾으려면 아래 검색을 이용하세요.')
        : '', noCandidates);
      if (loadButton && !search) loadButton.textContent = '후보 다시 불러오기';
    } catch (error) {
      showMessage(error.message, true);
    } finally {
      setBusy(form.dataset.submitting === 'true');
    }
  }

  loadButton?.addEventListener('click', () => void load('qid'));

  searchForm?.addEventListener('submit', event => {
    event.preventDefault();
    const query = (searchInput?.value || '').trim().replace(/\s+/g, ' ');
    if (!query) {
      showMessage('검색어를 입력해 주세요.', true);
      searchInput?.focus?.();
      return;
    }
    void load('search', query);
  });

  form.addEventListener('submit', event => {
    if (form.dataset.submitting === 'true' || photos.length === 0) {
      event.preventDefault();
      return;
    }
    // 빠른 두 번 클릭이 두 번 제출되지 않도록 첫 제출 즉시 잠근다. 결과는 서버가 돌려준 관리 화면에서 안내한다.
    form.dataset.submitting = 'true';
    submitButton.disabled = true;
    setBusy(true);
    submitButton.textContent = '추가 중…';
    submitStatus.hidden = false;
    submitStatus.textContent = `선택한 사진 ${photos.length}장을 추가하는 중입니다. 서버가 출처·라이선스를 다시 확인하고 내려받은 뒤 이 화면으로 돌아옵니다.`;
    // 다른 처리기가 제출을 취소했으면 다시 풀어 준다.
    setTimeout(() => {
      if (event.defaultPrevented) unlock();
    });
  });

  function unlock() {
    delete form.dataset.submitting;
    submitButton.textContent = submitLabel;
    submitButton.disabled = photos.length === 0;
    setBusy(false);
    submitStatus.hidden = true;
  }

  // 오류 화면에서 뒤로 돌아오면(페이지 캐시) 다시 추가할 수 있게 풀어 준다.
  window.addEventListener('pageshow', event => {
    if (event.persisted && form.dataset.submitting === 'true') unlock();
  });
});
