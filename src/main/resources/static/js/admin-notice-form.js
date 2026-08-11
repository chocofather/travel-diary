document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('notice-form');
    if (!form) return;

    window.initQuillEditor(
        '#notice-editor',
        'notice-content',
        'notice-form',
        'notice-initial-content'
    );
});
