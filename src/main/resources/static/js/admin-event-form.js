document.addEventListener('DOMContentLoaded', () => {
    const bindPreview = (inputId, previewId, emptyId) => {
        const input = document.getElementById(inputId);
        const preview = document.getElementById(previewId);
        const empty = document.getElementById(emptyId);
        if (!input || !preview || !empty) return;

        input.addEventListener('change', () => {
            if (preview.dataset.objectUrl) {
                URL.revokeObjectURL(preview.dataset.objectUrl);
                delete preview.dataset.objectUrl;
            }
            const selectedFile = input.files && input.files[0];
            if (!selectedFile) return;

            const objectUrl = URL.createObjectURL(selectedFile);
            preview.dataset.objectUrl = objectUrl;
            preview.src = objectUrl;
            preview.hidden = false;
            empty.hidden = true;
        });
    };

    bindPreview('event-image', 'event-image-preview', 'event-image-empty');
    bindPreview('event-poster', 'event-poster-preview', 'event-poster-empty');

    const form = document.getElementById('admin-event-form');
    const typeInputs = Array.from(document.querySelectorAll('input[name="eventType"]'));
    const slideInput = document.getElementById('event-slide');
    const panels = Array.from(document.querySelectorAll('[data-event-panel]'));
    const descriptionInput = document.getElementById('event-description');
    const posterInput = document.getElementById('event-poster');
    const imageInput = document.getElementById('event-image');

    // 숨겨진 영역의 값은 지우지 않고 유지한다. 필수 여부만 선택한 유형 정책에 맞춘다.
    const hasExisting = (input) => !!input && input.dataset.hasExisting === 'true';
    const syncEventTypePanels = () => {
        const selectedType = typeInputs.find((input) => input.checked)?.value || 'INFOGRAPHIC';
        const isStandard = selectedType === 'STANDARD';
        const showSlideImage = !!slideInput && slideInput.checked;
        const visible = {
            poster: !isStandard,
            mainImage: isStandard || showSlideImage,
            description: isStandard
        };

        panels.forEach((panel) => {
            panel.hidden = visible[panel.dataset.eventPanel] !== true;
        });
        if (descriptionInput) {
            descriptionInput.required = visible.description;
        }
        if (posterInput) {
            posterInput.required = visible.poster && !hasExisting(posterInput);
        }
        if (imageInput) {
            imageInput.required = showSlideImage && !hasExisting(imageInput);
        }
    };

    typeInputs.forEach((input) => input.addEventListener('change', syncEventTypePanels));
    if (slideInput) {
        slideInput.addEventListener('change', syncEventTypePanels);
    }
    syncEventTypePanels();

    const dateGroups = Array.from(document.querySelectorAll('[data-date-group]'));

    const normalizeShortPart = (input) => {
        if (input.value.length === 1) {
            input.value = input.value.padStart(2, '0');
        }
    };

    const dateFromGroup = (group) => {
        const yearInput = group.querySelector('[data-date-part="year"]');
        const monthInput = group.querySelector('[data-date-part="month"]');
        const dayInput = group.querySelector('[data-date-part="day"]');
        const year = Number(yearInput.value);
        const month = Number(monthInput.value);
        const day = Number(dayInput.value);
        const date = new Date(Date.UTC(year, month - 1, day));
        const valid = yearInput.value.length === 4
            && month >= 1 && month <= 12
            && day >= 1 && day <= 31
            && date.getUTCFullYear() === year
            && date.getUTCMonth() === month - 1
            && date.getUTCDate() === day;
        return {date, dayInput, valid};
    };

    dateGroups.forEach((group) => {
        const inputs = Array.from(group.querySelectorAll('[data-date-part]'));
        inputs.forEach((input, index) => {
            input.addEventListener('input', () => {
                input.value = input.value.replace(/\D/g, '').slice(0, input.maxLength);
                input.setCustomValidity('');

                const isYearComplete = input.dataset.datePart === 'year'
                    && input.value.length === 4;
                const isShortPartComplete = input.dataset.datePart !== 'year'
                    && input.value.length === 2;
                const nextInput = inputs[index + 1];
                if ((isYearComplete || isShortPartComplete) && nextInput) {
                    nextInput.focus();
                }
            });

            input.addEventListener('blur', () => {
                if (input.dataset.datePart !== 'year') {
                    normalizeShortPart(input);
                }
            });

            input.addEventListener('keydown', (event) => {
                const previousInput = inputs[index - 1];
                if (event.key === 'Backspace' && input.value === '' && previousInput) {
                    event.preventDefault();
                    previousInput.focus();
                }
            });
        });
    });

    if (form) {
        form.addEventListener('submit', (event) => {
            dateGroups.forEach((group) => {
                group.querySelectorAll('[data-date-part="month"], [data-date-part="day"]')
                    .forEach(normalizeShortPart);
            });

            const parsedDates = dateGroups.map(dateFromGroup);
            parsedDates.forEach(({dayInput, valid}) => {
                dayInput.setCustomValidity(valid ? '' : '실제로 존재하는 날짜를 입력해 주세요.');
            });
            if (parsedDates.some(({valid}) => !valid)) {
                event.preventDefault();
                form.reportValidity();
            }
        });
    }
});
