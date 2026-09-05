/**
 * Quill 편집기를 만들어 hidden input 에 붙인다.
 *
 * <p>editor/content/initial 자리에는 선택자·id 문자열 대신 element 를 그대로 넘겨도 된다.
 * 한 폼에 편집기가 여러 개 있을 수 있어(예: 언어별 본문) submit 연결은 편집기마다 따로 건다.
 * options.required 가 false 면 빈 본문이어도 저장을 막지 않는다. (선택 입력 편집기)
 */
window.initQuillEditor = function (editorSelector, contentInputId, formId, initialContentId,
                                   options = {}) {
    const required = options.required !== false;
    const editorElement = typeof editorSelector === 'string'
        ? document.querySelector(editorSelector)
        : editorSelector;
    const contentInput = typeof contentInputId === 'string'
        ? document.getElementById(contentInputId)
        : contentInputId;
    const form = document.getElementById(formId);

    if (!editorElement) {
        console.error(`[initQuillEditor] editor ${editorSelector}을(를) 찾을 수 없습니다.`);
        return null;
    }
    if (!contentInput) {
        console.error(`[initQuillEditor] content input#${contentInputId}을(를) 찾을 수 없습니다.`);
        return null;
    }
    if (!form) {
        console.error(`[initQuillEditor] form#${formId}을(를) 찾을 수 없습니다.`);
        return null;
    }
    if (typeof Quill === 'undefined') {
        console.error('[initQuillEditor] Quill을 불러오지 못했습니다.');
        return null;
    }
    if (editorElement.quillEditorInstance) {
        return editorElement.quillEditorInstance;
    }

    const Font = Quill.import('formats/font');
    Font.whitelist = [
        'serif',
        'monospace',
        'pretendard',
        'noto-sans-kr',
        'noto-serif-kr',
        'nanum-human',
        'school-safe-bareonbatang',
        'cafe24-dongdong',
        'gangwon-saeeum'
    ];
    Quill.register(Font, true);
    const Delta = Quill.import('delta');
    const fontFormatsByClass = new Map([
        ['ql-font-serif', 'serif'],
        ['ql-font-monospace', 'monospace'],
        ['ql-font-pretendard', 'pretendard'],
        ['ql-font-noto-sans-kr', 'noto-sans-kr'],
        ['ql-font-noto-serif-kr', 'noto-serif-kr'],
        ['ql-font-nanum-human', 'nanum-human'],
        ['ql-font-school-safe-bareonbatang', 'school-safe-bareonbatang'],
        ['ql-font-cafe24-dongdong', 'cafe24-dongdong'],
        ['ql-font-gangwon-saeeum', 'gangwon-saeeum']
    ]);

    function normalizeLinkUrl(rawUrl) {
        const url = rawUrl.trim();
        if (!url) return null;

        if (/^(?:javascript|data|vbscript):/i.test(url) || url.startsWith('//')) {
            return null;
        }
        if (/^https?:\/\//i.test(url)) {
            try {
                const parsed = new URL(url);
                return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? url : null;
            } catch {
                return null;
            }
        }
        if (/^mailto:[^\s]+$/i.test(url)) return url;
        if (/^tel:[+0-9().\- ]+$/i.test(url)) return url;
        if ((url.startsWith('/') && !url.startsWith('//')) || url.startsWith('#')) {
            return /\s/.test(url) ? null : url;
        }

        const localhost = url.match(/^localhost(?::(\d{1,5}))?(?:[/?#][^\s]*)?$/i);
        if (localhost) {
            return isValidPort(localhost[1]) ? `http://${url}` : null;
        }

        const ipv4 = url.match(/^((?:\d{1,3}\.){3}\d{1,3})(?::(\d{1,5}))?(?:[/?#][^\s]*)?$/);
        if (ipv4) {
            const validAddress = ipv4[1].split('.').every(part => Number(part) <= 255);
            return validAddress && isValidPort(ipv4[2]) ? `http://${url}` : null;
        }
        if (/^[a-z][a-z0-9+.-]*:/i.test(url)) {
            return null;
        }

        const domain = url.match(/^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}(?::(\d{1,5}))?(?:[/?#][^\s]*)?$/i);
        return domain && isValidPort(domain[1]) ? `https://${url}` : null;
    }

    function isValidPort(port) {
        if (port === undefined) return true;
        const number = Number(port);
        return number >= 1 && number <= 65535;
    }

    function setAccessibleLabel(element, label) {
        if (!element) return;
        element.title = label;
        element.setAttribute('aria-label', label);
    }

    function localizePicker(toolbar, selector, label, optionLabels) {
        const picker = toolbar.querySelector(selector);
        if (!picker) return;

        setAccessibleLabel(picker.querySelector('.ql-picker-label'), label);
        picker.querySelectorAll('.ql-picker-item').forEach(item => {
            const value = item.getAttribute('data-value') ?? '';
            setAccessibleLabel(item, optionLabels[value] ?? label);
        });
    }

    function groupToolbarFormats(toolbar) {
        if (toolbar.querySelector('.quill-toolbar-group-set')) return;

        const formatGroups = Array.from(toolbar.children)
            .filter(element => element.classList.contains('ql-formats'));
        const secondRowIndex = formatGroups.findIndex(group => group.querySelector('.ql-blockquote'));
        if (secondRowIndex <= 0) return;

        const firstRow = document.createElement('div');
        firstRow.className = 'quill-toolbar-group-set is-text-format';
        firstRow.setAttribute('role', 'group');
        firstRow.setAttribute('aria-label', '글자 서식 도구');

        const secondRow = document.createElement('div');
        secondRow.className = 'quill-toolbar-group-set is-block-format';
        secondRow.setAttribute('role', 'group');
        secondRow.setAttribute('aria-label', '문단 및 삽입 도구');

        formatGroups.forEach((group, index) => {
            (index < secondRowIndex ? firstRow : secondRow).appendChild(group);
        });
        toolbar.append(firstRow, secondRow);
    }

    function localizeToolbar(quillEditor) {
        const toolbar = quillEditor.getModule('toolbar')?.container;
        if (!toolbar) return;

        toolbar.setAttribute('aria-label', '본문 편집 도구');
        groupToolbarFormats(toolbar);
        const buttonLabels = new Map([
            ['.ql-bold', '굵게'],
            ['.ql-italic', '기울임'],
            ['.ql-underline', '밑줄'],
            ['.ql-strike', '취소선'],
            ['.ql-blockquote', '인용'],
            ['.ql-list[value="ordered"]', '번호 목록'],
            ['.ql-list[value="bullet"]', '글머리 목록'],
            ['.ql-list[value="check"]', '체크리스트'],
            ['.ql-indent[value="-1"]', '내어쓰기'],
            ['.ql-indent[value="+1"]', '들여쓰기'],
            ['.ql-undo', '실행 취소'],
            ['.ql-redo', '다시 실행'],
            ['.ql-link', '링크'],
            ['.ql-image', '이미지'],
            ['.ql-clean', '서식 지우기']
        ]);
        buttonLabels.forEach((label, selector) => {
            setAccessibleLabel(toolbar.querySelector(selector), label);
        });

        const undoButton = toolbar.querySelector('.ql-undo');
        const redoButton = toolbar.querySelector('.ql-redo');
        if (undoButton) undoButton.textContent = '↶';
        if (redoButton) redoButton.textContent = '↷';

        localizePicker(toolbar, '.ql-header', '제목 형식', {
            '': '본문', '1': '제목 1', '2': '제목 2', '3': '제목 3',
            '4': '제목 4', '5': '제목 5', '6': '제목 6'
        });
        localizePicker(toolbar, '.ql-font', '글꼴', {
            '': '기본',
            'pretendard': 'Pretendard',
            'noto-sans-kr': 'Noto Sans KR',
            'noto-serif-kr': 'Noto Serif KR',
            'nanum-human': '나눔휴먼',
            'school-safe-bareonbatang': '학교안심 바른바탕',
            'cafe24-dongdong': '카페24 동동',
            'gangwon-saeeum': '강원교육새음',
            'monospace': '고정폭'
        });
        localizePicker(toolbar, '.ql-size', '글자 크기', {
            '': '보통', 'small': '작게', 'large': '크게', 'huge': '아주 크게'
        });
        localizePicker(toolbar, '.ql-color', '글자색', {});
        localizePicker(toolbar, '.ql-background', '배경색', {});
        localizePicker(toolbar, '.ql-align', '정렬', {
            '': '왼쪽 정렬', 'center': '가운데 정렬',
            'right': '오른쪽 정렬', 'justify': '양쪽 정렬'
        });
    }

    const toolbarOptions = [
        [{header: [1, 2, 3, 4, 5, 6, false]}],
        [{font: [false, 'pretendard', 'noto-sans-kr', 'noto-serif-kr',
                'nanum-human', 'school-safe-bareonbatang', 'cafe24-dongdong',
                'gangwon-saeeum', 'monospace']},
            {size: ['small', false, 'large', 'huge']}],
        ['bold', 'italic', 'underline', 'strike'],
        [{color: []}, {background: []}],
        [{align: []}],
        ['blockquote'],
        [{list: 'ordered'}, {list: 'bullet'}, {list: 'check'}],
        [{indent: '-1'}, {indent: '+1'}],
        ['link', 'image'],
        ['undo', 'redo'],
        ['clean']
    ];

    // The script-tag UMD bundle registers modules/resize by itself. Its
    // window.QuillResize export is a module namespace object, not the module
    // constructor, so registering that object again breaks Quill creation.
    const registeredResizeModule = Quill.import('modules/resize');
    const resizeModuleAvailable = typeof registeredResizeModule === 'function';
    if (!resizeModuleAvailable) {
        console.warn('[initQuillEditor] 이미지 크기 조절 모듈을 불러오지 못했습니다.');
    }

    const resizeOptions = {
        modules: ['Resize'],
        embedTags: [],
        parchment: {
            image: {
                attribute: ['width'],
                limit: {
                    minWidth: 120,
                    maxWidth: 1200
                }
            }
        }
    };

    let pendingImageUploads = 0;
    const quill = new Quill(editorElement, {
        theme: 'snow',
        modules: {
            toolbar: {
                container: toolbarOptions,
                handlers: {
                    undo: function () {
                        this.quill.history.undo();
                    },
                    redo: function () {
                        this.quill.history.redo();
                    },
                    link: function (value) {
                        const range = this.quill.getSelection(true) ?? {
                            index: Math.max(0, this.quill.getLength() - 1),
                            length: 0
                        };

                        if (!value) {
                            if (range.length > 0) {
                                this.quill.formatText(range.index, range.length, 'link', false, 'user');
                            } else {
                                this.quill.format('link', false, 'user');
                            }
                            return;
                        }

                        const currentLink = this.quill.getFormat(range).link ?? '';
                        const input = window.prompt('링크 URL을 입력해 주세요.', currentLink);
                        if (input === null) return;
                        if (!input.trim()) {
                            if (range.length > 0) {
                                this.quill.formatText(range.index, range.length, 'link', false, 'user');
                            } else {
                                this.quill.format('link', false, 'user');
                            }
                            return;
                        }

                        const normalizedUrl = normalizeLinkUrl(input);
                        if (!normalizedUrl) {
                            alert('올바른 링크 주소를 입력해 주세요.');
                            return;
                        }

                        if (range.length > 0) {
                            this.quill.formatText(
                                range.index,
                                range.length,
                                'link',
                                normalizedUrl,
                                'user'
                            );
                            this.quill.setSelection(range.index, range.length, 'silent');
                            return;
                        }

                        const linkText = input.trim();
                        this.quill.insertText(range.index, linkText, 'link', normalizedUrl, 'user');
                        this.quill.setSelection(range.index + linkText.length, 0, 'silent');
                        this.quill.format('link', false, 'silent');
                    },
                    image: function () {
                        const imageInput = document.createElement('input');
                        imageInput.type = 'file';
                        imageInput.accept = 'image/*';
                        imageInput.addEventListener('change', async () => {
                            const image = imageInput.files?.[0];
                            if (!image) return;
                            if (!image.type.startsWith('image/')) {
                                alert('이미지 파일을 선택해 주세요.');
                                return;
                            }

                            const formData = new FormData();
                            formData.append('image', image);
                            pendingImageUploads += 1;

                            try {
                                const response = await fetch('/api/upload/editor-image', {
                                    method: 'POST',
                                    body: formData
                                });
                                if (!response.ok) {
                                    throw new Error(`이미지 업로드 실패: ${response.status}`);
                                }

                                const data = await response.json();
                                if (typeof data.url !== 'string' || !data.url.startsWith('/uploads/editor/')) {
                                    throw new Error('올바르지 않은 이미지 URL 응답입니다.');
                                }

                                const range = this.quill.getSelection(true);
                                this.quill.insertEmbed(range.index, 'image', data.url, 'user');
                                this.quill.setSelection(range.index + 1, 0, 'silent');
                            } catch (error) {
                                console.error(error);
                                alert('이미지 업로드에 실패했습니다.');
                            } finally {
                                pendingImageUploads -= 1;
                            }
                        });
                        imageInput.click();
                    }
                }
            },
            history: true,
            ...(resizeModuleAvailable ? {resize: resizeOptions} : {})
        }
    });
    editorElement.quillEditorInstance = quill;
    localizeToolbar(quill);

    // Quill's default Clipboard conversion does not reliably restore custom
    // class-attributor font values. Reapply only the explicitly registered
    // font classes while still loading all initial HTML through Clipboard.
    quill.clipboard.addMatcher(Node.ELEMENT_NODE, (node, delta) => {
        const fontFormat = Array.from(node.classList ?? [])
            .map(className => fontFormatsByClass.get(className))
            .find(Boolean);
        if (!fontFormat) return delta;
        return delta.compose(new Delta().retain(delta.length(), {font: fontFormat}));
    });

    if (initialContentId) {
        const initialContent = typeof initialContentId === 'string'
            ? document.getElementById(initialContentId)
            : initialContentId;
        if (initialContent?.value) {
            const delta = quill.clipboard.convert({
                html: initialContent.value,
                text: ''
            });
            quill.setContents(delta, 'silent');
        }
    }

    function isPersistableImage(image) {
        const src = image.getAttribute('src')?.trim() ?? '';
        const lower = src.toLowerCase();
        return src.length > 0
            && !src.includes('\\')
            && !/[\u0000-\u001f\u007f]/.test(src)
            && !lower.startsWith('javascript:')
            && !lower.startsWith('data:')
            && !lower.startsWith('blob:')
            && !lower.startsWith('//');
    }

    // 편집기마다 한 번씩만 건다. 같은 폼에 편집기가 여러 개여도 각자 자기 hidden input 을 채운다.
    if (editorElement.dataset.quillEditorSubmitBound !== 'true') {
        form.addEventListener('submit', event => {
            if (pendingImageUploads > 0) {
                event.preventDefault();
                alert('이미지 업로드가 끝난 뒤 다시 저장해 주세요.');
                return;
            }

            const text = quill.getText().trim();
            const hasImage = Array.from(quill.root.querySelectorAll('img[src]'))
                .some(isPersistableImage);

            if (!text && !hasImage) {
                if (required) {
                    event.preventDefault();
                    contentInput.value = '';
                    alert('본문을 입력해 주세요.');
                    quill.focus();
                    return;
                }
                // 선택 입력 편집기는 비어 있어도 막지 않는다. 서버가 빈 값으로 본다.
                contentInput.value = '';
                return;
            }

            contentInput.value = quill.getSemanticHTML();
        });
        editorElement.dataset.quillEditorSubmitBound = 'true';
    }

    return quill;
};
