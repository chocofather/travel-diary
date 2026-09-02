export function detailMessage(name, ...values) {
    const source = document.getElementById('destination-detail-i18n');
    const template = source?.dataset[name] || '';
    return template.replace(/\{(\d+)\}/g, (match, index) =>
        index < values.length ? String(values[index]) : match
    );
}
