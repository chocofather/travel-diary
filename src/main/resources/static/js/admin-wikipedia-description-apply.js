(function (root) {
  const languages = [
    ['ko', '한국어', 0, 'ko', null],
    ['en', '영어', 1, 'en', null],
    ['ja', '일본어', 2, 'ja', null],
    ['zh-CN', '중국어(간체)', 3, 'zh', 'zh-cn'],
    ['zh-TW', '중국어(번체)', 4, 'zh', 'zh-tw']
  ];

  function planWikipediaDescriptions(preview, currentValues) {
    const changes = [];
    const preserved = [];
    for (const [code, label, index, sourceLanguage, variant] of languages) {
      const entry = preview.languages?.find(item => item.language === code);
      const name = `translations[${index}].description`;
      if (!Object.hasOwn(currentValues, name) || entry?.status !== 'AVAILABLE'
        || entry.sourceLanguage !== sourceLanguage || (entry.variant || null) !== variant
        || !Number.isSafeInteger(entry.revisionId) || entry.revisionId <= 0
        || !String(entry.licenseName ?? '').trim()
        || !String(entry.licenseUrl ?? '').startsWith('https://creativecommons.org/')
        || !String(entry.description ?? '').trim()) continue;
      const change = {
        name, code, label, value: entry.description, qid: preview.qid,
        title: entry.title, sourceUrl: entry.sourceUrl, revisionId: entry.revisionId,
        sourceLanguage: entry.sourceLanguage, variant: entry.variant,
        licenseName: entry.licenseName, licenseUrl: entry.licenseUrl
      };
      if (String(currentValues[name] ?? '').trim()) preserved.push(change);
      else changes.push(change);
    }
    return {changes, preserved};
  }

  // Wikidata에 이 언어 제목이 없을 때만 Wikipedia가 해당 변형으로 변환해 준 문서 제목(displayTitle)을 쓴다.
  // 현재는 번체(zh-TW)만 대상이다. 변환 제목이 없으면 간체 등 다른 표기를 대신 넣지 않는다.
  const titleFallbackLanguages = [['zh-TW', '중국어(번체)', 4, 'zh', 'zh-tw']];

  function planWikipediaTitleFallback(preview, wikidataNames, currentValues) {
    const changes = [];
    for (const [code, label, index, sourceLanguage, variant] of titleFallbackLanguages) {
      const name = `translations[${index}].name`;
      const entry = preview?.languages?.find(item => item.language === code);
      const title = String(entry?.displayTitle ?? '').trim();
      if (!Object.hasOwn(currentValues, name) || String(currentValues[name] ?? '').trim()
        || String(wikidataNames?.[code] ?? '').trim()
        || entry?.status !== 'AVAILABLE' || entry.sourceLanguage !== sourceLanguage
        || (entry.variant || null) !== variant || !title
        // 동음이의 구분용 괄호가 붙은 문서 제목은 여행지명으로 그대로 쓰지 않는다.
        || /[（(][^（()）]*[)）]$/.test(title)
        || [...title].length > 255) continue;
      changes.push({name, code, label: `${label} 여행지명`, value: title});
    }
    return changes;
  }

  function stagePreviewDescription(field, change) {
    if (!field || field.getAttribute('name') !== change.name || String(field.value ?? '').trim()) {
      return false;
    }
    field.value = change.value;
    field.setAttribute('data-wikipedia-original-name', change.name);
    return true;
  }

  function discardPreviewDescription(field) {
    const name = field?.getAttribute('data-wikipedia-original-name');
    if (!name) return false;
    field.value = '';
    field.removeAttribute('data-wikipedia-original-name');
    return true;
  }

  function summarizeWikipediaApplication(appliedCodes) {
    const applied = languages.filter(([code]) => appliedCodes.includes(code))
      .map(([code, label]) => `${label} (${code})`);
    return applied.length
      ? `Wikipedia 상세 설명 자동입력됨 · ${applied.length}개 언어: ${applied.join(', ')}`
      : 'Wikipedia 상세 설명 자동입력 없음 · 출처 상세 보기에서 언어별 사유를 확인하세요.';
  }

  root.TripBoraWikipediaDescriptionPlanner = {
    planWikipediaDescriptions, planWikipediaTitleFallback, stagePreviewDescription, discardPreviewDescription,
    summarizeWikipediaApplication
  };
  root.TravelDiaryWikipediaDescriptionPlanner = root.TripBoraWikipediaDescriptionPlanner; // legacy alias (P10a 제거)
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {planWikipediaDescriptions, planWikipediaTitleFallback, stagePreviewDescription,
      discardPreviewDescription, summarizeWikipediaApplication};
  }
})(typeof window !== 'undefined' ? window : globalThis);
