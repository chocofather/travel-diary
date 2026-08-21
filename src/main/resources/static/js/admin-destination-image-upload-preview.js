/**
 * 관리자 여행지 이미지 관리 - 직접 업로드 미리보기.
 * 선택한 파일을 브라우저에서만 보여 주며, 실제 저장과 JPEG/PNG 검증은 서버가 담당한다.
 */
document.addEventListener("DOMContentLoaded", () => {
    // 화면마다 파일 input 이 다르므로 블록이 자기 input 을 id 로 가리킨다.
    document.querySelectorAll("[data-destination-upload-preview]").forEach(setUpPreview);
});

function setUpPreview(preview) {
    const input = document.getElementById(preview.dataset.destinationUploadPreview);
    const count = preview.querySelector("[data-destination-upload-preview-count]");
    const grid = preview.querySelector("[data-destination-upload-preview-grid]");
    if (!input || !count || !grid) return;

    let objectUrls = [];

    function releaseObjectUrls() {
        objectUrls.forEach(objectUrl => URL.revokeObjectURL(objectUrl));
        objectUrls = [];
    }

    // 재선택 시 이전 미리보기와 object URL 을 모두 정리한다.
    function clearPreview() {
        grid.replaceChildren();
        releaseObjectUrls();
        count.textContent = "";
        preview.hidden = true;
    }

    function previewCard(file) {
        const card = document.createElement("figure");
        card.className = "admin-upload-preview-card";

        const objectUrl = URL.createObjectURL(file);
        objectUrls.push(objectUrl);

        const image = document.createElement("img");
        image.className = "admin-upload-preview-image";
        image.src = objectUrl;
        image.alt = `${file.name} 미리보기`;
        image.addEventListener("error", () => {
            // 이 판정은 미리보기 실패일 뿐이며, 최종 이미지 검증은 서버가 한다.
            image.remove();
            const fallback = document.createElement("span");
            fallback.className = "admin-upload-preview-fallback";
            fallback.textContent = "미리보기를 불러올 수 없습니다.";
            card.prepend(fallback);
        });

        const name = document.createElement("figcaption");
        name.className = "admin-upload-preview-name";
        name.textContent = file.name;
        name.title = file.name;

        card.append(image, name);
        return card;
    }

    input.addEventListener("change", () => {
        clearPreview();

        const files = Array.from(input.files ?? []);
        if (files.length === 0) return;

        const fragment = document.createDocumentFragment();
        files.forEach(file => {
            // 한 장이 실패해도 나머지 미리보기는 계속 만든다.
            try {
                fragment.append(previewCard(file));
            } catch (error) {
                console.warn("미리보기를 만들지 못했습니다.", error);
            }
        });

        grid.append(fragment);
        count.textContent = `선택한 이미지 ${files.length}장`;
        preview.hidden = false;
    });

    window.addEventListener("pagehide", releaseObjectUrls);
}
