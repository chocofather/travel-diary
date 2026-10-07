// 등록폼의 '다른 여행지 확인' 칸. TourAPI·Wikidata 단건 검색이 후보를 고를 때 이벤트로 알려 준다.
// detail 이 '중복 확인' 판별 결과면 확인한 것으로 체크하고 기존 여행지를 보여주며, null 이면 확인을 지운다.
// 서버는 이 칸이 체크된 경우에만 이름·위치가 같은 기존 여행지가 있는 후보를 저장한다.
document.addEventListener('tripbora:possible-duplicate', event => {
  const box = document.querySelector('[data-possible-duplicate-ack]');
  const input = document.querySelector('[data-allow-possible-duplicate]');
  if (!box || !input) return;
  const duplicate = event.detail || null;
  input.checked = Boolean(duplicate);
  box.hidden = !duplicate;
  if (!duplicate) return;
  const label = `#${duplicate.destinationId} ${duplicate.destinationName || ''}`.trim();
  const text = box.querySelector('[data-possible-duplicate-text]');
  if (text) text.textContent = `기존 여행지 ${label}와 다른 여행지임을 확인했습니다.`;
  const link = box.querySelector('[data-possible-duplicate-link]');
  if (link) {
    link.href = `/admin/destinations/edit/${encodeURIComponent(duplicate.destinationId)}`;
    link.textContent = `기존 여행지 #${duplicate.destinationId} 열기`;
    link.hidden = false;
  }
});
