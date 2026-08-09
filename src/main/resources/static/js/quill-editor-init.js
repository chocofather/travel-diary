window.initQuillEditor = function (editorSelector, contentInputId, formId, initialContentId) {
    const editorElement = document.querySelector(editorSelector);
    const contentInput = document.getElementById(contentInputId);
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

    function localizeToolbar(quillEditor) {
        const toolbar = quillEditor.getModule('toolbar')?.container;
        if (!toolbar) return;

        toolbar.setAttribute('aria-label', '본문 편집 도구');
        const buttonLabels = new Map([
            ['.ql-bold', '굵게'],
            ['.ql-italic', '기울임'],
            ['.ql-underline', '밑줄'],
            ['.ql-strike', '취소선'],
            ['.ql-blockquote', '인용'],
            ['.ql-list[value="ordered"]', '번호 목록'],
            ['.ql-list[value="bullet"]', '글머리 목록'],
            ['.ql-link', '링크'],
            ['.ql-image', '이미지'],
            ['.ql-clean', '서식 지우기']
        ]);
        buttonLabels.forEach((label, selector) => {
            setAccessibleLabel(toolbar.querySelector(selector), label);
        });

        localizePicker(toolbar, '.ql-header', '제목 형식', {
            '': '본문', '1': '제목 1', '2': '제목 2', '3': '제목 3',
            '4': '제목 4', '5': '제목 5', '6': '제목 6'
        });
        localizePicker(toolbar, '.ql-font', '글꼴', {
            '': '기본', 'serif': '명조', 'monospace': '고정폭'
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
        [{font: []}, {size: ['small', false, 'large', 'huge']}],
        ['bold', 'italic', 'underline', 'strike'],
        [{color: []}, {background: []}],
        [{align: []}],
        ['blockquote'],
        [{list: 'ordered'}, {list: 'bullet'}],
        ['link', 'image'],
        ['clean']
    ];

    let pendingImageUploads = 0;
    const quill = new Quill(editorElement, {
        theme: 'snow',
        modules: {
            toolbar: {
                container: toolbarOptions,
                handlers: {
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
            }
        }
    });
    editorElement.quillEditorInstance = quill;
    localizeToolbar(quill);

    if (initialContentId) {
        const initialContent = document.getElementById(initialContentId);
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

    if (form.dataset.quillEditorSubmitBound !== 'true') {
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
                event.preventDefault();
                contentInput.value = '';
                alert('본문을 입력해 주세요.');
                quill.focus();
                return;
            }

            contentInput.value = quill.getSemanticHTML();
        });
        form.dataset.quillEditorSubmitBound = 'true';
    }

    return quill;
};
