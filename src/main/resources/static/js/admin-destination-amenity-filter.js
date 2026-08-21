/**
 * 관리자 여행지 등록/수정 폼 - 편의시설 필터.
 * 서버가 내려준 유형 태그(data-amenity-types)로 보이기/숨기기만 한다.
 * 체크 상태와 submit 값은 절대 건드리지 않는다.
 */
document.addEventListener("DOMContentLoaded", () => {
    const fields = Array.from(document.querySelectorAll("[data-amenity-field]"));
    if (fields.length === 0) return;

    function tokensOf(value) {
        return String(value || "").trim().split(/\s+/).filter(token => token !== "");
    }

    function applyFilter(field, filter) {
        const wanted = tokensOf(filter);
        const showAll = wanted.length === 0 || wanted.includes("ALL");

        field.querySelectorAll("[data-amenity-option]").forEach(option => {
            const owned = tokensOf(option.dataset.amenityTypes);
            option.hidden = !showAll && !owned.some(type => wanted.includes(type));
        });

        field.querySelectorAll("[data-amenity-filter]").forEach(button => {
            const pressed = button.dataset.amenityFilter === filter;
            button.setAttribute("aria-pressed", String(pressed));
            button.classList.toggle("active", pressed);
        });
    }

    function defaultFilterOf(field) {
        return field.dataset.amenityDefaultFilter || "ALL";
    }

    fields.forEach(field => {
        field.querySelectorAll("[data-amenity-filter]").forEach(button =>
            button.addEventListener("click", () => applyFilter(field, button.dataset.amenityFilter)));
        applyFilter(field, defaultFilterOf(field));
    });

    // 여행지 유형을 바꾸면 각 블록을 자기 유형의 기본 필터로 되돌린다.
    const typeSelect = document.querySelector('select[name="type"]');
    if (typeSelect) {
        typeSelect.addEventListener("change", () =>
            fields.forEach(field => applyFilter(field, defaultFilterOf(field))));
    }
});
