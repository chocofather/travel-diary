(function (root) {
  // Wikimedia Commons 사진 후보 그리드와 선택 규칙. 등록폼과 기존 여행지 이미지 관리 화면이 함께 쓴다.
  // 서버로는 파일명·대표 여부만 보낸다. URL·저작자·라이선스는 저장할 때 서버가 Commons에서 다시 조회한다.
  //
  // 대표 사진 규칙(mainMode)
  //   auto     : 새 여행지 등록. 사진을 고르면 그중 한 장은 항상 대표다.
  //   explicit : 기존 여행지에 추가. 관리자가 직접 고를 때만 대표를 바꾸고, 다시 누르면 지정을 푼다.
  //
  // 후보는 서버가 묶음(nextCursor)으로 나눠 준다. '사진 더 보기'는 options.loadMore(cursor)로 다음 묶음을 받아
  // 기존 카드 뒤에 중복 없이 붙이고, 선택·대표 상태는 그대로 둔다.

  /** 서버 저장 검증과 같은 기본 선택 한도. 응답의 selectionLimit 이 있으면 그 값을 쓴다. */
  const DEFAULT_SELECTION_LIMIT = 5;
  /** hideBlocked 에서 숨기는 작은 사진 기준(짧은 변). 서버 수동 검색(CommonsPhotoPreviewService.MIN_PHOTO_SHORT_SIDE)과 같다. */
  const MIN_PHOTO_SHORT_SIDE = 400;
  /** 같은 영역을 다시 그리면(QID 변경 등) 이전 그리기의 늦은 '더 보기' 응답을 버린다. */
  const renders = new WeakMap();

  function togglePhoto(photos, fileName, checked, mainMode = 'auto') {
    const next = photos.filter(photo => photo.fileName !== fileName).map(photo => ({...photo}));
    if (checked) next.push({fileName, main: false});
    if (mainMode === 'auto' && next.length && !next.some(photo => photo.main)) next[0].main = true;
    return next;
  }

  function setMain(photos, fileName, mainMode = 'auto') {
    const current = photos.find(photo => photo.fileName === fileName);
    if (!current) return photos.map(photo => ({...photo}));
    if (mainMode === 'explicit' && current.main) return photos.map(photo => ({...photo, main: false}));
    return photos.map(photo => ({...photo, main: photo.fileName === fileName}));
  }

  function serializeSelection(qid, photos) {
    return photos.length ? JSON.stringify({qid, photos}) : '';
  }

  function element(tag, className, value) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (value != null) node.textContent = value;
    return node;
  }

  function appendField(container, label, value) {
    const row = element('div', 'admin-wikidata-field');
    row.append(element('dt', '', label), element('dd', '', value || '제공되지 않음'));
    container.append(row);
  }

  // 서버 판별 결과(reuseStatus)별 이름. 승인 절차는 없고, 저장 불가 사진은 선택할 수 없다.
  const STATUS_LABELS = {
    LICENSE_EVIDENCE_MISSING: '라이선스 근거 부족',
    RIGHTS_UNCLEAR: '권리 상태 불명확',
    UNSUPPORTED_FORMAT: '파일 형식 미지원',
    RESTRICTED: '기타 이용 제한'
  };

  // 서버 라이선스 코드(PUBLIC_DOMAIN, UNKNOWN 등)는 관리자에게 그대로 보이지 않게 읽을 수 있는 이름으로 바꾼다.
  // CC BY-SA 4.0 같은 표준 표기명은 그대로 쓴다.
  const LICENSE_TYPE_LABELS = {PUBLIC_DOMAIN: '퍼블릭 도메인', CC0: 'CC0', CC_BY: 'CC BY', CC_BY_SA: 'CC BY-SA'};

  function licenseText(photo) {
    if (photo.licenseType === 'PUBLIC_DOMAIN' || /^public domain$/i.test(photo.licenseName || '')) return '퍼블릭 도메인';
    if (photo.licenseName) return photo.licenseName;
    const type = LICENSE_TYPE_LABELS[photo.licenseType];
    return type ? [type, photo.licenseVersion].filter(Boolean).join(' ') : '라이선스 확인되지 않음';
  }

  function blockedText(photo) {
    const label = STATUS_LABELS[photo.reuseStatus] || '자동 저장 불가';
    return `선택 불가 · ${label} · ${photo.reviewReason || photo.saveBlockReason || '이용 조건을 확인해 주세요.'}`;
  }

  function safeLink(container, url, label, allowedHost, allowHttp = false) {
    try {
      const parsed = new URL(url);
      if (!(parsed.protocol === 'https:' || (allowHttp && parsed.protocol === 'http:'))
        || !allowedHost(parsed.hostname)) return null;
      const link = element('a', '', label);
      link.href = parsed.href;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      container.append(link);
      return link;
    } catch (_) {
      // An invalid upstream URL is not rendered as a link.
      return null;
    }
  }

  function thumbnail(photo) {
    try {
      const thumb = new URL(photo.thumbnailUrl);
      if (thumb.protocol === 'https:' && ['thumb.wikimedia.org', 'upload.wikimedia.org'].includes(thumb.hostname)
        && thumb.pathname.startsWith('/wikipedia/commons/')) {
        const image = element('img', 'admin-commons-card-thumb');
        image.src = thumb.href;
        image.alt = photo.fileName;
        // 서버가 준 240px 미리보기만 쓰고, 화면에 들어올 때 받아 비동기로 그린다.
        image.loading = 'lazy';
        image.decoding = 'async';
        image.addEventListener('error', () => image.remove());
        return image;
      }
    } catch (_) {
      // Invalid thumbnail URLs are never loaded by the browser.
    }
    return element('span', 'admin-commons-card-noimage', '미리보기 없음');
  }

  // 저작자·라이선스·원문 링크 등 긴 정보는 접어 둔다. 펼쳐도 카드 폭을 넘지 않게 CSS에서 줄바꿈한다.
  function sourceDetails(photo) {
    const details = element('details', 'admin-commons-card-details');
    details.append(element('summary', '', '출처 상세 보기'));
    const fields = element('dl', 'admin-wikidata-detail-grid');
    appendField(fields, '원본 파일명', photo.fileName);
    appendField(fields, '이미지 크기', photo.width && photo.height ? `${photo.width} × ${photo.height}px` : null);
    appendField(fields, '촬영자·저작자', photo.author);
    appendField(fields, '출처', photo.sourceCredit);
    if (photo.customAttribution) appendField(fields, '별도 크레딧', photo.customAttribution);
    appendField(fields, '라이선스', licenseText(photo));
    if (photo.conditions) appendField(fields, '이용 조건', photo.conditions);
    appendField(fields, '저작자 표시', photo.attributionRequired ? '필요' : '라이선스상 의무 없음');
    appendField(fields, '변경사항 표시', photo.changesRequired ? '필요' : '라이선스상 의무 없음');
    if (photo.shareAlikeRequired) appendField(fields, '동일조건변경허락', '수정물에 적용');
    if (photo.restrictions) appendField(fields, '추가 제한', photo.restrictions);
    if (photo.savable && photo.licenseEvidence) appendField(fields, '판별 근거', photo.licenseEvidence);
    const links = element('div', 'admin-wikidata-source-links');
    safeLink(links, photo.filePageUrl, 'Commons 파일·라이선스 확인', host => host === 'commons.wikimedia.org');
    safeLink(links, photo.sourceImageUrl, '원본 이미지', host => host === 'upload.wikimedia.org');
    safeLink(links, photo.licenseUrl, '라이선스 전문', host => host === 'creativecommons.org');
    for (const credit of photo.creditLinks || []) {
      safeLink(links, credit.url, `저작자·출처: ${credit.label}`, () => true, true);
    }
    details.append(fields, links);
    return details;
  }

  /**
   * @param options.selection      현재 선택 [{fileName, main}]
   * @param options.onChange       선택이 바뀔 때 새 배열을 받는다
   * @param options.registeredFileNames 이미 이 여행지에 있는 Commons 파일명. 선택할 수 없게 표시한다
   * @param options.loadMore       cursor 를 받아 다음 묶음 응답(Promise)을 돌려준다. 없으면 '사진 더 보기'를 숨긴다
   * @param options.selectionLimit 서버 한도보다 좁힐 선택 수. 1이면 새로 고른 사진으로 바꾼다
   * @param options.hideBlocked    저장할 수 없는 사진(라이선스·형식·이용 제한)과 너무 작은 사진은 카드로 그리지 않는다
   */
  function render(section, data, options = {}) {
    const renderId = {};
    renders.set(section, renderId);
    const stale = () => renders.get(section) !== renderId;
    const mainMode = options.mainMode || 'auto';
    const serverLimit = Number.isInteger(data.selectionLimit) && data.selectionLimit > 0
      ? data.selectionLimit : DEFAULT_SELECTION_LIMIT;
    // 화면이 더 좁게 제한할 수 있다(해외 일괄 등록은 여행지당 대표 사진 1장). 1장이면 새로 고른 사진으로 바꾼다.
    const limit = Number.isInteger(options.selectionLimit) && options.selectionLimit > 0
      ? Math.min(options.selectionLimit, serverLimit) : serverLimit;
    const registered = new Set(options.registeredFileNames || []);
    // 이미 등록된 사진은 선택에 남기지 않는다.
    let photos = (options.selection || []).filter(photo => !registered.has(photo.fileName)).map(photo => ({...photo}));
    let nextCursor = data.nextCursor || null;
    let loading = false;
    section.replaceChildren();
    section.append(element('h4', '', options.heading || 'Wikimedia Commons 사진 후보'));
    if (options.intro) section.append(element('p', 'admin-field-help', options.intro));
    if (data.message) section.append(element('p', 'admin-wikidata-review-note is-warning', data.message));
    if (!data.photos?.length && !nextCursor) {
      if (!data.message) section.append(element('p', 'admin-wikidata-review-note', '표시할 수 있는 사진이 없습니다.'));
      return;
    }

    const summary = element('p', 'admin-commons-picker-summary');
    summary.setAttribute('aria-live', 'polite');
    const grid = element('div', 'admin-commons-grid');
    const footer = element('div', 'admin-commons-picker-more');
    const shownCount = element('span', 'admin-commons-picker-count');
    const moreButton = element('button', 'admin-btn', '사진 더 보기');
    moreButton.type = 'button';
    const moreStatus = element('p', 'admin-wikidata-review-note is-warning');
    moreStatus.setAttribute('role', 'status');
    moreStatus.hidden = true;
    footer.append(shownCount, moreButton, moreStatus);
    section.append(summary, grid, footer);

    const controls = [];
    const shown = new Set();

    function change(next) {
      photos = next;
      refreshSelection();
      options.onChange?.(photos.map(photo => ({...photo})));
    }

    function refreshSelection() {
      const main = photos.find(photo => photo.main);
      summary.textContent = !photos.length
        ? (options.emptySelectionText || '선택한 사진 없음')
        : `${options.selectedLabel || '저장할 사진'} ${photos.length}/${limit}장 · 대표: ${main?.fileName
          || (mainMode === 'explicit' ? '기존 대표 사진 유지' : '없음')}`
          + (limit > 1 && photos.length >= limit ? ` · 최대 ${limit}장까지 선택할 수 있습니다` : '');
      for (const control of controls) {
        const entry = photos.find(photo => photo.fileName === control.fileName);
        control.checkbox.checked = Boolean(entry);
        // 한도에 닿으면 아직 고르지 않은 사진만 막는다. 이미 고른 사진은 해제할 수 있다.
        control.checkbox.disabled = !control.selectable || (!entry && limit > 1 && photos.length >= limit);
        // 대표 지정은 고른 사진에서만 한다.
        control.mainButton.hidden = !entry;
        control.mainButton.disabled = !entry || (mainMode === 'auto' && entry.main);
        control.mainButton.textContent = entry?.main
          ? (mainMode === 'explicit' ? '대표 지정 취소' : '대표 사진') : '대표로 지정';
        control.mainBadge.hidden = !entry?.main;
        control.card.classList.toggle('is-selected', Boolean(entry));
      }
    }

    function refreshMore() {
      shownCount.textContent = `사진 ${shown.size}장 표시 중`;
      moreButton.hidden = !nextCursor || typeof options.loadMore !== 'function';
      moreButton.disabled = loading;
      moreButton.textContent = loading ? '불러오는 중…' : '사진 더 보기';
    }

    let placeholder = null;

    function hidden(photo) {
      if (!options.hideBlocked || registered.has(photo.fileName)) return false;
      return !photo.savable || Math.min(photo.width || 0, photo.height || 0) < MIN_PHOTO_SHORT_SIDE;
    }

    function addCard(photo) {
      if (!photo?.fileName || shown.has(photo.fileName) || hidden(photo)) return;
      shown.add(photo.fileName);
      placeholder?.remove();
      placeholder = null;
      const alreadyRegistered = registered.has(photo.fileName);
      const selectable = Boolean(photo.savable) && !alreadyRegistered;
      const state = alreadyRegistered ? 'is-registered' : photo.savable ? 'is-available' : 'is-blocked';
      const card = element('article', `admin-commons-card ${state}`);

      // 사진을 누르면 선택된다(label). 선택할 수 없는 사진은 체크박스가 막혀 있다.
      const media = element('label', 'admin-commons-card-media');
      media.append(thumbnail(photo));
      const badges = element('span', 'admin-commons-card-badges');
      if (photo.source === 'P18') badges.append(element('span', 'admin-commons-badge is-p18', 'Wikidata 대표 이미지'));
      const mainBadge = element('span', 'admin-commons-badge is-main', '대표');
      mainBadge.hidden = true;
      badges.append(mainBadge);
      // name 없는 입력은 multipart 에 실리지 않는다. 서버로는 hidden 선택값 한 칸만 보낸다.
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.className = 'admin-commons-card-check';
      checkbox.disabled = !selectable;
      checkbox.setAttribute('aria-label', `${photo.fileName} ${options.actionLabel || '선택'}`);
      checkbox.addEventListener('change', () => change(togglePhoto(
        limit === 1 && checkbox.checked ? [] : photos, photo.fileName, checkbox.checked, mainMode)));
      media.append(badges, checkbox);

      const body = element('div', 'admin-commons-card-body');
      const title = element('strong', 'admin-commons-card-title', photo.fileName);
      title.setAttribute('title', photo.fileName);
      const meta = element('p', 'admin-commons-card-meta',
        `${photo.author || '저작자 정보 없음'} · ${licenseText(photo)}`);
      const stateText = alreadyRegistered ? '이미 이 여행지에 등록된 사진'
        : photo.savable ? '저장 가능' : blockedText(photo);
      const status = element('p', 'admin-commons-card-state', stateText);
      status.setAttribute('title', stateText);
      const mainButton = element('button', 'admin-btn is-small', '대표로 지정');
      mainButton.type = 'button';
      mainButton.hidden = true;
      mainButton.addEventListener('click', () => change(setMain(photos, photo.fileName, mainMode)));
      body.append(title, meta, status, mainButton);
      // 사진 자체는 선택 영역이라, 원본 파일 페이지는 별도 링크로 새 탭에서 연다.
      const filePageLink = safeLink(body, photo.filePageUrl, 'Commons에서 보기', host => host === 'commons.wikimedia.org');
      if (filePageLink) filePageLink.className = 'admin-commons-card-link';
      body.append(sourceDetails(photo));

      card.append(media, body);
      controls.push({fileName: photo.fileName, selectable, checkbox, mainButton, mainBadge, card});
      grid.append(card);
    }

    moreButton.addEventListener('click', () => {
      if (loading || !nextCursor || typeof options.loadMore !== 'function') return;
      loading = true;
      moreStatus.hidden = true;
      refreshMore();
      Promise.resolve(options.loadMore(nextCursor)).then(page => {
        if (stale()) return;
        (page?.photos || []).forEach(addCard);
        nextCursor = page?.nextCursor || null;
        if (page?.message) {
          moreStatus.textContent = page.message;
          moreStatus.hidden = false;
        } else if (!nextCursor) {
          moreStatus.textContent = '더 볼 수 있는 사진이 없습니다.';
          moreStatus.hidden = false;
        }
        refreshSelection();
      }).catch(error => {
        if (stale()) return;
        // 실패하면 같은 위치에서 다시 시도할 수 있게 버튼을 남긴다.
        moreStatus.textContent = error?.message || '사진을 더 불러오지 못했습니다. 다시 시도해 주세요.';
        moreStatus.hidden = false;
      }).finally(() => {
        if (stale()) return;
        loading = false;
        refreshMore();
      });
    });

    (data.photos || []).forEach(addCard);
    if (!shown.size) {
      placeholder = element('p', 'admin-wikidata-review-note', nextCursor
        ? '앞쪽 파일에 표시할 사진이 없습니다. 사진 더 보기로 이어서 찾아보세요.'
        : '사용할 수 있는 라이선스의 사진이 없습니다.');
      grid.append(placeholder);
    }
    refreshSelection();
    refreshMore();
  }

  root.TripBoraCommonsPhotoPicker = {togglePhoto, setMain, serializeSelection, render};
  root.TravelDiaryCommonsPhotoPicker = root.TripBoraCommonsPhotoPicker; // legacy alias (P10a 제거)
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {togglePhoto, setMain, serializeSelection, render};
  }
})(typeof window !== 'undefined' ? window : globalThis);
