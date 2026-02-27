document.addEventListener("DOMContentLoaded", function() {
    // 타입별 div id 매핑
    const typeFieldsMap = {
        "ATTRACTION": "attraction-fields",
        "ACCOMMODATION": "accommodation-fields",
        "RESTAURANTS": "restaurant-fields",
        "CAFE": "restaurant-fields",     // 🍴 카페도 restaurant-fields 사용!
        "ACTIVITY": "activity-fields",
        "SHOP": "shop-fields",
    };

    const typeSelect = document.querySelector('select[name="type"]');
    const allFields = document.querySelectorAll(".type-fields");

    function showTypeFields() {
        // 모두 숨김
        allFields.forEach(div => div.style.display = "none");
        // 선택한 타입에 맞는 div만 보임
        const selectedType = typeSelect.value;
        const fieldId = typeFieldsMap[selectedType];
        if (fieldId) {
            document.getElementById(fieldId).style.display = "block";
        }
    }

    // 처음 로드 시 한 번 실행 (수정 페이지 대비)
    showTypeFields();

    // 선택 시마다
    typeSelect.addEventListener("change", showTypeFields);
});
