// 여행지 이미지 관리 화면의 Pixabay 스톡 사진 검색·선택.
// 검색은 서버 API만 부른다(Pixabay API Key는 서버에만 있다). 처음 30장, '사진 더 보기'마다 20장씩 받는다.
// 어느 API 페이지를 쓸지와 24시간 캐시는 서버가 맡고, 화면은 이미 보인 사진 수(offset)만 넘긴다.
// 카드 모양은 Commons 사진 후보 카드(admin-commons-*)를 그대로 쓴다. 저장 폼으로는 고른 사진의 Pixabay ID만 보낸다.
document.addEventListener('DOMContentLoaded', () => {
  const root = document.querySelector('[data-pixabay-add]');
  const searchForm = root?.querySelector('[data-pixabay-search-form]');
  if (!root || !searchForm) return;

  const searchUrl = root.dataset.searchUrl;
  const selectionLimit = Number.parseInt(root.dataset.selectionLimit, 10) || 20;
  const searchInput = root.querySelector('[data-pixabay-search-query]');
  const searchButton = searchForm.querySelector('button');
  const message = root.querySelector('[data-pixabay-message]');
  const results = root.querySelector('[data-pixabay-results]');
  const form = root.querySelector('[data-pixabay-add-form]');
  const selectionFields = root.querySelector('[data-pixabay-selection]');
  const submitButton = root.querySelector('[data-pixabay-submit]');
  const submitStatus = root.querySelector('[data-pixabay-status]');
  const submitLabel = submitButton.textContent;

  // 검색어가 바뀌면 화면 위치·선택·결과를 모두 새로 시작한다. 늦게 온 이전 검색 응답은 버린다.
  let searchId = 0;
  let query = '';
  let nextOffset = null;
  let loading = false;
  let selected = [];
  let controls = [];
  const shown = new Set();
  let summary;
  let grid;
  let shownCount;
  let moreButton;
  let moreStatus;

  function element(tag, className, value) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (value != null) node.textContent = value;
    return node;
  }

  function showMessage(text, isError = false) {
    message.hidden = !text;
    message.textContent = text || '';
    message.classList.toggle('is-warning', isError);
  }

  function pixabayUrl(value, hosts) {
    try {
      const url = new URL(value);
      return url.protocol === 'https:' && hosts.includes(url.hostname) ? url.href : null;
    } catch (_) {
      return null;
    }
  }

  async function fetchPage(offset) {
    const response = await fetch(`${searchUrl}?query=${encodeURIComponent(query)}&offset=${offset}`,
      {headers: {Accept: 'application/json'}});
    if (response.redirected && new URL(response.url).pathname.startsWith('/login')) {
      throw new Error('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.');
    }
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) {
      throw new Error(data?.message || 'Pixabay 사진을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
    return data;
  }

  function updateSelection() {
    selectionFields.replaceChildren(...selected.map(id => {
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = 'pixabayImageIds';
      input.value = String(id);
      return input;
    }));
    submitButton.disabled = form.dataset.submitting === 'true' || selected.length === 0;
    if (summary) {
      summary.textContent = selected.length
        ? `추가할 사진 ${selected.length}/${selectionLimit}장`
          + (selected.length >= selectionLimit ? ` · 최대 ${selectionLimit}장까지 선택할 수 있습니다` : '')
        : '선택한 사진 없음';
    }
    for (const control of controls) {
      const checked = selected.includes(control.id);
      control.checkbox.checked = checked;
      control.checkbox.disabled = control.registered || (!checked && selected.length >= selectionLimit);
      control.card.classList.toggle('is-selected', checked);
    }
  }

  function refreshMore() {
    if (!shownCount) return;
    shownCount.textContent = `사진 ${shown.size}장 표시 중`;
    moreButton.hidden = nextOffset == null;
    moreButton.disabled = loading;
    moreButton.textContent = loading ? '불러오는 중…' : '사진 더 보기';
  }

  function addCard(photo) {
    // 같은 사진은 한 번만 보인다(서버도 API 페이지를 합칠 때 같은 ID를 한 번만 남긴다).
    if (!Number.isSafeInteger(photo?.id) || shown.has(photo.id)) return;
    shown.add(photo.id);
    const registered = Boolean(photo.registered);
    const card = element('article', `admin-commons-card ${registered ? 'is-registered' : 'is-available'}`);

    const media = element('label', 'admin-commons-card-media');
    const thumbUrl = pixabayUrl(photo.thumbnailUrl, ['pixabay.com', 'cdn.pixabay.com']);
    if (thumbUrl) {
      // 검색 결과를 잠시 보여 주는 미리보기다. 저장할 때는 서버가 사진을 내려받아 TripBora 주소로 쓴다.
      const image = element('img', 'admin-commons-card-thumb');
      image.src = thumbUrl;
      image.alt = photo.tags || `Pixabay 사진 ${photo.id}`;
      image.loading = 'lazy';
      image.decoding = 'async';
      image.referrerPolicy = 'no-referrer';
      image.addEventListener('error', () => image.remove());
      media.append(image);
    } else {
      media.append(element('span', 'admin-commons-card-noimage', '미리보기 없음'));
    }
    const badges = element('span', 'admin-commons-card-badges');
    badges.append(element('span', 'admin-commons-badge', 'Pixabay'));
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'admin-commons-card-check';
    checkbox.disabled = registered;
    checkbox.setAttribute('aria-label', `Pixabay 사진 ${photo.id} 추가할 사진으로 선택`);
    checkbox.addEventListener('change', () => {
      selected = selected.filter(id => id !== photo.id);
      if (checkbox.checked && selected.length < selectionLimit) selected.push(photo.id);
      updateSelection();
    });
    media.append(badges, checkbox);

    const body = element('div', 'admin-commons-card-body');
    const title = element('strong', 'admin-commons-card-title', photo.tags || `Pixabay 사진 ${photo.id}`);
    title.setAttribute('title', photo.tags || '');
    const size = photo.width && photo.height ? `${photo.width} × ${photo.height}px` : '해상도 정보 없음';
    const meta = element('p', 'admin-commons-card-meta', `${photo.user || '작가 정보 없음'} · ${size}`);
    meta.setAttribute('title', `${photo.user || ''} · ${size}`);
    const state = element('p', 'admin-commons-card-state', registered ? '등록됨' : '선택 가능');
    body.append(title, meta, state);
    const pageUrl = pixabayUrl(photo.pageUrl, ['pixabay.com']);
    if (pageUrl) {
      // 사진 영역은 선택용이라 원본 페이지는 별도 링크로 새 탭에서 연다.
      const link = element('a', 'admin-commons-card-link', 'Pixabay에서 보기');
      link.href = pageUrl;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      body.append(link);
    }
    card.append(media, body);
    controls.push({id: photo.id, registered, checkbox, card});
    grid.append(card);
  }

  function render(data) {
    results.replaceChildren();
    controls = [];
    shown.clear();
    results.append(element('h4', '', `Pixabay 검색 결과 · ${data.query}`));
    // Pixabay 이용 조건: 검색 결과를 보여 줄 때 사진 출처를 알린다.
    results.append(element('p', 'admin-field-help',
      `이미지 제공: Pixabay · 전체 ${data.totalHits || 0}장 중 최대 500장까지 볼 수 있습니다. `
      + '이미 이 여행지에 저장된 사진은 \'등록됨\'으로 표시되며 다시 추가할 수 없습니다.'));
    if (!data.photos?.length) {
      results.append(element('p', 'admin-wikidata-review-note', '검색 결과가 없습니다. 다른 검색어로 찾아보세요.'));
      summary = grid = shownCount = moreButton = moreStatus = null;
      return;
    }
    summary = element('p', 'admin-commons-picker-summary');
    summary.setAttribute('aria-live', 'polite');
    grid = element('div', 'admin-commons-grid');
    const footer = element('div', 'admin-commons-picker-more');
    shownCount = element('span', 'admin-commons-picker-count');
    moreButton = element('button', 'admin-btn', '사진 더 보기');
    moreButton.type = 'button';
    moreButton.addEventListener('click', () => void loadMore());
    moreStatus = element('p', 'admin-wikidata-review-note is-warning');
    moreStatus.setAttribute('role', 'status');
    moreStatus.hidden = true;
    footer.append(shownCount, moreButton, moreStatus);
    results.append(summary, grid, footer);
    data.photos.forEach(addCard);
  }

  async function search(nextQuery) {
    const currentSearch = ++searchId;
    query = nextQuery;
    nextOffset = null;
    selected = [];
    loading = false;
    searchButton.disabled = true;
    showMessage('Pixabay에서 사진을 검색하는 중입니다.');
    try {
      const data = await fetchPage(0);
      if (currentSearch !== searchId) return;
      nextOffset = data.nextOffset ?? null;
      render(data);
      showMessage('');
    } catch (error) {
      if (currentSearch !== searchId) return;
      results.replaceChildren();
      summary = grid = shownCount = moreButton = moreStatus = null;
      showMessage(error.message, true);
    } finally {
      if (currentSearch === searchId) {
        searchButton.disabled = form.dataset.submitting === 'true';
        updateSelection();
        refreshMore();
      }
    }
  }

  async function loadMore() {
    if (loading || nextOffset == null) return;
    const currentSearch = searchId;
    loading = true;
    moreStatus.hidden = true;
    refreshMore();
    try {
      const data = await fetchPage(nextOffset);
      if (currentSearch !== searchId) return;
      (data.photos || []).forEach(addCard);
      nextOffset = data.nextOffset ?? null;
      if (nextOffset == null) {
        moreStatus.textContent = '더 볼 수 있는 사진이 없습니다.';
        moreStatus.hidden = false;
      }
    } catch (error) {
      if (currentSearch !== searchId) return;
      // 실패하면 같은 위치에서 다시 시도할 수 있게 버튼을 남긴다(자동으로 다시 부르지 않는다).
      moreStatus.textContent = error.message;
      moreStatus.hidden = false;
    } finally {
      if (currentSearch === searchId) {
        loading = false;
        updateSelection();
        refreshMore();
      }
    }
  }

  searchForm.addEventListener('submit', event => {
    event.preventDefault();
    const nextQuery = (searchInput?.value || '').trim().replace(/\s+/g, ' ');
    if (!nextQuery) {
      showMessage('검색어를 입력해 주세요.', true);
      searchInput?.focus?.();
      return;
    }
    void search(nextQuery);
  });

  form.addEventListener('submit', event => {
    if (form.dataset.submitting === 'true' || selected.length === 0) {
      event.preventDefault();
      return;
    }
    // 빠른 두 번 클릭이 두 번 제출되지 않도록 첫 제출 즉시 잠근다. 결과는 서버가 돌려준 관리 화면에서 안내한다.
    form.dataset.submitting = 'true';
    submitButton.disabled = true;
    searchButton.disabled = true;
    submitButton.textContent = '추가 중…';
    submitStatus.hidden = false;
    submitStatus.textContent = `선택한 사진 ${selected.length}장을 내려받아 저장하는 중입니다. 완료되면 이 화면으로 돌아옵니다.`;
    setTimeout(() => {
      if (event.defaultPrevented) unlock();
    });
  });

  function unlock() {
    delete form.dataset.submitting;
    submitButton.textContent = submitLabel;
    submitButton.disabled = selected.length === 0;
    searchButton.disabled = false;
    submitStatus.hidden = true;
  }

  // 오류 화면에서 뒤로 돌아오면(페이지 캐시) 다시 추가할 수 있게 풀어 준다.
  window.addEventListener('pageshow', event => {
    if (event.persisted && form.dataset.submitting === 'true') unlock();
  });
});
