document.addEventListener('DOMContentLoaded', () => {
  const root = document.querySelector('[data-wikidata-preview]');
  if (!root) return;

  const keyword = root.querySelector('[data-wikidata-keyword]');
  const searchButton = root.querySelector('[data-wikidata-search]');
  const status = root.querySelector('[data-wikidata-status]');
  const results = root.querySelector('[data-wikidata-results]');
  const detail = root.querySelector('[data-wikidata-detail]');
  const baseUrl = '/admin/api/wikidata/destinations';
  const languages = [
    ['ko', '한국어'], ['en', '영어'], ['ja', '일본어'],
    ['zh-CN', '중국어(간체)'], ['zh-TW', '중국어(번체)']
  ];
  let requestNumber = 0;

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

  async function getJson(url) {
    let response;
    try {
      response = await fetch(url, { headers: { Accept: 'application/json' } });
    } catch (_) {
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

  function renderCandidates(candidates) {
    results.replaceChildren();
    for (const candidate of candidates) {
      const card = element('article', 'admin-wikidata-candidate');
      addImage(card, candidate.imageUrl, candidate.name);
      const text = element('div', 'admin-wikidata-candidate-text');
      text.append(element('strong', '', candidate.name || '명칭 없음'));
      text.append(element('span', 'admin-wikidata-qid', candidate.qid));
      text.append(element('p', '', candidate.shortDescription || '간단 설명 없음'));
      text.append(element('small', '', [candidate.country, candidate.region].filter(Boolean).join(' · ') || '국가·지역 정보 없음'));
      card.append(text);
      const button = element('button', 'admin-btn', '미리보기');
      button.type = 'button';
      button.setAttribute('aria-label', `${candidate.name || candidate.qid} (${candidate.qid}) 미리보기`);
      button.addEventListener('click', () => preview(candidate.qid));
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
    const current = ++requestNumber;
    results.replaceChildren();
    detail.replaceChildren();
    detail.hidden = true;
    showStatus('Wikidata 후보를 검색하는 중입니다.');
    searchButton.disabled = true;
    try {
      const candidates = await getJson(`${baseUrl}/search?keyword=${encodeURIComponent(query)}`);
      if (current !== requestNumber) return;
      if (!candidates.length) {
        showStatus('검색 결과가 없습니다. 다른 한국어 또는 영어 명칭으로 검색해 주세요.');
        return;
      }
      renderCandidates(candidates);
      showStatus(`${candidates.length}개 후보를 찾았습니다. QID와 국가·지역을 확인한 뒤 미리보기를 선택하세요.`);
    } catch (error) {
      if (current === requestNumber) showStatus(error.message, true);
    } finally {
      if (current === requestNumber) searchButton.disabled = false;
    }
  }

  function renderPreview(data) {
    detail.replaceChildren();
    detail.hidden = false;
    detail.append(element('h3', '', `선택한 후보 · ${data.qid}`));
    const grid = element('dl', 'admin-wikidata-detail-grid');
    appendField(grid, 'Wikidata QID', data.qid);
    appendField(grid, '국가', data.country ? `${data.country} (${data.countryQid})` : data.countryQid);
    appendField(grid, '지역', data.regionPath && data.regionPath.length ? data.regionPath.join(' → ') : null);
    const coordinates = data.latitude != null && data.longitude != null
      ? `${data.latitude}, ${data.longitude}` : null;
    appendField(grid, '위도·경도', coordinates);
    detail.append(grid);

    const languageGrid = element('div', 'admin-wikidata-languages');
    for (const [code, languageName] of languages) {
      const card = element('section', 'admin-wikidata-language');
      card.append(element('h4', '', `${languageName} (${code})`));
      const fields = element('dl', 'admin-wikidata-detail-grid');
      appendField(fields, '명칭', data.names && data.names[code]);
      appendField(fields, '간단 설명', data.shortDescriptions && data.shortDescriptions[code]);
      card.append(fields);
      languageGrid.append(card);
    }
    detail.append(languageGrid);

    const imageInfo = element('div', 'admin-wikidata-image-info');
    addImage(imageInfo, data.imageUrl, data.names && (data.names.ko || data.names.en));
    const imageText = element('div');
    imageText.append(element('strong', '', '대표 이미지'));
    imageText.append(element('p', '', data.imageFileName || '제공되지 않음'));
    if (data.imagePageUrl && data.imagePageUrl.startsWith('https://commons.wikimedia.org/')) {
      const link = element('a', '', 'Wikimedia Commons 원본·라이선스 확인');
      link.href = data.imagePageUrl;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      imageText.append(link);
    }
    imageInfo.append(imageText);
    detail.append(imageInfo);

    const match = data.regionMatch;
    const matchMessage = match && match.message ? match.message : '국가·지역을 직접 확인해 주세요.';
    detail.append(element('p', `admin-wikidata-match${match && match.matched ? '' : ' is-review'}`,
      `기존 지역 매핑: ${matchMessage}`));
    detail.append(element('p', 'admin-field-help', '미리보기 전용입니다. 등록폼 값은 변경되지 않으며 여행지와 이미지는 저장되지 않습니다.'));
  }

  async function preview(qid) {
    const current = ++requestNumber;
    detail.replaceChildren();
    detail.hidden = true;
    showStatus(`${qid} 상세 정보를 불러오는 중입니다.`);
    try {
      const data = await getJson(`${baseUrl}/preview?qid=${encodeURIComponent(qid)}`);
      if (current !== requestNumber) return;
      renderPreview(data);
      showStatus(`${qid} 원본 정보를 표시했습니다. 누락된 항목과 지역 매핑을 확인하세요.`);
    } catch (error) {
      if (current === requestNumber) showStatus(error.message, true);
    }
  }

  searchButton.addEventListener('click', search);
  keyword.addEventListener('keydown', event => {
    if (event.key === 'Enter') {
      event.preventDefault();
      search();
    }
  });
});
