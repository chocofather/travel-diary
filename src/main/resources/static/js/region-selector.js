document.addEventListener("DOMContentLoaded", function () {
    const continentSelect = document.getElementById("continent");
    const countrySelect = document.getElementById("country");
    const citySelect = document.getElementById("city");
    const districtSelect = document.getElementById("district");
    const regionIdHidden = document.getElementById("regionIdHidden");  // 선언 추가

    function fetchRegions(parentId, targetSelect) {
        // 기본 선택 옵션 유지
        targetSelect.innerHTML = '<option value="">선택</option>';

        let url = "/api/regions";
        if (parentId) url += `?parentId=${parentId}`;

        fetch(url)
            .then(res => res.json())
            .then(data => {
                if (data.length === 0) return;
                data.forEach(region => {
                    const option = document.createElement("option");
                    option.value = region.id;
                    option.textContent = region.regionName;
                    targetSelect.appendChild(option);
                });
            })
            .catch(err => console.error("API 오류:", err));
    }

    function updateRegionId() {
        // 선택 가능한 셀렉트 순서대로 depth4 → depth3 → depth2 → depth1
        const selected =
            (districtSelect && districtSelect.value) ||
            (citySelect && citySelect.value) ||
            (countrySelect && countrySelect.value) ||
            (continentSelect && continentSelect.value) ||
            "";
        regionIdHidden.value = selected;
        // console.log("최종 regionId:", selected);
    }

    continentSelect.addEventListener("change", () => {
        fetchRegions(continentSelect.value, countrySelect);
        citySelect.innerHTML = '<option value="">선택</option>';
        districtSelect.innerHTML = '<option value="">선택</option>';
        updateRegionId();
    });

    countrySelect.addEventListener("change", () => {
        fetchRegions(countrySelect.value, citySelect);
        districtSelect.innerHTML = '<option value="">선택</option>';
        updateRegionId();
    });

    citySelect.addEventListener("change", () => {
        fetchRegions(citySelect.value, districtSelect);
        updateRegionId();
    });

    districtSelect.addEventListener("change", updateRegionId);

    fetchRegions(undefined, continentSelect);

    document.querySelector("form").addEventListener("submit", function() {
        updateRegionId();
    });
});
