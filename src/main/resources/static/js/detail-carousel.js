
console.log("jQuery 상태:", typeof $);
$(document).ready(function () {
        const $track = $('.carousel .track');
        const $slides = $track.find('.slide');
        let idx = 0;

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

        function move(n) {
            idx = (n + $slides.length) % $slides.length;
            $track.css('transform', `translateX(-${idx * 100}%)`);
        }

        $('.carousel .prev').click(function () {
            move(idx - 1);
        });

        $('.carousel .next').click(function () {
            move(idx + 1);
        });
    });

