document.addEventListener('DOMContentLoaded', () => {
  const root = document.querySelector('[data-wikidata-preview]');
  if (!root) return;

  const keyword = root.querySelector('[data-wikidata-keyword]');
  const searchButton = root.querySelector('[data-wikidata-search]');
  const status = root.querySelector('[data-wikidata-status]');
  const results = root.querySelector('[data-wikidata-results]');
  const detail = root.querySelector('[data-wikidata-detail]');
  const form = document.querySelector('form[data-translation-collapsible]');
  const applyPlanner = window.TravelDiaryWikidataApplyPlanner;
  const wikipediaPlanner = window.TravelDiaryWikipediaDescriptionPlanner;
  const pendingWikipedia = new Map();
  const automaticValues = {};
  const responseCache = new Map();
  const wikipediaArea = form?.querySelector('[data-wikidata-wikipedia-area]');
  const commonsArea = form?.querySelector('[data-wikidata-commons-area]');
  const commonsSelectionField = form?.querySelector('[data-commons-selection]');
  const commonsPicker = window.TravelDiaryCommonsPhotoPicker;
  const commonsSelection = {qid: null, photos: []};
  let restoredCommonsSelection = null;
  let wikipediaSummary = null;
  let selectedQid = form?.querySelector('[data-wikidata-qid]')?.value || null;
  let automaticRegionId = null;
  // 지금 고른 후보의 기본정보 상태: pending → ready | failed. Wikipedia 적용 여부 판단에 쓴다.
  let basicState = null;
  // 번체 제목 보완은 Wikidata 제목 유무(기본정보)와 Wikipedia 변환 제목이 모두 있어야 판단할 수 있다.
  let currentBasicData = null;
  let currentWikipediaData = null;
  let searchController = null;
  const managerChoices = new Set();
  const baseUrl = '/admin/api/wikidata/destinations';
  const languages = [
    ['ko', '한국어'], ['en', '영어'], ['ja', '일본어'],
    ['zh-CN', '중국어(간체)'], ['zh-TW', '중국어(번체)']
  ];
  let searchNumber = 0;
  let selectionNumber = 0;

  function element(tag, className, value) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (value != null) node.textContent = value;
    return node;
  }

  function showStatus(message, isError = false) {
    status.textContent = message;
    status.classList.toggle('is-error', isError);
  }

  async function getJson(url, signal) {
    let response;
    try {
      response = await fetch(url, { headers: { Accept: 'application/json' }, signal });
    } catch (error) {
      if (error?.name === 'AbortError') throw error;
      throw new Error('서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
    if (response.redirected && new URL(response.url).pathname.startsWith('/login')) {
      throw new Error('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.');
    }
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(body && body.message ? body.message : 'Wikidata 요청을 처리하지 못했습니다.');
    }
    if (body == null) throw new Error('서버 응답을 확인하지 못했습니다. 다시 시도해 주세요.');
    return body;
  }

  // 같은 QID 요청은 진행 중인 것까지 5분 동안 공유한다. 실패한 응답은 남기지 않는다.
  // 공유 요청이라 중단 신호를 붙이지 않는다. 오래된 후보의 응답은 selectionNumber 로 걸러 적용하지 않는다.
  // Commons '사진 더 보기'는 cursor 별로 따로 둔다.
  function getCandidateData(kind, qid, cursor = null) {
    const key = cursor ? `${kind}:${qid}:${cursor}` : `${kind}:${qid}`;
    const cached = responseCache.get(key);
    if (cached && Date.now() - cached.at < 5 * 60 * 1000) return cached.promise;
    const promise = getJson(`${baseUrl}/${kind}?qid=${encodeURIComponent(qid)}`
      + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''));
    responseCache.set(key, {at: Date.now(), promise});
    promise.catch(() => {
      if (responseCache.get(key)?.promise === promise) responseCache.delete(key);
    });
    return promise;
  }

  function addImage(container, url, name) {
    if (!url || !url.startsWith('https://commons.wikimedia.org/')) return;
    const image = element('img', 'admin-wikidata-image');
    image.src = url;
    image.alt = name ? `${name} 대표 이미지` : 'Wikidata 대표 이미지';
    image.loading = 'lazy';
    image.addEventListener('error', () => image.remove());
    container.append(image);
  }

  function appendField(container, label, value) {
    const row = element('div', 'admin-wikidata-field');
    row.append(element('dt', '', label), element('dd', '', value || '제공되지 않음'));
    container.append(row);
  }

  function safeLink(container, url, label, allowedHost, allowHttp = false) {
    try {
      const parsed = new URL(url);
      if (!(parsed.protocol === 'https:' || (allowHttp && parsed.protocol === 'http:'))
        || !allowedHost(parsed.hostname)) return;
      const link = element('a', '', label);
      link.href = parsed.href;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      container.append(link);
    } catch (_) {
      // An invalid upstream URL is not rendered as a link.
    }
  }

  // 저장할 Commons 사진은 QID와 함께 파일명·대표 여부만 hidden 한 칸에 담는다.
  // 후보를 바꾸면 비우므로 이전 QID의 선택이 새 후보와 섞이지 않는다.
  function writeCommonsSelection() {
    if (!commonsSelectionField) return;
    commonsSelectionField.value = commonsSelection.photos.length
      ? JSON.stringify({qid: commonsSelection.qid, photos: commonsSelection.photos}) : '';
  }

  function resetCommonsSelection(qid) {
    commonsSelection.qid = qid;
    commonsSelection.photos = [];
    writeCommonsSelection();
  }

  function storedCommonsSelection() {
    try {
      const stored = JSON.parse(commonsSelectionField?.value || '');
      return stored && typeof stored.qid === 'string' && Array.isArray(stored.photos) ? stored : null;
    } catch (_) {
      return null;
    }
  }

  function renderCommons(section, data, current) {
    section.replaceChildren();
    if (commonsSelection.qid !== data.qid) resetCommonsSelection(data.qid);
    if (restoredCommonsSelection?.qid === data.qid) {
      for (const photo of restoredCommonsSelection.photos) {
        if (typeof photo?.fileName !== 'string' || commonsSelection.photos.some(item => item.fileName === photo.fileName)) continue;
        // 첫 묶음에서 저장 불가로 확인된 사진만 뺀다. '사진 더 보기'로 고른 사진은 첫 묶음에 없으니 그대로 두고,
        // 저장할 때 서버가 다시 확인한다.
        const candidate = data.photos?.find(item => item.fileName === photo.fileName);
        if (candidate && !candidate.savable) continue;
        commonsSelection.photos.push({fileName: photo.fileName, main: photo.main === true});
      }
      if (commonsSelection.photos.filter(photo => photo.main).length !== 1) {
        commonsSelection.photos.forEach((photo, index) => { photo.main = index === 0; });
      }
      writeCommonsSelection();
    }
    restoredCommonsSelection = null;
    commonsPicker.render(section, data, {
      mainMode: 'auto',
      selection: commonsSelection.photos,
      intro: 'Wikidata 대표 이미지와 Commons 카테고리의 JPEG·PNG 사진입니다. 여행지와 맞는 사진인지 확인해 고르면, 등록할 때 서버가 출처·라이선스를 다시 확인해 저장합니다.',
      selectedLabel: '저장할 사진',
      emptySelectionText: '선택한 사진 없음 · 사진 없이 등록됩니다.',
      actionLabel: '저장할 사진으로 선택',
      // 다음 묶음은 같은 QID·위치를 공유 캐시로 한 번만 받는다. 후보를 바꾼 뒤 도착한 응답은 버린다.
      loadMore: cursor => getCandidateData('commons-photos', data.qid, cursor).then(page => {
        if (current !== selectionNumber || page.qid !== data.qid) {
          throw new Error('선택한 후보가 바뀌어 사진을 더 불러오지 않았습니다.');
        }
        return page;
      }),
      onChange: photos => {
        commonsSelection.photos = photos;
        writeCommonsSelection();
      }
    });
  }

  function renderWikipedia(section, data) {
    section.replaceChildren();
    section.append(element('h4', '', 'Wikipedia 다국어 상세 설명'));
    wikipediaSummary = element('p', 'admin-wikidata-source-summary');
    section.append(wikipediaSummary);
    refreshWikipediaSummary();
    const details = element('details', 'admin-wikidata-source-details');
    const toggle = element('summary');
    toggle.append(element('span', 'admin-wikidata-toggle-closed', '출처 상세 보기'),
      element('span', 'admin-wikidata-toggle-open', '출처 상세 접기'));
    details.append(toggle);
    const cards = element('div', 'admin-wikidata-languages');
    for (const [code, label] of languages) {
      const entry = data.languages?.find(item => item.language === code);
      const card = element('section', 'admin-wikidata-language');
      card.append(element('h4', '', `${label} (${code})`));
      if (!entry || entry.status !== 'AVAILABLE') {
        card.append(element('p', 'admin-wikidata-review-note is-warning',
          `미적용 · ${entry?.message || '이 언어의 소개 내용이 제공되지 않습니다.'}`));
      } else {
        const fields = element('dl', 'admin-wikidata-detail-grid');
        appendField(fields, '문서 제목', entry.title);
        if (entry.variant) appendField(fields, '변환 표기 제목', entry.displayTitle);
        // 중국어 두 칸은 같은 중국어 Wikipedia 문서를 간체·번체 표기로 변환한 것이라 원문과 구분해 적는다.
        appendField(fields, '원문', entry.variant
          ? `중국어 Wikipedia · ${entry.variant === 'zh-tw' ? '번체' : '간체'} 표기로 자동 변환`
          : `${label} Wikipedia 원문`);
        appendField(fields, '판본 ID', entry.revisionId == null ? null : String(entry.revisionId));
        appendField(fields, '라이선스', entry.licenseName || '확인 필요');
        card.append(fields);
        const source = element('div', 'admin-wikidata-source-links');
        safeLink(source, entry.sourceUrl, 'Wikipedia 원문 확인',
          host => host === `${entry.sourceLanguage}.wikipedia.org`);
        safeLink(source, entry.licenseUrl, '라이선스 확인', host => host === 'creativecommons.org');
        card.append(source, element('small', '', '출처: Wikipedia 기여자'));
        const applied = pendingWikipedia.get(code);
        if (applied) {
          applied.detailStatus = element('p', 'admin-wikidata-review-note');
          updateWikipediaSourceState(applied);
          card.append(applied.detailStatus);
        } else if (form?.elements.namedItem(`translations[${languages.findIndex(item => item[0] === code)}].description`)?.value?.trim()) {
          card.append(element('p', 'admin-wikidata-review-note',
            '미적용 · 기존 관리자 입력값을 유지했습니다. Wikipedia 출처는 연결하지 않습니다.'));
        } else {
          card.append(element('p', 'admin-wikidata-review-note is-warning',
            `미적용 · ${wikipediaOmissionReason(code, entry)}`));
        }
      }
      cards.append(card);
    }
    details.append(cards);
    section.append(details);
  }

  function wikipediaOmissionReason(code, entry) {
    const expectedLanguage = code.startsWith('zh-') ? 'zh' : code;
    const expectedVariant = code.startsWith('zh-') ? code.toLowerCase() : null;
    if (entry.sourceLanguage !== expectedLanguage || (entry.variant || null) !== expectedVariant) {
      return '원문 언어 또는 중국어 변형이 이 입력칸과 일치하지 않습니다.';
    }
    if (!String(entry.description ?? '').trim()) return '원문 설명이 제공되지 않습니다.';
    return '자동입력에 필요한 판본 또는 라이선스 정보를 확인하지 못했습니다.';
  }

  function appliedWikipediaCodes() {
    return languages.filter(([code]) => String(pendingWikipedia.get(code)?.field.value ?? '').trim())
      .map(([code]) => code);
  }

  function refreshWikipediaSummary() {
    if (wikipediaSummary && wikipediaPlanner) {
      wikipediaSummary.textContent = wikipediaPlanner.summarizeWikipediaApplication(appliedWikipediaCodes());
    }
  }

  // 설명을 비우면 그 언어의 판본 연결도 비워 출처 없이 등록되게 한다. 다시 입력하면 관리자 수정본으로 다시 연결한다.
  function syncWikipediaRevision(item) {
    const revision = form?.querySelector(`[data-wikipedia-revision="${item.change.code}"]`);
    if (revision) revision.value = String(item.field.value ?? '').trim() ? String(item.change.revisionId) : '';
  }

  function updateWikipediaSourceState(item) {
    if (!item.detailStatus) return;
    item.detailStatus.textContent = !String(item.field.value).trim()
      ? '설명을 비워 이 언어는 Wikipedia 출처 없이 등록됩니다.'
      : `등록폼에 적용됨 · 수정 여부: ${item.field.value === item.change.value ? '변경 없음' : '관리자 수정'}`;
  }

  function attachWikipediaSource(field, change) {
    pendingWikipedia.set(change.code, {field, change});
  }

  function applyWikipedia(data) {
    if (!form || !wikipediaPlanner || data.qid !== selectedQid) return 0;
    const plan = wikipediaPlanner.planWikipediaDescriptions(data, formValues());
    let filled = 0;
    for (const change of plan.changes) {
      const field = form.elements.namedItem(change.name);
      const revision = form.querySelector(`[data-wikipedia-revision="${change.code}"]`);
      if (!revision || !wikipediaPlanner.stagePreviewDescription(field, change)) continue;
      // The visible textarea must remain a named form control for the final registration POST.
      if (field.getAttribute('name') !== change.name) field.setAttribute('name', change.name);
      automaticValues[change.name] = field.value;
      field.dataset.autofill = 'wikipedia';
      revision.value = String(change.revisionId);
      attachWikipediaSource(field, change);
      field.dispatchEvent(new Event('input', {bubbles: true}));
      filled++;
    }
    return filled;
  }

  // Wikidata에 번체 제목이 없을 때만 Wikipedia 번체 변환 제목으로 빈 제목 칸을 채운다.
  // 자동입력값으로 기록해 두므로 후보를 바꾸면 손대지 않은 제목은 함께 지워지고, 관리자가 고친 제목은 남는다.
  function applyTitleFallback(current) {
    if (!form || !wikipediaPlanner?.planWikipediaTitleFallback || current !== selectionNumber
        || basicState !== 'ready' || !currentBasicData || !currentWikipediaData) return;
    const changes = wikipediaPlanner.planWikipediaTitleFallback(
      currentWikipediaData, currentBasicData.names, formValues());
    for (const change of changes) {
      const field = form.elements.namedItem(change.name);
      if (!field || String(field.value ?? '').trim()
          || (field.maxLength >= 0 && change.value.length > field.maxLength)) continue;
      setAutomaticValue(field, change.name, change.value, 'wikipedia');
      wikipediaArea?.append(element('p', 'admin-wikidata-review-note',
        `${change.label}: Wikidata에 제목이 없어 Wikipedia 문서의 번체 표기 제목(${change.value})을 자동입력했습니다. 확인해 주세요.`));
    }
  }

  function formValues() {
    const values = {};
    if (!form) return values;
    for (const field of form.elements) {
      if (field.name && 'value' in field) values[field.name] = field.value;
    }
    return values;
  }

  function regionOccupied() {
    const region = form && form.querySelector('[data-region-field]');
    if (!region) return false;
    return Boolean(region.dataset.regionMode || region.querySelector('#regionIdHidden')?.value
      || Array.from(region.querySelectorAll('select')).some(select => select.value));
  }

  function setStage(stage, message, state = '') {
    const node = root.querySelector(`[data-wikidata-stage="${stage}"]`);
    if (!node) return;
    node.textContent = message;
    node.classList.toggle('is-error', state === 'error');
    node.classList.toggle('is-warning', state === 'warning');
  }

  function clearAppliedWikipedia() {
    // Wikipedia fields keep an independent source marker. Clear only untouched text from
    // the old QID; edited text becomes an ordinary manager-written description.
    for (const item of pendingWikipedia.values()) {
      if (item.field.getAttribute('name') !== item.change.name) {
        item.field.setAttribute('name', item.change.name);
      }
      if (item.field.value === item.change.value) {
        item.field.value = '';
        item.field.dispatchEvent(new Event('input', {bubbles: true}));
        item.field.dispatchEvent(new Event('change', {bubbles: true}));
      }
      item.field.removeAttribute('data-wikipedia-original-name');
      delete automaticValues[item.change.name];
    }
    pendingWikipedia.clear();
    wikipediaSummary = null;
    form?.querySelectorAll('[data-wikipedia-revision]').forEach(field => { field.value = ''; });
  }

  function clearPreviousCandidate() {
    clearAppliedWikipedia();
    const clearNames = applyPlanner.automaticFieldsToClear(formValues(), automaticValues);
    for (const name of clearNames) {
      const field = form?.elements.namedItem(name);
      if (field && 'value' in field) {
        field.value = '';
        field.removeAttribute('data-wikipedia-original-name');
        field.dispatchEvent(new Event('input', {bubbles: true}));
        field.dispatchEvent(new Event('change', {bubbles: true}));
      }
    }
    for (const name of Object.keys(automaticValues)) delete automaticValues[name];
    for (const name of [...conversionNotes.keys()]) removeConversionNote(name);
    const region = form?.querySelector('#regionIdHidden');
    if (automaticRegionId && region) {
      window.TravelDiaryRegionSelector?.clearSelection();
    }
    automaticRegionId = null;
  }

  // '중국어 표기 자동 변환' 안내. 변환 문장이 자동입력된 칸 바로 아래에 두고,
  // 관리자가 칸을 고치거나 후보를 바꾸면 지운다.
  const conversionNotes = new Map();

  function attachConversionNote(field, name, text) {
    removeConversionNote(name);
    const note = element('span', 'admin-wikidata-conversion-note', text);
    field.after(note);
    conversionNotes.set(name, note);
  }

  function removeConversionNote(name) {
    conversionNotes.get(name)?.remove();
    conversionNotes.delete(name);
  }

  // 자동입력값을 기록하고 칸에 출처 표시(data-autofill)를 남긴다. 관리자가 값을 바꾸면 input 처리에서 표시를 지운다.
  function setAutomaticValue(field, name, value, source = 'wikidata') {
    field.value = value;
    automaticValues[name] = value;
    field.dataset.autofill = source;
    field.dispatchEvent(new Event('input', {bubbles: true}));
    field.dispatchEvent(new Event('change', {bubbles: true}));
  }

  function applyBasic(data, current) {
    // 이전 후보 정리는 선택 시점(preview)에 끝냈다. 여기서는 지금 고른 후보의 응답만 적용한다.
    if (!form || !applyPlanner || current !== selectionNumber || data.qid !== selectedQid) return null;
    const qidField = form.querySelector('[data-wikidata-qid]');
    if (qidField) qidField.value = data.qid;
    for (const name of ['type', 'season']) {
      const field = form.elements.namedItem(name);
      if (field && !managerChoices.has(name)) field.value = '';
      if (field && name === 'season') field.required = true;
    }
    const plan = applyPlanner.planAutofill(data, formValues(), regionOccupied());
    let filled = 0;
    const omitted = [];
    const converted = [];
    for (const change of plan.changes) {
      const field = form.elements.namedItem(change.name);
      if (!field || !('value' in field) || String(field.value ?? '').trim()) continue;
      if (field.maxLength >= 0 && change.value.length > field.maxLength) {
        omitted.push(change.label);
        continue;
      }
      setAutomaticValue(field, change.name, change.value, change.source || 'wikidata');
      if (change.source === 'chinese-conversion') {
        attachConversionNote(field, change.name, change.note);
        converted.push(change.label);
      }
      filled++;
    }
    const conversionNote = converted.length ? ` · 중국어 표기 자동 변환: ${converted.join(', ')}` : '';
    // 공식 웹사이트·전화번호는 유형별 상세정보 칸에 따로 채우고 기본정보 개수에는 넣지 않는다.
    const travelLabels = new Set();
    for (const change of plan.travelChanges || []) {
      const field = form.elements.namedItem(change.name);
      if (!field || !('value' in field) || String(field.value ?? '').trim()) continue;
      setAutomaticValue(field, change.name, change.value);
      travelLabels.add(change.label);
    }
    // 기본정보 단계 문구 뒤에 붙는 안내: 상세정보 자동입력, 중국어 표기 자동 변환.
    const travelNote = (travelLabels.size ? ` · 상세정보 ${[...travelLabels].join('·')} 자동입력` : '')
      + conversionNote;
    const missing = [];
    if (plan.regionPath && !regionOccupied() && window.TravelDiaryRegionSelector) {
      automaticRegionId = String(plan.regionPath.at(-1).id);
      void window.TravelDiaryRegionSelector.applyRegionPath(plan.regionPath).then(applied => {
        if (current !== selectionNumber) return;
        if (applied) {
          automaticRegionId = String(plan.regionPath.at(-1).id);
          setStage('basic', `기본정보 완료 · ${filled}개 입력${travelNote}, 국가·지역 자동 선택${missing.length ? ` · 확인 필요: ${missing.join(', ')}` : ''}`,
            missing.length ? 'warning' : '');
        } else {
          automaticRegionId = null;
          setStage('basic', `기본정보 완료 · ${filled}개 입력${travelNote} · 확인 필요: 국가·지역${missing.length ? `, ${missing.join(', ')}` : ''}`, 'warning');
        }
      }).catch(() => {
        if (current === selectionNumber) {
          automaticRegionId = null;
          setStage('basic', `기본정보 완료 · ${filled}개 입력${travelNote} · 확인 필요: 국가·지역${missing.length ? `, ${missing.join(', ')}` : ''}`, 'warning');
        }
      });
    }
    if (!data.names?.ko && !String(form.elements.namedItem('translations[0].name')?.value ?? '').trim()) {
      missing.push('한국어 여행지명');
    }
    if (plan.regionStatus === 'manual') missing.push('국가·지역');
    if (!form.elements.namedItem('type')?.value) missing.push('여행지 유형');
    if (!form.elements.namedItem('season')?.value) missing.push('시즌');
    if (omitted.length) missing.push(`길이 초과: ${omitted.join(', ')}`);
    setStage('basic', `기본정보 완료 · ${filled}개 입력${travelNote}${missing.length ? ` · 확인 필요: ${missing.join(', ')}` : ''}`,
      missing.length ? 'warning' : '');
    return plan;
  }

  function fieldLabel(name) {
    const match = /^translations\[([0-4])\]\.(name|shortDescription|description)$/.exec(name);
    if (match) {
      const title = {name: '여행지명', shortDescription: '간단 설명', description: '상세 설명'}[match[2]];
      return `${languages[Number(match[1])][1]} ${title}`;
    }
    return name === 'latitude' ? '위도' : name === 'longitude' ? '경도' : name;
  }

  function chooseCandidate(qid) {
    if (qid === selectedQid) {
      void preview(qid);
      return;
    }
    const review = root.querySelector('[data-wikidata-switch-review]');
    review?.replaceChildren();
    const conflicts = selectedQid && applyPlanner
      ? applyPlanner.manualConflicts(formValues(), automaticValues) : [];
    if (selectedQid && regionOccupied() && !automaticRegionId) conflicts.push({name: '국가·지역'});
    if (conflicts.length && review) {
      review.append(element('h4', '', `${qid} 후보로 변경하기`));
      review.append(element('p', 'admin-wikidata-review-note is-warning',
        '관리자가 입력하거나 수정한 다음 항목은 유지합니다. 이전 후보의 자동입력값과 Wikipedia 출처 연결은 새 후보 값으로 바뀝니다.'));
      review.append(element('p', 'admin-wikidata-review-note',
        conflicts.map(item => fieldLabel(item.name)).join(', ')));
      const actions = element('div', 'admin-wikidata-review-actions');
      const confirm = element('button', 'admin-btn is-primary', '수동 입력 유지하고 후보 변경');
      confirm.type = 'button';
      confirm.addEventListener('click', () => {
        review.replaceChildren();
        void preview(qid);
      });
      const cancel = element('button', 'admin-btn', '취소');
      cancel.type = 'button';
      cancel.addEventListener('click', () => review.replaceChildren());
      actions.append(confirm, cancel);
      review.append(actions);
      showStatus('수동 입력값을 확인한 뒤 후보를 변경해 주세요.');
      return;
    }
    void preview(qid);
  }

  function renderCandidates(candidates, detailsPending = false) {
    results.replaceChildren();
    for (const candidate of candidates) {
      const card = element('article', 'admin-wikidata-candidate');
      addImage(card, candidate.imageUrl, candidate.name);
      const text = element('div', 'admin-wikidata-candidate-text');
      text.append(element('strong', '', candidate.name || '명칭 없음'));
      text.append(element('span', 'admin-wikidata-qid', candidate.qid));
      text.append(element('p', '', candidate.shortDescription || '간단 설명 없음'));
      const location = element('small', '', detailsPending ? '국가·지역 확인 중'
        : [candidate.country, candidate.region].filter(Boolean).join(' · ') || '국가·지역 정보 없음');
      location.dataset.candidateLocation = '';
      text.append(location);
      card.append(text);
      const button = element('button', 'admin-btn', '선택하고 자동입력');
      button.type = 'button';
      button.setAttribute('aria-label', `${candidate.name || candidate.qid} (${candidate.qid}) 선택하고 자동입력`);
      button.addEventListener('click', () => chooseCandidate(candidate.qid));
      card.append(button);
      results.append(card);
    }
  }

  async function search() {
    const query = keyword.value.trim();
    if (query.length < 2 || query.length > 100) {
      showStatus('검색어를 2~100자로 입력해 주세요.', true);
      return;
    }
    const current = ++searchNumber;
    searchController?.abort();
    searchController = new AbortController();
    results.replaceChildren();
    showStatus('Wikidata 후보를 검색하는 중입니다.');
    searchButton.disabled = true;
    try {
      // 목록은 검색 1회 결과로 먼저 보여주고, 국가·지역·이미지는 이어서 채운다.
      const candidates = await getJson(`${baseUrl}/search?keyword=${encodeURIComponent(query)}`,
        searchController.signal);
      if (current !== searchNumber) return;
      if (!candidates.length) {
        showStatus('검색 결과가 없습니다. 다른 한국어 또는 영어 명칭으로 검색해 주세요.');
        return;
      }
      renderCandidates(candidates, true);
      showStatus(`${candidates.length}개 후보를 찾았습니다. 국가·지역 정보를 확인하는 중입니다.`);
      void loadSearchDetails(candidates, current, searchController.signal);
    } catch (error) {
      if (current === searchNumber && error?.name !== 'AbortError') showStatus(error.message, true);
    } finally {
      if (current === searchNumber) searchButton.disabled = false;
    }
  }

  async function loadSearchDetails(candidates, current, signal) {
    const params = new URLSearchParams();
    for (const candidate of candidates) params.append('qids', candidate.qid);
    try {
      const details = await getJson(`${baseUrl}/search-details?${params}`, signal);
      if (current !== searchNumber) return;
      const merged = applyPlanner.mergeSearchDetails(candidates, details);
      if (!merged.length) {
        results.replaceChildren();
        showStatus('장소로 확인된 후보가 없습니다. 다른 한국어 또는 영어 명칭으로 검색해 주세요.');
        return;
      }
      renderCandidates(merged);
      showStatus(`${merged.length}개 후보를 찾았습니다. QID와 국가·지역을 확인한 뒤 선택하세요.`);
    } catch (error) {
      if (current !== searchNumber || error?.name === 'AbortError') return;
      results.querySelectorAll('[data-candidate-location]').forEach(node => {
        node.textContent = '국가·지역 정보를 불러오지 못했습니다';
      });
      showStatus(`${candidates.length}개 후보를 찾았습니다. 국가·지역 정보를 불러오지 못했으니 QID를 확인한 뒤 선택하세요.`, true);
    }
  }

  function renderPreview(data) {
    detail.replaceChildren();
    detail.hidden = false;
    detail.append(element('h3', '', `선택한 후보 · ${data.qid}`));
    const grid = element('dl', 'admin-wikidata-detail-grid');
    appendField(grid, '국가', data.country ? `${data.country} (${data.countryQid})` : data.countryQid);
    appendField(grid, '지역', data.regionPath?.length ? data.regionPath.join(' → ') : null);
    appendField(grid, '위도·경도', data.latitude != null && data.longitude != null
      ? `${data.latitude}, ${data.longitude}` : null);
    appendField(grid, '공식 웹사이트', data.travelInfo?.homepageUrl ? `${data.travelInfo.homepageUrl} (Wikidata P856)` : null);
    appendField(grid, '전화번호', data.travelInfo?.contactNumber ? `${data.travelInfo.contactNumber} (Wikidata P1329)` : null);
    detail.append(grid);
    // 운영시간·휴관일·입장료는 자주 바뀌고 Wikidata 값의 확인 시점도 불명확해 자동입력하지 않는다.
    const travelGuide = element('div', 'admin-wikidata-review-note');
    travelGuide.append(element('span', '',
      '운영시간·휴관일·입장료는 자주 바뀌어 자동입력하지 않습니다. 공식 웹사이트에서 최신 정보를 확인해 직접 입력해 주세요.'));
    if (data.travelInfo?.openingHoursStated || data.travelInfo?.admissionFeeStated) {
      const stated = [data.travelInfo.openingHoursStated && '운영시간', data.travelInfo.admissionFeeStated && '입장료']
        .filter(Boolean).join('·');
      travelGuide.append(element('span', '',
        ` Wikidata에 ${stated} 정보가 있지만 확인 시점과 적용 조건이 불분명해 참고용으로만 안내합니다.`));
      safeLink(travelGuide, `https://www.wikidata.org/wiki/${encodeURIComponent(data.qid)}`,
        'Wikidata 항목 보기', host => host === 'www.wikidata.org');
    }
    if (data.travelInfo?.homepageUrl) {
      safeLink(travelGuide, data.travelInfo.homepageUrl, '공식 웹사이트 열기', () => true, true);
    }
    detail.append(travelGuide);
    const match = data.regionMatch;
    detail.append(element('p', `admin-wikidata-match${match?.matched ? '' : ' is-review'}`,
      `기존 지역 매핑: ${match?.message || '국가·지역을 직접 확인해 주세요.'}`));
    detail.append(element('p', 'admin-field-help',
      '비어 있던 칸만 자동입력합니다. 자동입력 칸은 옅은 파란색, 다른 중국어 표기에서 변환한 간단 설명은 옅은 노란색으로 표시되며 직접 고치면 관리자 입력으로 바뀝니다.'));
  }

  // 기본정보를 기다리지 않고 도착하는 대로 적용한다. 기본정보 조회가 실패하면 QID가 확정되지 않으므로
  // 그때 관리자가 손대지 않은 Wikipedia 자동입력과 판본 연결을 거둬들인다(withdrawWikipedia).
  async function loadWikipedia(qid, current) {
    try {
      const data = await getCandidateData('wikipedia', qid);
      if (current !== selectionNumber) return;
      if (basicState === 'failed') {
        setStage('wikipedia', 'Wikipedia 설명 조회 완료 · 기본정보를 확인하지 못해 자동입력을 보류했습니다.', 'warning');
        return;
      }
      const filled = applyWikipedia(data);
      renderWikipedia(wikipediaArea, data);
      currentWikipediaData = data;
      applyTitleFallback(current);
      const available = data.languages?.filter(item => item.status === 'AVAILABLE').length || 0;
      const unavailable = languages.length - available;
      setStage('wikipedia', `Wikipedia 설명 완료 · ${filled}개 자동입력${unavailable ? ` · ${unavailable}개 언어 미제공·확인 필요` : ''}`,
        unavailable ? 'warning' : '');
    } catch (error) {
      if (current !== selectionNumber || error?.name === 'AbortError') return;
      wikipediaSummary = null;
      wikipediaArea.replaceChildren(
        element('p', 'admin-wikidata-review-note is-warning', error.message));
      setStage('wikipedia', `Wikipedia 설명 조회 실패 · ${error.message}`, 'error');
    } finally {
      if (current === selectionNumber && form) delete form.dataset.wikipediaLoading;
    }
  }

  function withdrawWikipedia() {
    if (!pendingWikipedia.size) return;
    clearAppliedWikipedia();
    wikipediaArea.replaceChildren(element('p', 'admin-wikidata-review-note is-warning',
      '기본정보를 확인하지 못해 Wikipedia 자동입력을 취소했습니다. 후보를 다시 선택해 주세요.'));
    setStage('wikipedia', 'Wikipedia 자동입력 취소 · 기본정보를 확인하지 못했습니다.', 'warning');
  }

  async function loadCommons(qid, current) {
    try {
      const data = await getCandidateData('commons-photos', qid);
      if (current !== selectionNumber) return;
      renderCommons(commonsArea, data, current);
      const state = data.status === 'ERROR' ? 'error' : data.status === 'PARTIAL' || data.status === 'NO_PHOTOS'
        ? 'warning' : '';
      const text = data.status === 'ERROR' ? 'Commons 사진 조회 실패'
        : data.photos?.length ? `Commons 사진 완료 · ${data.photos.length}장${data.nextCursor ? ' · 더 보기 가능' : ''}`
          : data.nextCursor ? 'Commons 사진 · 더 보기로 찾기' : 'Commons 사진 없음';
      setStage('commons', text, state);
    } catch (error) {
      if (current !== selectionNumber || error?.name === 'AbortError') return;
      commonsArea.replaceChildren(
        element('p', 'admin-wikidata-review-note is-warning', error.message));
      setStage('commons', `Commons 사진 조회 실패 · ${error.message}`, 'error');
    }
  }

  function preview(qid) {
    const current = ++selectionNumber;
    if (form) form.dataset.wikidataLoading = 'true';
    if (form) form.dataset.wikipediaLoading = 'true';
    // 후보 전환은 응답을 기다리지 않고 여기서 끝낸다. 이전 후보의 자동입력·Wikipedia 출처·사진 선택을 정리하고,
    // QID 칸은 새 후보의 기본정보가 확인된 뒤에만 채운다.
    if (selectedQid && selectedQid !== qid) clearPreviousCandidate();
    selectedQid = qid;
    basicState = 'pending';
    currentBasicData = null;
    currentWikipediaData = null;
    const qidField = form?.querySelector('[data-wikidata-qid]');
    if (qidField) qidField.value = '';
    restoredCommonsSelection = null;
    resetCommonsSelection(qid);
    setStage('basic', '기본정보 불러오는 중');
    setStage('wikipedia', 'Wikipedia 다국어 설명 불러오는 중');
    setStage('commons', 'Commons 사진 불러오는 중');
    wikipediaArea.hidden = false;
    commonsArea.hidden = false;
    wikipediaSummary = null;
    wikipediaArea.replaceChildren(element('p', 'admin-field-help', '언어별 Wikipedia 설명을 불러오는 중입니다.'));
    commonsArea.replaceChildren(element('p', 'admin-field-help', '사진과 라이선스 정보를 불러오는 중입니다.'));
    showStatus(`${qid} 후보를 선택했습니다. 기본정보와 설명을 자동입력합니다.`);

    // 기본정보·Wikipedia·Commons는 서로 기다리지 않는 독립 요청이다.
    getCandidateData('preview', qid).then(data => {
      if (current !== selectionNumber) return;
      if (data.qid !== qid) throw new Error('선택한 후보와 다른 Wikidata 응답입니다. 다시 선택해 주세요.');
      renderPreview(data);
      applyBasic(data, current);
      basicState = 'ready';
      currentBasicData = data;
      applyTitleFallback(current);
      showStatus(`${qid} 기본정보가 등록폼에 반영됐습니다. 나머지 자료는 준비되는 대로 표시합니다.`);
    }).catch(error => {
      if (current !== selectionNumber) return;
      basicState = 'failed';
      setStage('basic', `기본정보 조회 실패 · ${error.message}`, 'error');
      showStatus(error.message, true);
      withdrawWikipedia();
    }).finally(() => {
      if (current === selectionNumber && form) delete form.dataset.wikidataLoading;
    });
    void loadWikipedia(qid, current);
    void loadCommons(qid, current);
  }

  form?.addEventListener('input', event => {
    const field = event.target;
    if (field?.name && Object.hasOwn(automaticValues, field.name)
        && field.value !== automaticValues[field.name]) {
      delete automaticValues[field.name];
    }
    // 자동입력 후 관리자가 바꾸거나 지운 칸은 관리자 작성값으로 본다.
    if (field?.dataset?.autofill && !(field.name && Object.hasOwn(automaticValues, field.name))) {
      delete field.dataset.autofill;
      removeConversionNote(field.name);
    }
    for (const item of pendingWikipedia.values()) {
      if (item.field === field) {
        syncWikipediaRevision(item);
        updateWikipediaSourceState(item);
        refreshWikipediaSummary();
      }
    }
  });
  for (const name of ['type', 'season']) {
    form?.elements.namedItem(name)?.addEventListener('change', event => {
      if (event.isTrusted) managerChoices.add(name);
    });
  }
  form?.querySelector('[data-region-field]')?.addEventListener('change', () => {
    automaticRegionId = null;
  });
  form?.querySelector('[data-region-field]')?.addEventListener('click', event => {
    if (event.target.closest('[data-region-mode-button], [data-region-chips]')) automaticRegionId = null;
  });
  // 등록 버튼: 다른 검사로 막히지 않고 실제 제출이 시작된 뒤에만 잠그고 진행 상태를 보여준다.
  // 완료 여부는 서버 응답(목록 이동 또는 오류 화면)으로만 알 수 있으므로 여기서는 성공을 표시하지 않는다.
  const submitStatus = form?.querySelector('[data-registration-status]');
  const submitButtons = () => Array.from(form?.querySelectorAll('[data-registration-submit]') || []);
  function lockRegistration() {
    form.dataset.submitting = 'true';
    for (const button of submitButtons()) {
      button.dataset.idleLabel ??= button.textContent;
      button.disabled = true;
      button.textContent = '등록 중…';
    }
    if (submitStatus) {
      submitStatus.hidden = false;
      submitStatus.classList.remove('is-warning');
      submitStatus.textContent = form.querySelector('[data-wikidata-qid]')?.value
        ? '등록 중입니다. Wikidata 기본정보·Wikipedia 출처와 선택한 Commons 사진을 서버에서 다시 확인해 저장하고 있습니다. 완료되면 여행지 목록으로 이동합니다.'
        : '등록 중입니다. 완료되면 여행지 목록으로 이동합니다.';
    }
  }
  function unlockRegistration() {
    if (!form) return;
    delete form.dataset.submitting;
    for (const button of submitButtons()) {
      button.disabled = false;
      if (button.dataset.idleLabel != null) button.textContent = button.dataset.idleLabel;
    }
    if (submitStatus) submitStatus.hidden = true;
  }
  form?.addEventListener('submit', event => {
    if (form.dataset.submitting === 'true') {
      event.preventDefault();
      return;
    }
    // 같은 제출에 걸린 다른 검사 핸들러가 모두 끝난 뒤, 막히지 않았을 때만 잠근다.
    setTimeout(() => {
      if (!event.defaultPrevented) lockRegistration();
    });
  });
  // 뒤로 가기로 페이지가 복원되면 잠금을 푼다.
  window.addEventListener?.('pageshow', event => {
    if (event.persisted) unlockRegistration();
  });

  form?.addEventListener('submit', event => {
    if (form.dataset.wikidataLoading === 'true' || form.dataset.wikipediaLoading === 'true') {
      event.preventDefault();
      const message = '선택한 후보의 기본정보와 Wikipedia 설명 조회가 끝난 뒤 등록해 주세요.';
      showStatus(message, true);
      showSubmitProblem(message);
    }
  });

  // 제출 전에 막힌 이유는 관리자가 보고 있는 등록 버튼 옆에 보여준다. 버튼은 잠그지 않는다.
  function showSubmitProblem(message) {
    if (!submitStatus) return;
    submitStatus.hidden = false;
    submitStatus.classList.add('is-warning');
    submitStatus.textContent = message;
  }

  function controlLabel(field) {
    const section = field.closest('.type-fields')?.querySelector('h4')?.textContent.trim();
    const panel = field.closest('[data-translation-panel]');
    const language = panel && field.closest('[data-translation-tabs]')
      ?.querySelector(`[data-translation-tab="${panel.dataset.translationPanel}"]`)?.textContent.trim();
    const label = field.closest('label');
    const own = (label && Array.from(label.childNodes)
      .filter(node => node !== field && (node.nodeType === 3 || node.tagName === 'SPAN'))
      .map(node => node.textContent).join(' ').replace(/\s+/g, ' ').trim())
      || field.closest('.admin-form-field')?.querySelector('label')?.textContent.trim()
      || field.getAttribute('aria-label') || '입력칸';
    return [section, language, own].filter(Boolean).join(' · ');
  }

  // 접힌 번역 영역, 다른 언어 탭, 닫힌 상세 보기 안의 칸은 펼쳐서 보이게 한다.
  function revealControl(field) {
    const body = field.closest('[data-translation-tabs-body]');
    if (body?.hidden) body.closest('[data-translation-tabs]')?.querySelector('.admin-translation-collapse-toggle')?.click();
    const panel = field.closest('[data-translation-panel]');
    if (panel?.hidden) {
      panel.closest('[data-translation-tabs]')
        ?.querySelector(`[data-translation-tab="${panel.dataset.translationPanel}"]`)?.click();
    }
    field.closest('details:not([open])')?.setAttribute('open', '');
    return field.getClientRects().length > 0;
  }

  // 등록 버튼을 누르면 브라우저 기본 검사 전에 시즌을 확인한다. 시즌의 빈 선택지는 목록 끝에 있어
  // required 만으로는 브라우저가 막지 않으므로 직접 안내 문구를 건다.
  for (const button of submitButtons()) {
    button.addEventListener('click', () => {
      if (form.dataset.submitting === 'true') return;
      if (submitStatus?.classList.contains('is-warning')) submitStatus.hidden = true;
      const season = form.elements.namedItem('season');
      season?.setCustomValidity?.(season.required && !season.value ? '시즌을 선택해 주세요.' : '');
    });
  }
  form?.elements.namedItem('season')?.addEventListener('change', event => event.target.setCustomValidity?.(''));

  // 브라우저 기본 검사에 걸리면 submit 이벤트 없이 멈춘다. 보이지 않는 칸이면 브라우저가 초점을 옮기지 못해
  // 아무 안내 없이 막히므로, 첫 번째 칸을 펼쳐 보여주고 버튼 옆에 무엇을 고칠지 적는다.
  let invalidReported = false;
  form?.addEventListener('invalid', event => {
    if (invalidReported) return;
    invalidReported = true;
    setTimeout(() => { invalidReported = false; });
    const field = event.target;
    const visible = revealControl(field);
    const reason = field.validationMessage || '입력값을 확인해 주세요.';
    showSubmitProblem(visible
      ? `${controlLabel(field)}: ${reason}`
      : `${controlLabel(field)}: ${reason} 지금 선택한 유형에서는 보이지 않는 칸입니다. 이 값을 입력한 유형으로 바꿔 고치거나 지운 뒤 다시 등록해 주세요.`);
    if (visible) {
      field.scrollIntoView?.({block: 'center'});
      field.focus?.({preventScroll: true});
    }
  }, true);

  // 등록 오류로 폼이 다시 열리면 같은 QID의 Commons 후보만 다시 불러와 남은 선택을 보여준다.
  // 선택값이 후보 목록에 없거나 저장 불가로 바뀌었으면 선택에서 뺀다.
  // 서버가 등록을 거부해 다시 열린 폼이면 위쪽 오류 안내로 초점을 옮기고, 적용했던 후보가 남아 있음을 알린다.
  document.querySelector('[data-registration-error]')?.focus?.();
  if (selectedQid) {
    showStatus(`${selectedQid} 후보를 적용한 입력값과 선택한 사진이 그대로 남아 있습니다. 위쪽 안내를 확인해 고친 뒤 다시 등록해 주세요.`);
    setStage('basic', `기본정보 · ${selectedQid} 적용값 유지`);
    setStage('wikipedia', Array.from(form?.querySelectorAll('[data-wikipedia-revision]') || []).some(field => field.value)
      ? 'Wikipedia 설명 · 적용값 유지' : 'Wikipedia 설명 · 적용 없음');
  }
  if (selectedQid && commonsArea) {
    const stored = storedCommonsSelection();
    restoredCommonsSelection = stored?.qid === selectedQid ? stored : null;
    resetCommonsSelection(selectedQid);
    commonsArea.hidden = false;
    commonsArea.replaceChildren(element('p', 'admin-field-help', '사진과 라이선스 정보를 불러오는 중입니다.'));
    setStage('commons', 'Commons 사진 불러오는 중');
    void loadCommons(selectedQid, selectionNumber);
  }

  searchButton.addEventListener('click', search);
  keyword.addEventListener('keydown', event => {
    if (event.key === 'Enter') {
      event.preventDefault();
      search();
    }
  });
});
