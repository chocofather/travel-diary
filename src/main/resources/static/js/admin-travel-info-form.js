document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('travel-info-form');
    if (!form) return;

    window.initQuillEditor(
        '#travel-info-editor',
        'travel-info-content',
        'travel-info-form',
        'travel-info-initial-content'
    );

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

    const contentType = document.getElementById('travel-info-content-type');
    const periodSection = document.getElementById('travel-info-period-section');
    const periodList = document.getElementById('travel-info-period-list');
    const addPeriodButton = document.getElementById('add-travel-info-period');

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

    function updatePeriodVisibility() {
        const festival = contentType.value === 'FESTIVAL';
        periodSection.hidden = !festival;
        periodRows().forEach(row => {
            row.querySelectorAll('input').forEach(input => {
                input.disabled = !festival;
            });
        });
        if (festival && periodRows().length === 0) addPeriod();
    }

    addPeriodButton.addEventListener('click', addPeriod);
    periodList.addEventListener('click', event => {
        const removeButton = event.target.closest('[data-remove-period]');
        if (!removeButton) return;
        removeButton.closest('[data-period-row]')?.remove();
        reindexPeriods();
    });
    contentType.addEventListener('change', updatePeriodVisibility);

    reindexPeriods();
    updatePeriodVisibility();
});
