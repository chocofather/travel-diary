document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('admin-content-filter-form');
    const typeSelect = form?.querySelector('select[name="targetType"]');
    if (!form || !typeSelect) return;

    // 콘텐츠 유형은 선택 즉시 적용한다.
    // 같은 폼을 그대로 전송하므로 입력해 둔 검색어도 함께 유지된다.
    typeSelect.addEventListener('change', () => form.submit());
});
