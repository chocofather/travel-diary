(function (root) {
  // 국내(KTO)·해외(Wikidata) 일괄 등록 공통: 현재 페이지(화면에 보이는 후보)의 선택 상태.
  // 선택 목록 자체는 각 화면이 페이지를 넘어 유지하고, 여기서는 현재 페이지 기준 건수와 머리 체크박스 상태만 정한다.

  /**
   * @param items      현재 페이지 후보
   * @param selectable 고를 수 있는 후보인지(등록 완료·잠김 제외)
   * @param isSelected 선택 목록에 들어 있는지
   * @returns {{targets: Array, selectable: number, selected: number, state: 'none'|'some'|'all'}}
   */
  function pageSelection(items, selectable, isSelected) {
    const targets = items.filter(selectable);
    const selected = targets.filter(isSelected).length;
    const state = !selected ? 'none' : selected === targets.length ? 'all' : 'some';
    return {targets, selectable: targets.length, selected, state};
  }

  /** 머리 체크박스·현재 페이지 버튼·건수 문구를 같은 모양으로 반영한다. */
  function applyPageSelection(summary, controls) {
    const {toggle, selectButton, clearButton, count} = controls;
    if (toggle) {
      toggle.checked = summary.state === 'all';
      toggle.indeterminate = summary.state === 'some';
      toggle.disabled = !summary.selectable;
    }
    if (selectButton) selectButton.disabled = summary.state === 'all' || !summary.selectable;
    if (clearButton) clearButton.disabled = !summary.selected;
    if (count) count.textContent = `현재 페이지 선택 가능 ${summary.selectable}건 중 ${summary.selected}건 선택`;
  }

  root.TripBoraBulkPageSelection = {pageSelection, applyPageSelection};
  root.TravelDiaryBulkPageSelection = root.TripBoraBulkPageSelection; // legacy alias (P10a 제거)
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {pageSelection, applyPageSelection};
  }
})(typeof window !== 'undefined' ? window : globalThis);
