(() => {
    const page = document.querySelector("[data-admin-dashboard-page]");
    if (!page) {
        return;
    }

    if (window.lucide?.createIcons) {
        window.lucide.createIcons();
    }
})();
