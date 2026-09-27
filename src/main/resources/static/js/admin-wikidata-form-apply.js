(function (root) {
  const languages = [
    ['ko', '한국어', 0], ['en', '영어', 1], ['ja', '일본어', 2],
    ['zh-CN', '중국어(간체)', 3], ['zh-TW', '중국어(번체)', 4]
  ];

  const chineseScriptLabels = {'zh-CN': '간체', 'zh-TW': '번체'};
  const chineseSourceLabels = {
    'zh-hans': '간체(zh-hans)', 'zh-cn': '간체(zh-cn)', 'zh-tw': '번체(zh-tw)', 'zh-hant': '번체(zh-hant)',
    zh: '중국어(zh)'
  };

  function chineseConversionNote(code, from) {
    return `중국어 표기 자동 변환 · Wikidata ${chineseSourceLabels[from] || from} 설명을 Wikipedia 변환기로 `
      + `${chineseScriptLabels[code]}로 바꾼 문장입니다. 원문이 아니니 확인해 주세요.`;
  }

  function planAutofill(preview, currentValues, regionOccupied) {
    const changes = [];
    const preserved = [];

    function propose(name, label, sourceValue, extra = {}) {
      if (!Object.hasOwn(currentValues, name) || sourceValue == null) return;
      const value = String(sourceValue).trim();
      if (!value) return;
      const field = {name, label, value, ...extra};
      if (String(currentValues[name] ?? '').trim()) {
        if (String(currentValues[name]).trim() !== value) preserved.push(field);
      } else {
        changes.push(field);
      }
    }

    for (const [code, label, index] of languages) {
      propose(`translations[${index}].name`, `${label} 여행지명`, preview.names?.[code]);
      // 반대 표기 원문을 Wikipedia 변환기로 바꾼 간단 설명은 원문과 구분해 표시한다.
      const conversionFrom = preview.shortDescriptionConversions?.[code];
      propose(`translations[${index}].shortDescription`, `${label} 간단 설명`,
        preview.shortDescriptions?.[code],
        conversionFrom ? {source: 'chinese-conversion', note: chineseConversionNote(code, conversionFrom)} : {});
    }
    if (Number.isFinite(preview.latitude) && preview.latitude >= -90 && preview.latitude <= 90) {
      propose('latitude', '위도', preview.latitude);
    }
    if (Number.isFinite(preview.longitude) && preview.longitude >= -180 && preview.longitude <= 180) {
      propose('longitude', '경도', preview.longitude);
    }

    const matchedPath = preview.regionMatch?.matched && Array.isArray(preview.regionMatch.path)
      && preview.regionMatch.path.length === 3
      && preview.regionMatch.path.every(item => Number.isInteger(item.id) && item.id > 0
        && typeof item.regionName === 'string' && item.regionName.trim())
      && preview.regionMatch.path[1].id === preview.regionMatch.countryId
      && preview.regionMatch.path[2].id === preview.regionMatch.regionId;
    const regionStatus = regionOccupied ? 'existing' : matchedPath ? 'matched' : 'manual';
    return {
      changes,
      preserved,
      regionPath: regionStatus === 'matched' ? preview.regionMatch.path : null,
      regionStatus,
      travelChanges: planTravelInfo(preview.travelInfo, currentValues)
    };
  }

  // 여행 상세정보 중 Wikidata 구조화 값으로 확인되는 공식 웹사이트·전화번호만 제안한다.
  // 유형을 아직 고르지 않았을 수 있어 모든 유형의 칸에 제안하고, 저장은 선택한 유형의 칸만 된다.
  // [칸 이름, 전화번호 칸 길이(DB)] — 숙소·음식점 전화번호는 32자, 나머지는 255자다. 홈페이지는 모두 255자다.
  const travelInfoSections = [
    ['attractionInfo', 255], ['accommodationInfo', 32], ['restaurantInfo', 32],
    ['activityInfo', 255], ['shopInfo', 255]
  ];

  function planTravelInfo(travelInfo, currentValues) {
    const changes = [];
    const homepage = String(travelInfo?.homepageUrl ?? '').trim();
    const contact = String(travelInfo?.contactNumber ?? '').trim();
    for (const [section, contactLimit] of travelInfoSections) {
      for (const [field, label, value, limit] of [
        ['homepageUrl', '홈페이지', homepage, 255], ['contactNumber', '전화번호', contact, contactLimit]]) {
        const name = `${section}.${field}`;
        if (!value || value.length > limit || !Object.hasOwn(currentValues, name)
          || String(currentValues[name] ?? '').trim()) continue;
        changes.push({name, label, value, kind: field});
      }
    }
    return changes;
  }

  function automaticFieldsToClear(currentValues, automaticValues = {}) {
    return Object.entries(automaticValues)
      .filter(([name, value]) => Object.hasOwn(currentValues, name) && currentValues[name] === value)
      .map(([name]) => name);
  }

  function manualConflicts(currentValues, automaticValues = {}) {
    return Object.entries(currentValues)
      .filter(([name, value]) => /^(?:translations\[[0-4]\]\.(?:name|shortDescription|description)|latitude|longitude)$/.test(name)
        && String(value ?? '').trim()
        && !(Object.hasOwn(automaticValues, name) && value === automaticValues[name]))
      .map(([name]) => ({name}));
  }

  function stillEmptyChanges(changes, currentValues) {
    return changes.filter(change => Object.hasOwn(currentValues, change.name)
      && !String(currentValues[change.name] ?? '').trim());
  }

  // 먼저 보여준 검색 목록(최소 정보)에 국가·지역·이미지 상세를 합친다.
  // 상세 응답에서 빠진 후보는 지리 정보가 없는 항목이라 목록에서 뺀다. 상세 값이 비어 있으면 목록 값을 유지한다.
  function mergeSearchDetails(quickCandidates, detailedCandidates) {
    const details = new Map((detailedCandidates || []).map(item => [item.qid, item]));
    return (quickCandidates || []).filter(item => details.has(item.qid)).map(item => {
      const merged = {...item};
      for (const [key, value] of Object.entries(details.get(item.qid))) {
        if (value != null && value !== '') merged[key] = value;
      }
      return merged;
    });
  }

  root.TravelDiaryWikidataApplyPlanner = {planAutofill, planTravelInfo, stillEmptyChanges, manualConflicts,
    automaticFieldsToClear, mergeSearchDetails};
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {planAutofill, planTravelInfo, stillEmptyChanges, manualConflicts, automaticFieldsToClear,
      mergeSearchDetails};
  }
})(typeof window !== 'undefined' ? window : globalThis);
