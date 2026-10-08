// 기존 Wikidata 여행지의 이미지 관리 화면에서 Commons 사진을 골라 한 번에 추가한다.
// 후보 표시는 등록폼과 같은 카드(TripBoraCommonsPhotoPicker)를 쓰고, 서버로는 QID·파일명·대표 여부만 보낸다.
document.addEventListener('DOMContentLoaded', () => {
  const root = document.querySelector('[data-commons-add]');
  const picker = window.TripBoraCommonsPhotoPicker || window.TravelDiaryCommonsPhotoPicker; // legacy fallback (P10a 제거)
  if (!root || !picker) return;

  const qid = root.dataset.qid;
  const loadButton = root.querySelector('[data-commons-add-load]');
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

  function showMessage(text, isError = false) {
    message.hidden = !text;
    message.textContent = text || '';
    message.classList.toggle('is-warning', isError);
  }

  function updateSelection(next) {
    photos = next;
    selectionField.value = picker.serializeSelection(qid, photos);
    submitButton.disabled = form.dataset.submitting === 'true' || photos.length === 0;
  }

  // 같은 위치(cursor)의 묶음은 한 번만 받는다. 실패한 요청은 남기지 않아 다시 시도할 수 있다.
  const pages = new Map();

  function fetchPage(cursor = null) {
    const key = cursor || '';
    if (pages.has(key)) return pages.get(key);
    const request = (async () => {
      const response = await fetch(`/admin/api/wikidata/destinations/commons-photos?qid=${encodeURIComponent(qid)}`
        + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''), {headers: {Accept: 'application/json'}});
      if (response.redirected && new URL(response.url).pathname.startsWith('/login')) {
        throw new Error('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.');
      }
      const data = await response.json().catch(() => null);
      if (!response.ok || !data) throw new Error(data?.message || 'Commons 사진 후보를 불러오지 못했습니다.');
      if (data.qid !== qid) throw new Error('이 여행지의 QID와 다른 응답입니다. 다시 불러와 주세요.');
      return data;
    })();
    pages.set(key, request);
    request.catch(() => pages.delete(key));
    return request;
  }

  async function loadCandidates() {
    loadButton.disabled = true;
    showMessage('사진과 라이선스 정보를 불러오는 중입니다.');
    // '후보 다시 불러오기'는 처음부터 새로 받는다.
    pages.clear();
    try {
      const data = await fetchPage();
      updateSelection([]);
      picker.render(candidates, data, {
        mainMode: 'explicit',
        registeredFileNames,
        heading: `Commons 사진 후보 · ${qid}`,
        intro: '추가할 사진을 고르세요. 이미 이 여행지에 있는 사진은 다시 추가할 수 없습니다. 대표로 지정하지 않으면 현재 대표 사진을 그대로 둡니다.',
        selectedLabel: '추가할 사진',
        emptySelectionText: '선택한 사진 없음',
        actionLabel: '추가할 사진으로 선택',
        loadMore: cursor => fetchPage(cursor),
        onChange: updateSelection
      });
      showMessage('');
      loadButton.textContent = '후보 다시 불러오기';
    } catch (error) {
      showMessage(error.message, true);
    } finally {
      loadButton.disabled = false;
    }
  }

  loadButton.addEventListener('click', () => void loadCandidates());

  form.addEventListener('submit', event => {
    if (form.dataset.submitting === 'true' || photos.length === 0) {
      event.preventDefault();
      return;
    }
    // 빠른 두 번 클릭이 두 번 제출되지 않도록 첫 제출 즉시 잠근다. 결과는 서버가 돌려준 관리 화면에서 안내한다.
    form.dataset.submitting = 'true';
    submitButton.disabled = true;
    loadButton.disabled = true;
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
    loadButton.disabled = false;
    submitStatus.hidden = true;
  }

  // 오류 화면에서 뒤로 돌아오면(페이지 캐시) 다시 추가할 수 있게 풀어 준다.
  window.addEventListener('pageshow', event => {
    if (event.persisted && form.dataset.submitting === 'true') unlock();
  });
});
