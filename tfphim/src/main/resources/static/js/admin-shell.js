(() => {
    const ADMIN_PATHS = new Set([
        "/admin",
        "/dashboard",
        "/me-cung-phim-nhat",
        "/quan-ly-phim",
        "/quan-ly-hero-banner"
    ]);
    const REUSABLE_SCRIPT_PATHS = new Set([
        "/js/main.js",
        "/js/lucide.min.js"
    ]);
    const PAGE_SCRIPT_PATHS = new Set([
        "/js/admin-dashboard.js",
        "/js/admin-movies.js",
        "/js/admin-hero-banner.js"
    ]);

    let pendingController = null;

    function currentAdminPage() {
        return document.querySelector(".admin-dashboard-page, .admin-movies-page, .admin-hero-page");
    }

    function normalizeAdminPath(pathname) {
        if (pathname === "/dashboard") {
            return "/admin";
        }
        if (pathname === "/quan-ly-phim") {
            return "/me-cung-phim-nhat";
        }
        return pathname;
    }

    function isAdminUrl(url) {
        return url.origin === window.location.origin && ADMIN_PATHS.has(url.pathname);
    }

    function scriptPath(src) {
        try {
            return new URL(src, window.location.origin).pathname;
        } catch (error) {
            return "";
        }
    }

    function stylesheetHref(link) {
        const href = link.getAttribute("href");
        if (!href) {
            return "";
        }
        return new URL(href, window.location.origin).href;
    }

    function isAdminStylesheet(link) {
        const href = stylesheetHref(link);
        return href.includes("/css/admin-");
    }

    function syncAttributes(target, source) {
        Array.from(target.attributes).forEach((attribute) => target.removeAttribute(attribute.name));
        Array.from(source.attributes).forEach((attribute) => target.setAttribute(attribute.name, attribute.value));
    }

    function loadStylesheet(link) {
        const nextLink = link.cloneNode(true);
        const siteFixes = document.querySelector('link[rel="stylesheet"][href*="/css/site-fixes.css"]');
        const loadPromise = new Promise((resolve) => {
            nextLink.addEventListener("load", resolve, { once: true });
            nextLink.addEventListener("error", resolve, { once: true });
            window.setTimeout(resolve, 800);
        });
        document.head.insertBefore(nextLink, siteFixes || null);
        return loadPromise;
    }

    async function syncAdminStyles(nextDocument) {
        const nextAdminLinks = Array.from(nextDocument.querySelectorAll('link[rel="stylesheet"]'))
                .filter(isAdminStylesheet);
        const nextHrefs = new Set(nextAdminLinks.map(stylesheetHref).filter(Boolean));
        const currentAdminLinks = Array.from(document.querySelectorAll('link[rel="stylesheet"]'))
                .filter(isAdminStylesheet);
        const currentHrefs = new Set(currentAdminLinks.map(stylesheetHref).filter(Boolean));
        const pendingLoads = [];

        nextAdminLinks.forEach((link) => {
            const href = stylesheetHref(link);
            if (href && !currentHrefs.has(href)) {
                pendingLoads.push(loadStylesheet(link));
                currentHrefs.add(href);
            }
        });

        await Promise.all(pendingLoads);

        currentAdminLinks.forEach((link) => {
            const href = stylesheetHref(link);
            if (href && !nextHrefs.has(href)) {
                link.remove();
            }
        });
    }

    function updateSidebarActive(targetUrl, nextDocument) {
        const activePaths = new Set();
        nextDocument.querySelectorAll(".admin-sidebar a.is-active").forEach((link) => {
            try {
                activePaths.add(normalizeAdminPath(new URL(link.getAttribute("href"), window.location.origin).pathname));
            } catch (error) {
            }
        });

        if (activePaths.size === 0) {
            activePaths.add(normalizeAdminPath(targetUrl.pathname));
        }

        document.querySelectorAll(".admin-sidebar a").forEach((link) => {
            const href = link.getAttribute("href");
            let isActive = false;
            try {
                const linkUrl = new URL(href, window.location.origin);
                isActive = activePaths.has(normalizeAdminPath(linkUrl.pathname));
            } catch (error) {
                isActive = false;
            }

            link.classList.toggle("is-active", isActive);
            if (isActive) {
                link.setAttribute("aria-current", "page");
            } else {
                link.removeAttribute("aria-current");
            }
        });
    }

    function replaceAdminContent(nextDocument) {
        const page = currentAdminPage();
        const nextPage = nextDocument.querySelector(".admin-dashboard-page, .admin-movies-page, .admin-hero-page");
        const sidebar = page?.querySelector(".admin-sidebar");
        const nextMain = nextPage?.querySelector(".admin-main");

        if (!page || !nextPage || !sidebar || !nextMain) {
            return false;
        }

        syncAttributes(page, nextPage);
        page.replaceChildren(sidebar, nextMain.cloneNode(true));
        return true;
    }

    function loadScript(src, { reusable = false } = {}) {
        const path = scriptPath(src);
        if (!path) {
            return Promise.resolve();
        }

        if (reusable && document.querySelector(`script[src$="${path}"]`)) {
            return Promise.resolve();
        }

        return new Promise((resolve, reject) => {
            const script = document.createElement("script");
            script.src = src;
            script.async = false;
            script.dataset.adminShellDynamic = "true";
            script.addEventListener("load", resolve, { once: true });
            script.addEventListener("error", reject, { once: true });
            document.body.appendChild(script);
        });
    }

    async function runPageScripts(nextDocument) {
        const scripts = Array.from(nextDocument.querySelectorAll("script[src]"));
        for (const script of scripts) {
            const src = script.getAttribute("src");
            const path = scriptPath(src);
            if (!path || path === "/js/admin-shell.js") {
                continue;
            }

            if (REUSABLE_SCRIPT_PATHS.has(path)) {
                await loadScript(src, { reusable: true });
                continue;
            }

            if (PAGE_SCRIPT_PATHS.has(path)) {
                await loadScript(src);
            }
        }

        if (window.lucide?.createIcons) {
            window.lucide.createIcons();
        }
    }

    function scrollToHash(hash) {
        if (!hash) {
            return;
        }

        const target = document.getElementById(decodeURIComponent(hash.slice(1)));
        target?.scrollIntoView({ block: "start" });
    }

    async function navigateTo(url, { push = true } = {}) {
        const targetUrl = typeof url === "string" ? new URL(url, window.location.href) : url;
        if (!isAdminUrl(targetUrl)) {
            window.location.href = targetUrl.href;
            return;
        }

        if (normalizeAdminPath(targetUrl.pathname) === normalizeAdminPath(window.location.pathname)
                && targetUrl.hash
                && targetUrl.hash !== window.location.hash) {
            if (push) {
                window.history.pushState({ adminShell: true }, "", targetUrl.href);
            }
            scrollToHash(targetUrl.hash);
            return;
        }

        pendingController?.abort();
        pendingController = new AbortController();
        const controller = pendingController;
        document.body.classList.add("admin-shell-loading");

        try {
            const response = await fetch(targetUrl.href, {
                credentials: "same-origin",
                signal: controller.signal,
                headers: {
                    "Accept": "text/html",
                    "X-Requested-With": "AdminShell"
                }
            });

            if (!response.ok) {
                throw new Error(`Admin navigation failed: ${response.status}`);
            }

            const html = await response.text();
            const nextDocument = new DOMParser().parseFromString(html, "text/html");
            const finalUrl = new URL(response.url || targetUrl.href, window.location.href);

            await syncAdminStyles(nextDocument);
            document.title = nextDocument.title || document.title;
            document.body.className = nextDocument.body.className;

            if (!replaceAdminContent(nextDocument)) {
                window.location.href = finalUrl.href;
                return;
            }

            updateSidebarActive(finalUrl, nextDocument);
            await runPageScripts(nextDocument);

            if (push) {
                window.history.pushState({ adminShell: true }, "", finalUrl.href);
            }

            if (finalUrl.hash) {
                scrollToHash(finalUrl.hash);
            }
        } catch (error) {
            if (error.name !== "AbortError") {
                window.location.href = targetUrl.href;
            }
        } finally {
            if (pendingController === controller) {
                pendingController = null;
            }
            document.body.classList.remove("admin-shell-loading");
        }
    }

    document.addEventListener("click", (event) => {
        const link = event.target.closest("a[href]");
        if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }

        if (link.target && link.target !== "_self") {
            return;
        }

        const url = new URL(link.getAttribute("href"), window.location.href);
        if (!isAdminUrl(url)) {
            return;
        }

        if (normalizeAdminPath(url.pathname) === normalizeAdminPath(window.location.pathname)
                && url.hash === window.location.hash) {
            return;
        }

        event.preventDefault();
        navigateTo(url);
    });

    window.addEventListener("popstate", () => {
        const url = new URL(window.location.href);
        if (isAdminUrl(url)) {
            navigateTo(url, { push: false });
        }
    });
})();
