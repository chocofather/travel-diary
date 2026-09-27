
$(document).ready(function () {
        const $carousel = $('.carousel-wrapper .carousel');
        const $track = $carousel.find('.track');
        const $slides = $track.find('.slide');
        const $attribution = $('[data-destination-image-attribution]');
        const $source = $attribution.find('[data-destination-image-source]');
        const $separator = $attribution.find('[data-destination-image-separator]');
        const $license = $attribution.find('[data-destination-image-license]');
        const $photographerSeparator = $attribution.find('[data-destination-image-photographer-separator]');
        const $photographerPrefix = $attribution.find('[data-destination-image-photographer-prefix]');
        const $photographer = $attribution.find('[data-destination-image-photographer]');
        const $sourceLink = $attribution.find('[data-destination-image-source-link]');
        const $licenseLink = $attribution.find('[data-destination-image-license-link]');
        const $creditSeparator = $attribution.find('[data-destination-image-credit-separator]');
        const $creditPrefix = $attribution.find('[data-destination-image-credit-prefix]');
        const $credit = $attribution.find('[data-destination-image-credit]');
        let idx = 0;

        if ($slides.length === 0) return;

        // 초기 스타일 적용
        $track.css({
            display: 'flex',
            transition: 'transform 0.4s ease'
        });

        $slides.each(function () {
            $(this).css({
                minWidth: '100%',
                flexShrink: 0
            });
        });

        function updateAttribution() {
            if ($attribution.length === 0) return;
            const slide = $slides.get(idx);
            const sourceName = slide?.dataset.sourceName || '';
            const licenseLabel = slide?.dataset.licenseLabel || '';
            const photographer = slide?.dataset.photographer || '';
            const sourceUrl = slide?.dataset.sourceUrl || '';
            const licenseUrl = slide?.dataset.licenseUrl || '';
            const credit = slide?.dataset.credit || '';

            $attribution.prop('hidden', !sourceName && !licenseLabel && !photographer && !sourceUrl);
            $source.text(sourceName).prop('hidden', !sourceName);
            // 라이선스 전문 주소가 있으면 라이선스명을 링크로 보여준다.
            $license.text(licenseLabel).prop('hidden', !licenseLabel || Boolean(licenseUrl));
            $licenseLink.text(licenseLabel).prop('hidden', !licenseLabel || !licenseUrl);
            if (licenseLabel && licenseUrl) {
                $licenseLink.attr('href', licenseUrl);
            } else {
                $licenseLink.removeAttr('href');
            }
            $creditSeparator.prop('hidden', !credit);
            $creditPrefix.prop('hidden', !credit);
            $credit.text(credit).prop('hidden', !credit);
            $separator.prop('hidden', !sourceName || !licenseLabel);
            $photographerSeparator.prop('hidden', !photographer || (!sourceName && !licenseLabel));
            $photographerPrefix.prop('hidden', !photographer);
            $photographer.text(photographer).prop('hidden', !photographer);
            $sourceLink.prop('hidden', !sourceUrl);
            if (sourceUrl) {
                $sourceLink.attr('href', sourceUrl);
            } else {
                $sourceLink.removeAttr('href');
            }
        }

        function move(n) {
            idx = (n + $slides.length) % $slides.length;
            $track.css('transform', `translateX(-${idx * 100}%)`);
            $slides.each(function (index) {
                const active = index === idx;
                $(this).toggleClass('active', active).attr('aria-hidden', String(!active));
            });
            updateAttribution();
        }

        $carousel.find('.prev').click(function () {
            move(idx - 1);
        });

        $carousel.find('.next').click(function () {
            move(idx + 1);
        });

        // 확대 모달의 이전/다음 이동도 배경 캐러셀과 출처 캡션에 같은 index를 반영한다.
        document.addEventListener('destination-gallery-change', function (event) {
            const nextIndex = Number(event.detail?.index);
            if (Number.isInteger(nextIndex) && nextIndex >= 0 && nextIndex < $slides.length
                    && nextIndex !== idx) {
                move(nextIndex);
            }
        });

        move(0);
    });
