document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('travel-info-form');
    if (!form) return;

    window.initQuillEditor(
        '#travel-info-editor',
        'travel-info-content',
        'travel-info-form',
        'travel-info-initial-content'
    );

    // 언어별 본문 편집기는 공통 스크립트(/js/admin-translation-editors.js)가 맡는다.

    const thumbnailPreview = document.getElementById('travel-info-thumbnail-preview');
    const thumbnailPreviewImage = document.getElementById('travel-info-thumbnail-preview-image');
    const thumbnailEmpty = document.getElementById('travel-info-thumbnail-empty');
    const thumbnailFile = document.getElementById('travel-info-thumbnail-file');
    const removeThumbnail = document.getElementById('travel-info-remove-thumbnail');

    if (thumbnailPreview && thumbnailPreviewImage && thumbnailEmpty && thumbnailFile) {
        const currentThumbnailUrl = thumbnailPreview.dataset.currentThumbnailUrl || '';
        let objectUrl = null;

        function releaseObjectUrl() {
            if (!objectUrl) return;
            URL.revokeObjectURL(objectUrl);
            objectUrl = null;
        }

        function showThumbnail(url) {
            thumbnailPreviewImage.src = url;
            thumbnailPreviewImage.hidden = false;
            thumbnailEmpty.hidden = true;
        }

        function showEmptyThumbnail() {
            thumbnailPreviewImage.hidden = true;
            thumbnailPreviewImage.removeAttribute('src');
            thumbnailEmpty.hidden = false;
        }

        function restoreCurrentThumbnail() {
            if (removeThumbnail?.checked || !currentThumbnailUrl) {
                showEmptyThumbnail();
                return;
            }
            showThumbnail(currentThumbnailUrl);
        }

        thumbnailFile.addEventListener('change', () => {
            releaseObjectUrl();
            const selectedFile = thumbnailFile.files?.[0];
            if (!selectedFile) {
                if (removeThumbnail) removeThumbnail.disabled = false;
                restoreCurrentThumbnail();
                return;
            }

            if (removeThumbnail) {
                removeThumbnail.checked = false;
                removeThumbnail.disabled = true;
            }
            objectUrl = URL.createObjectURL(selectedFile);
            showThumbnail(objectUrl);
        });

        removeThumbnail?.addEventListener('change', () => {
            if (thumbnailFile.files?.length) return;
            restoreCurrentThumbnail();
        });

        restoreCurrentThumbnail();
        window.addEventListener('beforeunload', releaseObjectUrl, {once: true});
    }

    // 화면의 '콘텐츠 구분'. 값은 GENERAL_DOMESTIC / GENERAL_INTERNATIONAL / GUIDE / FESTIVAL 이다.
    const contentType = document.getElementById('travel-info-content-type');
    const scopeField = document.getElementById('travel-info-scope-field');
    const scopeSelect = document.getElementById('travel-info-scope');
    const categorySelect = document.getElementById('travel-info-category');
    const periodSection = document.getElementById('travel-info-period-section');
    const periodList = document.getElementById('travel-info-period-list');
    const addPeriodButton = document.getElementById('add-travel-info-period');
    const homeFeatured = document.getElementById('travel-info-home-featured');
    const homeFeaturedCheck = document.getElementById('travel-info-home-featured-check');
    const homeFeaturedOrder = document.getElementById('travel-info-home-featured-order');

    if (!contentType || !periodSection || !periodList || !addPeriodButton) return;

    function createPeriodRow() {
        const row = document.createElement('div');
        row.className = 'admin-period-row';
        row.dataset.periodRow = '';

        const startField = document.createElement('div');
        startField.className = 'admin-form-field';
        const startLabel = document.createElement('label');
        startLabel.textContent = '시작일';
        const startInput = document.createElement('input');
        startInput.type = 'date';
        startField.append(startLabel, startInput);

        const endField = document.createElement('div');
        endField.className = 'admin-form-field';
        const endLabel = document.createElement('label');
        endLabel.textContent = '종료일';
        const endInput = document.createElement('input');
        endInput.type = 'date';
        endField.append(endLabel, endInput);

        const removeButton = document.createElement('button');
        removeButton.type = 'button';
        removeButton.className = 'admin-btn is-small is-danger';
        removeButton.dataset.removePeriod = '';
        removeButton.textContent = '삭제';

        row.append(startField, endField, removeButton);
        return row;
    }

    function periodRows() {
        return Array.from(periodList.querySelectorAll('[data-period-row]'));
    }

    function reindexPeriods() {
        periodRows().forEach((row, index) => {
            const inputs = row.querySelectorAll('input[type="date"]');
            const labels = row.querySelectorAll('label');
            const startId = `period-start-${index}`;
            const endId = `period-end-${index}`;

            inputs[0].id = startId;
            inputs[0].name = `periods[${index}].startDate`;
            inputs[1].id = endId;
            inputs[1].name = `periods[${index}].endDate`;
            labels[0].htmlFor = startId;
            labels[1].htmlFor = endId;
        });
    }

    function addPeriod() {
        periodList.append(createPeriodRow());
        reindexPeriods();
    }

    /* 콘텐츠 구분을 저장 유형(content_type)으로 읽는다. 국내/해외 여행정보는 둘 다 GENERAL 이다. */
    function selectedContentType() {
        return contentType.value.startsWith('GENERAL') ? 'GENERAL' : contentType.value;
    }

    /*
      서버는 모든 정보 카테고리를 그려 둔다(지금 유형이 아닌 것은 hidden/disabled).
      처음에 한 번 모아 두고, 유형이 바뀌면 그 유형의 option 만 select 에 다시 넣는다.
      hidden 만으로 숨기면 option 을 그대로 보여 주는 브라우저(Safari)가 있어 아예 빼 둔다.
    */
    const categoryOptions = categorySelect
        ? Array.from(categorySelect.querySelectorAll('option[data-content-type]'))
        : [];

    function syncCategoryOptions(selectedType) {
        if (!categorySelect) return;

        const selectedValue = categorySelect.value;
        categoryOptions.forEach(option => {
            const matchesContentType = option.dataset.contentType === selectedType;
            option.hidden = !matchesContentType;
            option.disabled = !matchesContentType;
            if (matchesContentType) {
                categorySelect.append(option);
            } else {
                option.remove();
            }
        });

        // 고른 카테고리가 새 유형에 없으면 선택을 비운다.
        const stillSelectable = categoryOptions.some(option =>
            !option.disabled && option.value === selectedValue);
        categorySelect.value = stillSelectable ? selectedValue : '';
    }

    /*
      국내/해외 여행정보는 콘텐츠 구분이 범위를 정하고, 여행가이드는 범위가 없다.
      축제·행사만 국내/해외를 따로 고른다. 막아 둔 select 는 전송되지 않는다.
    */
    function syncScopeAvailability(selectedType) {
        if (!scopeSelect) return;
        const festival = selectedType === 'FESTIVAL';
        if (scopeField) scopeField.hidden = !festival;
        scopeSelect.disabled = !festival;
    }

    function updatePeriodVisibility(selectedType) {
        const festival = selectedType === 'FESTIVAL';
        periodSection.hidden = !festival;
        periodRows().forEach(row => {
            row.querySelectorAll('input').forEach(input => {
                input.disabled = !festival;
            });
        });
        if (festival && periodRows().length === 0) addPeriod();
    }

    /*
      메인 추천은 국내/해외 여행정보·여행가이드만 쓴다. 축제·행사면 숨기고 막아 둔다(서버도 저장하지 않는다).
      체크는 노출 여부만 정하고, 순서는 체크와 상관없이 고칠 수 있다.
    */
    function syncHomeFeatured(selectedType) {
        if (!homeFeatured || !homeFeaturedCheck || !homeFeaturedOrder) return;
        const festival = selectedType === 'FESTIVAL';
        homeFeatured.hidden = festival;
        homeFeaturedCheck.disabled = festival;
        homeFeaturedOrder.disabled = festival;
    }

    /*
      콘텐츠 구분 하나가 기준이다. 저장 유형을 먼저 정하고, 그 유형에 딸린 화면을 차례로 맞춘다.
      (TourAPI 불러오기 영역은 admin-travel-info-festival-autofill.js 가 같은 select 의 값으로 맞춘다)
    */
    function syncContentSection() {
        const selectedType = selectedContentType();
        syncCategoryOptions(selectedType);
        syncScopeAvailability(selectedType);
        updatePeriodVisibility(selectedType);
        syncHomeFeatured(selectedType);
    }

    addPeriodButton.addEventListener('click', addPeriod);
    periodList.addEventListener('click', event => {
        const removeButton = event.target.closest('[data-remove-period]');
        if (!removeButton) return;
        removeButton.closest('[data-period-row]')?.remove();
        reindexPeriods();
    });
    contentType.addEventListener('change', syncContentSection);

    reindexPeriods();
    syncContentSection();
});
