(() => {
    const toast = document.querySelector("[data-withdrawal-toast]");
    if (!toast) return;

    const currentUrl = new URL(window.location.href);
    if (currentUrl.searchParams.has("withdrawn")) {
        currentUrl.searchParams.delete("withdrawn");
        window.history.replaceState(
            window.history.state,
            document.title,
            currentUrl.pathname + currentUrl.search + currentUrl.hash
        );
    }

    window.setTimeout(() => {
        toast.classList.add("is-hiding");
        window.setTimeout(() => toast.remove(), 180);
    }, 3600);
})();
