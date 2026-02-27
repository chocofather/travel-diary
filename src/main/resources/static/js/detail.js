document.addEventListener('DOMContentLoaded', function() {
    const tooltip = document.getElementById('amenity-tooltip');
    if (!tooltip) return;

    document.querySelectorAll('.amenities-list li').forEach(function(li) {
        li.addEventListener('mouseenter', function(e) {
            const text = li.getAttribute('data-tooltip');
            if (!text) return;
            tooltip.textContent = text;
            tooltip.style.display = 'block';
            // 위치 조정 (마우스 근처)
            tooltip.style.left = (e.clientX + 12) + 'px';
            tooltip.style.top = (e.clientY + 8) + 'px';
        });
        li.addEventListener('mousemove', function(e) {
            tooltip.style.left = (e.clientX + 12) + 'px';
            tooltip.style.top = (e.clientY + 8) + 'px';
        });
        li.addEventListener('mouseleave', function() {
            tooltip.style.display = 'none';
        });
    });
});