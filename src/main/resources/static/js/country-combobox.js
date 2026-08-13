(function () {
    function extractHangulInitials(value) {
        const initials = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
        return Array.from(String(value ?? ''), character => {
            const codePoint = character.codePointAt(0);
            if (codePoint < 0xAC00 || codePoint > 0xD7A3) return character;
            return initials[Math.floor((codePoint - 0xAC00) / 588)];
        }).join('');
    }

    function initCountryCombobox(config) {
        const root = config.root;
        const input = config.input;
        const listbox = config.listbox;
        const options = Array.from(config.options || []);
        const empty = config.empty;
        if (!root || !input || !listbox) return null;

        let highlightedIndex = -1;

        function visibleOptions() {
            return options.filter(option => !option.hidden);
        }

        function close() {
            listbox.hidden = true;
            input.setAttribute('aria-expanded', 'false');
            input.removeAttribute('aria-activedescendant');
            options.forEach(option => option.classList.remove('is-highlighted'));
            highlightedIndex = -1;
        }

        function open() {
            if (config.isDisabled?.()) return;
            listbox.hidden = false;
            input.setAttribute('aria-expanded', 'true');
        }

        function filter() {
            const keyword = input.value.trim().toLocaleLowerCase('ko-KR');
            options.forEach(option => {
                const countryName = option.dataset.countryName || option.textContent || '';
                const normalizedName = countryName.toLocaleLowerCase('ko-KR');
                option.hidden = keyword !== ''
                    && !normalizedName.includes(keyword)
                    && !extractHangulInitials(normalizedName).includes(keyword);
            });
            if (empty) empty.hidden = visibleOptions().length > 0;
            highlightedIndex = -1;
            open();
        }

        function highlight(index) {
            const visible = visibleOptions();
            if (visible.length === 0) return;
            highlightedIndex = (index + visible.length) % visible.length;
            options.forEach(option => option.classList.remove('is-highlighted'));
            const highlighted = visible[highlightedIndex];
            highlighted.classList.add('is-highlighted');
            input.setAttribute('aria-activedescendant', highlighted.id);
            highlighted.scrollIntoView({block: 'nearest'});
        }

        function restoreSelectedName() {
            input.value = config.getSelectedName?.() || '';
        }

        function setSelected(countryId, countryName, showName = true) {
            const selectedId = countryId ? String(countryId) : '';
            options.forEach(option => {
                option.hidden = false;
                option.setAttribute('aria-selected', String(option.dataset.countryId === selectedId));
            });
            if (empty) empty.hidden = true;
            input.value = showName ? countryName || '' : '';
            close();
        }

        options.forEach(option => {
            option.addEventListener('click', () => {
                const accepted = config.onSelect?.(
                    option.dataset.countryId || '',
                    option.dataset.countryName || option.textContent || ''
                );
                if (accepted === false) {
                    restoreSelectedName();
                    close();
                }
            });
        });

        input.addEventListener('focus', filter);
        input.addEventListener('click', filter);
        input.addEventListener('input', filter);
        input.addEventListener('keydown', event => {
            const visible = visibleOptions();
            if (event.key === 'ArrowDown') {
                event.preventDefault();
                open();
                highlight(highlightedIndex + 1);
            } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                open();
                highlight(highlightedIndex - 1);
            } else if (event.key === 'Enter' && !listbox.hidden) {
                event.preventDefault();
                const option = visible[highlightedIndex] || (visible.length === 1 ? visible[0] : null);
                option?.click();
            } else if (event.key === 'Escape') {
                event.preventDefault();
                restoreSelectedName();
                close();
            }
        });

        document.addEventListener('click', event => {
            if (!root.contains(event.target)) {
                restoreSelectedName();
                close();
            }
        });

        return {close, filter, setSelected};
    }

    window.CountryCombobox = {extractHangulInitials, init: initCountryCombobox};
})();
