(function (root) {
  // 여행지 JSON 일괄 등록: 붙여넣기·파일 선택 → 서버 미리보기 → 행 선택(중복 확인은 행별 승인) → 한 건씩 등록.
  // 검증·매핑·중복 판별은 모두 서버가 한다. 등록 요청도 서버가 그 항목을 처음부터 다시 검증하므로
  // 이 화면은 미리보기 결과를 보여주고, 고른 항목의 원본 JSON 을 그대로 보내는 일만 한다.

  const MAX_BYTES = 1048576;
  const STATUS = {
    NOT_REGISTERED: {label: '등록 가능', className: 'is-new'},
    POSSIBLE_DUPLICATE: {label: '중복 확인', className: 'is-review'},
    REGISTERED: {label: '등록됨', className: 'is-registered'},
    INVALID: {label: '오류', className: 'is-invalid'}
  };
  const RESULT = {
    RUNNING: {label: '등록 중…', className: 'is-running'},
    SUCCESS: {label: '등록 완료', className: 'is-success'},
    REGISTERED: {label: '이미 등록됨', className: 'is-registered'},
    POSSIBLE_DUPLICATE: {label: '중복 확인 필요', className: 'is-review'},
    INVALID: {label: '검증 오류', className: 'is-failed'},
    FAILED: {label: '등록 실패', className: 'is-failed'}
  };
  const TYPE_LABELS = {
    ATTRACTION: '관광지', ACCOMMODATION: '숙소', RESTAURANTS: '음식점',
    CAFE: '카페', SHOP: '쇼핑', ACTIVITY: '체험/액티비티'
  };
  const SEASON_LABELS = {SPRING: '봄', SUMMER: '여름', FALL: '가을', WINTER: '겨울', ALL_SEASONS: '사계절'};

  /** 이 행을 지금 고를 수 있는지. 등록됨·오류·등록 완료는 고를 수 없고, 중복 확인은 승인해야 고를 수 있다. */
  function selectable(row) {
    if (row.result && ['SUCCESS', 'REGISTERED', 'RUNNING'].includes(row.result.status)) return false;
    if (row.preview.status === 'NOT_REGISTERED') return true;
    if (row.preview.status === 'POSSIBLE_DUPLICATE') return row.approved;
    return false;
  }

  function issueText(issue) {
    return issue.path ? `${issue.path}: ${issue.message}` : issue.message;
  }

  function sourceLabel(preview) {
    if (preview.sourceType === 'KTO_TOURAPI') return `TourAPI ${preview.externalId}`;
    if (preview.sourceType === 'WIKIDATA') return `Wikidata ${preview.externalId}`;
    return '직접 입력';
  }

  function utf8Bytes(text) {
    return typeof TextEncoder !== 'undefined' ? new TextEncoder().encode(text).length : text.length;
  }

  const api = {selectable, issueText, sourceLabel, utf8Bytes, STATUS, RESULT};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.TripBoraDestinationJsonImport = api;

  if (typeof document === 'undefined') return;

  document.addEventListener('DOMContentLoaded', () => {
    const page = document.querySelector('[data-json-import]');
    if (!page) return;
    const $ = selector => page.querySelector(selector);
    const textArea = $('[data-json-import-text]');
    const fileInput = $('[data-json-import-file]');
    const fileName = $('[data-json-import-file-name]');
    const status = $('[data-json-import-status]');
    const previewButton = $('[data-json-import-preview]');
    const fileErrors = $('[data-json-import-file-errors]');
    const result = $('[data-json-import-result]');
    const rowsBody = $('[data-json-import-rows]');
    const empty = $('[data-json-import-empty]');
    const progress = $('[data-json-import-progress]');
    const selectedCount = $('[data-json-import-selected-count]');
    const registerButton = $('[data-json-import-register]');

    /** 미리보기 때의 원본 문서. 등록은 이 문서의 destinations[index] 를 그대로 보낸다. */
    let documentSnapshot = null;
    let snapshotText = null;
    let rows = [];
    let filter = 'ALL';
    let running = false;

    function element(tag, className, text) {
      const node = document.createElement(tag);
      if (className) node.className = className;
      if (text != null) node.textContent = text;
      return node;
    }

    function setStatus(message, isError) {
      status.textContent = message;
      status.classList.toggle('is-error', Boolean(isError));
    }

    function csrfHeaders() {
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      const header = document.querySelector('meta[name="_csrf_header"]')?.content;
      return token && header ? {[header]: token} : {};
    }

    async function postJson(url, body) {
      let response;
      try {
        response = await fetch(url, {
          method: 'POST',
          headers: {Accept: 'application/json', 'Content-Type': 'application/json', ...csrfHeaders()},
          body
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

    function renderIssues(list, items, className) {
      list.replaceChildren(...items.map(issue => element('li', className, issueText(issue))));
      list.hidden = items.length === 0;
    }

    function existingLink(destinationId, label) {
      const link = element('a', 'admin-kto-import-existing', label || `기존 여행지 #${destinationId} 열기`);
      link.href = `/admin/destinations/edit/${encodeURIComponent(destinationId)}`;
      link.target = '_blank';
      link.rel = 'noopener';
      return link;
    }

    function badge(info) {
      return element('span', `admin-kto-import-badge ${info.className}`, info.label);
    }

    function statusCell(row) {
      const cell = element('td', 'admin-json-import-state');
      const preview = row.preview;
      cell.append(badge(STATUS[preview.status]));
      const duplicate = preview.duplicate;
      if (duplicate && duplicate.status !== 'NOT_REGISTERED' && duplicate.destinationId != null) {
        cell.append(element('span', 'admin-kto-import-reason',
          `#${duplicate.destinationId} ${duplicate.destinationName || ''} · ${duplicate.message || ''}`));
        cell.append(existingLink(duplicate.destinationId));
      }
      if (preview.fileDuplicate) {
        cell.append(element('span', 'admin-kto-import-reason', preview.fileDuplicate.message));
      }
      if (preview.status === 'POSSIBLE_DUPLICATE' && !(row.result && row.result.status === 'SUCCESS')) {
        const label = element('label', 'admin-json-import-approve');
        const input = element('input');
        input.type = 'checkbox';
        input.checked = row.approved;
        input.disabled = running;
        input.addEventListener('change', () => {
          row.approved = input.checked;
          if (!row.approved) row.selected = false;
          render();
        });
        label.append(input, document.createTextNode(' 다른 여행지임을 확인'));
        cell.append(label);
      }
      if (row.result) {
        const info = RESULT[row.result.status] || RESULT.FAILED;
        const resultLine = element('div', `admin-json-import-outcome ${info.className}`);
        resultLine.append(element('strong', null, info.label));
        if (row.result.status === 'SUCCESS' && row.result.destinationId != null) {
          resultLine.append(existingLink(row.result.destinationId, '등록한 여행지 수정'));
        } else if (row.result.status === 'REGISTERED' && row.result.destinationId != null) {
          resultLine.append(existingLink(row.result.destinationId));
        } else if (row.result.status === 'POSSIBLE_DUPLICATE' && row.result.duplicate) {
          resultLine.append(element('span', 'admin-kto-import-reason', row.result.duplicate.message || ''));
          if (row.result.destinationId != null) resultLine.append(existingLink(row.result.destinationId));
        } else if (row.result.message && row.result.status !== 'RUNNING') {
          resultLine.append(element('span', 'admin-kto-import-reason', row.result.message));
        }
        if (row.result.errors && row.result.errors.length) {
          const list = element('ul', 'admin-json-import-issues is-error');
          renderIssues(list, row.result.errors, null);
          resultLine.append(list);
        }
        cell.append(resultLine);
      }
      return cell;
    }

    function reviewCell(preview) {
      const cell = element('td', 'admin-json-import-review');
      if (preview.errors.length) {
        const list = element('ul', 'admin-json-import-issues is-error');
        renderIssues(list, preview.errors, null);
        cell.append(list);
      }
      if (preview.warnings.length) {
        const list = element('ul', 'admin-json-import-issues is-warning');
        renderIssues(list, preview.warnings, null);
        cell.append(list);
      }
      if (preview.factFields.length || preview.evidence.length) {
        const details = element('details', 'admin-json-import-evidence');
        details.append(element('summary', null,
          `사실정보 ${preview.factFields.length}개 · 근거 ${preview.evidence.length}건`));
        if (preview.factFields.length) {
          details.append(element('p', null, `값이 있는 사실정보: ${preview.factFields.join(', ')}`));
        }
        const list = element('ul');
        for (const evidence of preview.evidence) {
          const item = element('li');
          if (evidence.url && /^https?:\/\//i.test(evidence.url)) {
            const link = element('a', null, evidence.url);
            link.href = evidence.url;
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            item.append(link);
          } else {
            item.append(element('span', null, evidence.url || '(주소 없음)'));
          }
          if (evidence.fields && evidence.fields.length) item.append(element('small', null, ` · ${evidence.fields.join(', ')}`));
          list.append(item);
        }
        if (preview.evidence.length) details.append(list);
        cell.append(details);
      }
      if (!cell.childNodes.length) cell.append(element('span', 'admin-kto-import-reason', '문제 없음'));
      return cell;
    }

    function rowElement(row) {
      const preview = row.preview;
      const tr = element('tr', `is-${preview.status.toLowerCase().replace(/_/g, '-')}`);
      if (preview.status === 'REGISTERED') tr.classList.add('is-registered');
      if (preview.status === 'POSSIBLE_DUPLICATE') tr.classList.add('is-review');

      const checkCell = element('td', 'is-check');
      const check = element('input');
      check.type = 'checkbox';
      check.setAttribute('aria-label', `${preview.name || preview.path} 선택`);
      check.checked = row.selected && selectable(row);
      check.disabled = running || !selectable(row);
      check.addEventListener('change', () => { row.selected = check.checked; renderCounts(); });
      checkCell.append(check);

      const nameCell = element('td', 'admin-json-import-name');
      nameCell.append(element('strong', null, preview.name || '(한국어 이름 없음)'));
      nameCell.append(element('span', 'admin-kto-import-reason',
        `#${preview.index + 1}${preview.key ? ` · ${preview.key}` : ''} · ${sourceLabel(preview)}`));

      const typeCell = element('td', null,
        [TYPE_LABELS[preview.type] || preview.type || '-', SEASON_LABELS[preview.season]].filter(Boolean).join(' · '));
      const categoryCell = element('td');
      categoryCell.append(element('span', null, preview.categories.length ? preview.categories.join(', ') : '-'));
      if (preview.mainCategory) categoryCell.append(element('span', 'admin-kto-import-reason', `대표: ${preview.mainCategory}`));

      tr.append(checkCell, nameCell, element('td', null, preview.regionLabel || '-'), typeCell, categoryCell,
        statusCell(row), reviewCell(preview));
      return tr;
    }

    function renderCounts() {
      const picked = rows.filter(row => row.selected && selectable(row)).length;
      selectedCount.textContent = `선택 ${picked}건`;
      registerButton.disabled = running || picked === 0 || textArea.value !== snapshotText;
    }

    function render() {
      const visible = rows.filter(row => filter === 'ALL' || row.preview.status === filter);
      rowsBody.replaceChildren(...visible.map(rowElement));
      empty.hidden = visible.length > 0;
      page.querySelectorAll('[data-json-import-filter]').forEach(button => {
        const active = button.dataset.jsonImportFilter === filter;
        button.classList.toggle('active', active);
        button.setAttribute('aria-pressed', String(active));
      });
      renderCounts();
    }

    function renderSummary(previewResult) {
      const counts = {
        ALL: previewResult.total,
        NOT_REGISTERED: previewResult.summary.registrable,
        POSSIBLE_DUPLICATE: previewResult.summary.possibleDuplicate,
        REGISTERED: previewResult.summary.registered,
        INVALID: previewResult.summary.invalid
      };
      for (const [key, value] of Object.entries(counts)) {
        page.querySelectorAll(`[data-json-import-summary-count="${key}"], [data-json-import-count="${key}"]`)
          .forEach(node => { node.textContent = String(value); });
      }
    }

    async function runPreview() {
      const text = textArea.value;
      if (!text.trim()) {
        setStatus('JSON을 붙여넣거나 파일을 선택해 주세요.', true);
        return;
      }
      if (utf8Bytes(text) > MAX_BYTES) {
        setStatus('JSON은 최대 1MB까지 받을 수 있습니다.', true);
        return;
      }
      previewButton.disabled = true;
      setStatus('검증하는 중입니다… TourAPI contentId 가 있으면 확인하느라 조금 걸릴 수 있습니다.', false);
      try {
        const previewResult = await postJson('/admin/api/destinations/import-json/preview', text);
        if (previewResult.fileErrors.length) {
          renderIssues(fileErrors, previewResult.fileErrors, null);
          result.hidden = true;
          documentSnapshot = null;
          snapshotText = null;
          rows = [];
          setStatus('파일을 읽지 못했습니다. 아래 오류를 고친 뒤 다시 미리보기 해 주세요.', true);
          return;
        }
        renderIssues(fileErrors, [], null);
        documentSnapshot = JSON.parse(text);
        snapshotText = text;
        rows = previewResult.rows.map(preview => ({preview, selected: false, approved: false, result: null}));
        filter = 'ALL';
        renderSummary(previewResult);
        result.hidden = false;
        progress.textContent = '등록 가능한 여행지를 고르세요. \'중복 확인\' 여행지는 행마다 다른 여행지임을 확인해야 고를 수 있습니다.';
        setStatus(`미리보기를 만들었습니다. 총 ${previewResult.total}건`, false);
        render();
      } catch (error) {
        setStatus(error.message, true);
      } finally {
        previewButton.disabled = false;
      }
    }

    async function registerSelected() {
      if (textArea.value !== snapshotText) {
        progress.textContent = 'JSON을 수정했습니다. 다시 미리보기 한 뒤 등록해 주세요.';
        return;
      }
      const targets = rows.filter(row => row.selected && selectable(row));
      if (!targets.length || !documentSnapshot) return;
      running = true;
      previewButton.disabled = true;
      let done = 0;
      const tally = {SUCCESS: 0, REGISTERED: 0, POSSIBLE_DUPLICATE: 0, INVALID: 0, FAILED: 0};
      for (const row of targets) {
        row.result = {status: 'RUNNING'};
        progress.textContent = `${targets.length}건 중 ${done + 1}번째 등록 중… (${row.preview.name || row.preview.path})`;
        render();
        try {
          row.result = await postJson('/admin/api/destinations/import-json/register', JSON.stringify({
            index: row.preview.index,
            allowPossibleDuplicate: row.preview.status === 'POSSIBLE_DUPLICATE' && row.approved,
            item: documentSnapshot.destinations[row.preview.index]
          }));
        } catch (error) {
          row.result = {status: 'FAILED', message: error.message};
        }
        tally[row.result.status] = (tally[row.result.status] || 0) + 1;
        if (row.result.status === 'SUCCESS' || row.result.status === 'REGISTERED') row.selected = false;
        done++;
      }
      running = false;
      previewButton.disabled = false;
      progress.textContent = `등록을 마쳤습니다. 성공 ${tally.SUCCESS}건 · 이미 등록됨 ${tally.REGISTERED}건 · `
        + `중복 확인 필요 ${tally.POSSIBLE_DUPLICATE}건 · 검증 오류 ${tally.INVALID}건 · 실패 ${tally.FAILED}건`
        + (tally.FAILED ? '. 실패한 여행지는 선택된 채로 남아 있어 다시 등록할 수 있습니다.' : '');
      render();
    }

    fileInput.addEventListener('change', async () => {
      const file = fileInput.files && fileInput.files[0];
      if (!file) return;
      if (file.size > MAX_BYTES) {
        setStatus('JSON 파일은 최대 1MB까지 선택할 수 있습니다.', true);
        fileInput.value = '';
        return;
      }
      try {
        textArea.value = await file.text();
        fileName.textContent = file.name;
        setStatus('파일 내용을 불러왔습니다. 미리보기를 눌러 검증하세요.', false);
        renderCounts();
      } catch (_) {
        setStatus('파일을 읽지 못했습니다.', true);
      } finally {
        fileInput.value = '';
      }
    });
    textArea.addEventListener('input', () => {
      if (snapshotText != null && textArea.value !== snapshotText) {
        progress.textContent = 'JSON을 수정했습니다. 다시 미리보기 해야 등록할 수 있습니다.';
      }
      renderCounts();
    });
    previewButton.addEventListener('click', runPreview);
    page.querySelectorAll('[data-json-import-filter]').forEach(button => {
      button.addEventListener('click', () => { filter = button.dataset.jsonImportFilter; render(); });
    });
    $('[data-json-import-select-all]').addEventListener('click', () => {
      for (const row of rows) {
        if (row.preview.status === 'NOT_REGISTERED' && selectable(row)) row.selected = true;
      }
      render();
    });
    $('[data-json-import-clear]').addEventListener('click', () => {
      for (const row of rows) row.selected = false;
      render();
    });
    registerButton.addEventListener('click', registerSelected);
    window.addEventListener('beforeunload', event => {
      if (running) event.preventDefault();
    });
  });
})(typeof window !== 'undefined' ? window : globalThis);
