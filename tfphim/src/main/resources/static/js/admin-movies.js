(() => {
    const page = document.querySelector("[data-admin-movies-page]");
    if (!page) {
        return;
    }

    const MANAGED_API_URL = "/api/quan-ly-phim/japan-maze";
    const API_SEARCH_URLS = {
        kk: "/api/movies/search",
        ophim: "/api/movies/ophim/search"
    };

    const state = {
        selected: [],
        results: [],
        source: "all",
        hasSearched: false,
        pointerDrag: null,
        saveQueue: Promise.resolve()
    };

    const els = {
        searchForm: document.querySelector("[data-admin-search-form]"),
        searchInput: document.getElementById("adminMovieSearch"),
        resultCount: document.querySelector("[data-admin-result-count]"),
        results: document.querySelector("[data-admin-results]"),
        selectedList: document.querySelector("[data-admin-selected-list]"),
        selectedCounts: Array.from(document.querySelectorAll("[data-admin-selected-count]")),
        enabledCounts: Array.from(document.querySelectorAll("[data-admin-enabled-count]")),
        disabledCounts: Array.from(document.querySelectorAll("[data-admin-disabled-count]")),
        saveState: document.querySelector("[data-admin-save-state]"),
        saveOrder: document.querySelector("[data-admin-save-order]"),
        sourceButtons: Array.from(document.querySelectorAll("[data-admin-source]"))
    };

    function notify(message, type = "success") {
        if (typeof window.showToast === "function") {
            window.showToast(message, type);
            return;
        }
        console[type === "error" ? "error" : "log"](message);
    }

    function setSaveState(status, label) {
        if (!els.saveState) {
            return;
        }

        const icon = status === "saving"
            ? "fa-spinner fa-spin"
            : status === "error"
                ? "fa-triangle-exclamation"
                : "fa-circle-check";
        els.saveState.className = `admin-movies-status is-${status}`;
        els.saveState.innerHTML = `<i class="fa-solid ${icon}" aria-hidden="true"></i><span>${escapeHtml(label)}</span>`;
    }

    async function fetchJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            headers: {
                "Accept": "application/json",
                ...(options.body ? { "Content-Type": "application/json" } : {}),
                ...(options.headers || {})
            },
            ...options
        });

        if (!response.ok) {
            throw new Error(`Request failed: ${response.status}`);
        }

        return response.json();
    }

    function getItems(payload) {
        if (typeof window.getMovieItems === "function") {
            return window.getMovieItems(payload);
        }
        if (Array.isArray(payload?.items)) {
            return payload.items;
        }
        if (Array.isArray(payload?.data?.items)) {
            return payload.data.items;
        }
        if (Array.isArray(payload?.data)) {
            return payload.data;
        }
        return [];
    }

    function text(value) {
        return String(value ?? "").trim();
    }

    function escapeHtml(value) {
        return text(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    }

    function normalizeSource(source) {
        return text(source).toLowerCase() === "ophim" ? "ophim" : "kk";
    }

    function getOriginalName(movie) {
        return text(movie?.original_name || movie?.origin_name);
    }

    function getYear(movie) {
        if (typeof window.getMovieYear === "function") {
            return text(window.getMovieYear(movie));
        }
        return text(movie?.year || movie?.release_year || movie?.modified?.time?.slice?.(0, 4));
    }

    function getDescription(movie) {
        const description = text(movie?.description || movie?.content || movie?.excerpt);
        if (typeof window.cleanTextSnippet === "function") {
            return window.cleanTextSnippet(description, 360);
        }
        return description.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim().slice(0, 360);
    }

    function getImage(movie) {
        if (typeof window.getMovieImage === "function") {
            return window.getMovieImage(movie);
        }
        return text(movie?.card_image_url || movie?.poster_url || movie?.thumb_url)
            || "https://via.placeholder.com/360x540?text=No+Image";
    }

    function getCategoryLine(category) {
        if (Array.isArray(category)) {
            return category
                .map((item) => (typeof item === "string" ? item : item?.name || ""))
                .map(text)
                .filter(Boolean)
                .join(" - ");
        }
        return text(category);
    }

    function getRating(movie, kind) {
        if (kind === "imdb" && typeof window.getMovieImdbRating === "function") {
            return text(window.getMovieImdbRating(movie));
        }
        if (kind === "tmdb" && typeof window.getMovieTmdbRating === "function") {
            return text(window.getMovieTmdbRating(movie));
        }
        return kind === "imdb"
            ? text(movie?.movie_imdb_rating || movie?.imdb_rating)
            : text(movie?.movie_tmdb_rating || movie?.tmdb_rating);
    }

    function normalizeMovie(movie, source = "") {
        const normalizedSource = normalizeSource(movie?.source || source);
        const originalName = getOriginalName(movie);
        const episode = text(movie?.episode_current || movie?.current_episode);
        const language = text(movie?.lang || movie?.language);
        const name = text(movie?.name || movie?.title || movie?.slug);

        return {
            slug: text(movie?.slug),
            source: normalizedSource,
            name,
            origin_name: originalName,
            original_name: originalName,
            poster_url: text(movie?.poster_url),
            thumb_url: text(movie?.thumb_url),
            card_image_url: text(movie?.card_image_url || getImage({ ...movie, source: normalizedSource })),
            quality: text(movie?.quality) || "HD",
            lang: language,
            language,
            episode_current: episode,
            current_episode: episode,
            total_episodes: text(movie?.total_episodes || movie?.episode_total),
            episode_total: text(movie?.episode_total || movie?.total_episodes),
            status: text(movie?.status || movie?.episode_status || episode),
            year: getYear(movie),
            type: text(movie?.type || movie?.movie_type || movie?.category_type),
            movie_type: text(movie?.movie_type || movie?.type),
            category_type: text(movie?.category_type || movie?.type),
            time: text(movie?.time || movie?.runtime || movie?.duration),
            content_rating: text(movie?.content_rating || movie?.age_rating || movie?.age),
            age_rating: text(movie?.age_rating || movie?.content_rating || movie?.age),
            description: getDescription(movie),
            category: getCategoryLine(movie?.category),
            country: getCategoryLine(movie?.country || movie?.countries),
            trailer_url: text(movie?.trailer_url),
            movie_rating: text(movie?.movie_rating || movie?.rating),
            movie_imdb_rating: getRating(movie, "imdb"),
            movie_tmdb_rating: getRating(movie, "tmdb"),
            imdb_rating: getRating(movie, "imdb"),
            tmdb_rating: getRating(movie, "tmdb"),
            enabled: movie?.enabled !== false,
            order: Number.parseInt(movie?.order, 10) || 0,
            added_at: text(movie?.added_at) || new Date().toISOString(),
            updated_at: text(movie?.updated_at)
        };
    }

    function movieKey(movie) {
        return `${normalizeSource(movie?.source)}|${text(movie?.slug).toLowerCase()}`;
    }

    function normalizeOrder() {
        state.selected = state.selected.map((movie, index) => ({
            ...movie,
            order: index + 1
        }));
    }

    function dedupeMovies(movies) {
        const seenKeys = new Set();
        return movies.filter((movie) => {
            if (!movie.slug) {
                return false;
            }
            const key = movieKey(movie);
            if (seenKeys.has(key)) {
                return false;
            }
            seenKeys.add(key);
            return true;
        });
    }

    function isSelected(movie) {
        const key = movieKey(movie);
        return state.selected.some((selectedMovie) => movieKey(selectedMovie) === key);
    }

    function renderLanguageBadges(movie) {
        const sourceText = [
            movie.lang,
            movie.language,
            movie.episode_current,
            movie.current_episode,
            movie.status
        ].filter(Boolean).join(" ");
        const badges = typeof window.getLanguageBadges === "function"
            ? window.getLanguageBadges(sourceText)
            : [];

        return badges.map((badge) => (
            `<span class="mini-badge ${escapeHtml(badge.className)}">${escapeHtml(badge.label)}</span>`
        )).join("");
    }

    function renderMovieMeta(movie) {
        return [
            movie.source === "ophim" ? "OPhim" : "KK",
            movie.year,
            movie.quality,
            movie.episode_current
        ].filter(Boolean).map(escapeHtml).join(" / ");
    }

    function getSourceLabel(movie) {
        return movie.source === "ophim" ? "OPhim" : "KK";
    }

    function renderSourceChip(movie) {
        const source = normalizeSource(movie?.source);
        return `<span class="admin-source-chip admin-source-chip--${escapeHtml(source)}">${escapeHtml(getSourceLabel(movie))}</span>`;
    }

    function renderResults() {
        if (!els.results || !els.resultCount) {
            return;
        }

        els.resultCount.textContent = state.results.length
            ? `${state.results.length} k\u1ebft qu\u1ea3`
            : state.hasSearched
                ? "Kh\u00f4ng c\u00f3 k\u1ebft qu\u1ea3"
                : "Ch\u01b0a c\u00f3 k\u1ebft qu\u1ea3";

        if (!state.results.length) {
            els.results.innerHTML = `
                <div class="admin-empty-state">
                    <i class="fa-solid fa-film" aria-hidden="true"></i>
                    <span>${state.hasSearched ? "Kh\u00f4ng c\u00f3 phim ph\u00f9 h\u1ee3p" : "T\u00ecm phim t\u1eeb API"}</span>
                </div>
            `;
            return;
        }

        els.results.innerHTML = state.results.map((movie, index) => {
            const selected = isSelected(movie);
            const image = getImage(movie);
            return `
                <article class="admin-result-card${selected ? " is-selected" : ""}">
                    <div class="admin-movie-thumb">
                        <img src="${escapeHtml(image)}" alt="${escapeHtml(movie.name)}" loading="lazy" decoding="async">
                        ${renderSourceChip(movie)}
                    </div>
                    <div class="admin-result-copy">
                        <h3 title="${escapeHtml(movie.name)}">${escapeHtml(movie.name)}</h3>
                        ${movie.original_name ? `<p title="${escapeHtml(movie.original_name)}">${escapeHtml(movie.original_name)}</p>` : ""}
                        <div class="admin-movie-meta">${renderMovieMeta(movie)}</div>
                        <div class="admin-language-row">${renderLanguageBadges(movie)}</div>
                    </div>
                    <button class="admin-add-btn" type="button" data-admin-add="${index}" ${selected ? "disabled" : ""}>
                        <i class="fa-solid ${selected ? "fa-check" : "fa-plus"}" aria-hidden="true"></i>
                        <span>${selected ? "\u0110\u00e3 ch\u1ecdn" : "Th\u00eam"}</span>
                    </button>
                </article>
            `;
        }).join("");
    }

    function renderSelected() {
        if (!els.selectedList) {
            return;
        }

        normalizeOrder();
        const enabledCount = state.selected.filter((movie) => movie.enabled !== false).length;
        const disabledCount = state.selected.length - enabledCount;
        els.selectedCounts.forEach((element) => {
            element.textContent = String(state.selected.length);
        });
        els.enabledCounts.forEach((element) => {
            element.textContent = String(enabledCount);
        });
        els.disabledCounts.forEach((element) => {
            element.textContent = String(disabledCount);
        });

        if (!state.selected.length) {
            els.selectedList.innerHTML = `
                <div class="admin-empty-state">
                    <i class="fa-solid fa-plus" aria-hidden="true"></i>
                    <span>Ch\u01b0a ch\u1ecdn phim</span>
                </div>
            `;
            return;
        }

        els.selectedList.innerHTML = state.selected.map((movie, index) => {
            const image = getImage(movie);
            const disabledClass = movie.enabled === false ? " is-disabled" : "";
            return `
                <article class="admin-selected-item${disabledClass}" data-admin-selected-index="${index}">
                    <button class="admin-drag-handle" type="button" data-admin-drag-handle aria-label="K\u00e9o s\u1eafp x\u1ebfp">
                        <i class="fa-solid fa-grip-vertical" aria-hidden="true"></i>
                    </button>
                    <span class="admin-selected-order">${index + 1}</span>
                    <div class="admin-movie-thumb">
                        <img src="${escapeHtml(image)}" alt="${escapeHtml(movie.name)}" loading="lazy" decoding="async">
                        ${renderSourceChip(movie)}
                    </div>
                    <div class="admin-selected-copy">
                        <h3 title="${escapeHtml(movie.name)}">${escapeHtml(movie.name)}</h3>
                        ${movie.original_name ? `<p title="${escapeHtml(movie.original_name)}">${escapeHtml(movie.original_name)}</p>` : ""}
                        <div class="admin-movie-meta">${renderMovieMeta(movie)}</div>
                        <div class="admin-language-row">${renderLanguageBadges(movie)}</div>
                    </div>
                    <div class="admin-visibility-cell">
                        <span class="admin-visibility-dot${movie.enabled === false ? "" : " is-on"}" aria-hidden="true"></span>
                        <label class="admin-switch" title="B\u1eadt t\u1eaft hi\u1ec3n th\u1ecb">
                            <input type="checkbox" data-admin-action="toggle" data-index="${index}" ${movie.enabled === false ? "" : "checked"}>
                            <span></span>
                        </label>
                    </div>
                    <div class="admin-row-actions">
                        <button type="button" data-admin-action="up" data-index="${index}" ${index === 0 ? "disabled" : ""} aria-label="L\u00ean tr\u00ean">
                            <i class="fa-solid fa-chevron-up" aria-hidden="true"></i>
                        </button>
                        <button type="button" data-admin-action="down" data-index="${index}" ${index === state.selected.length - 1 ? "disabled" : ""} aria-label="Xu\u1ed1ng d\u01b0\u1edbi">
                            <i class="fa-solid fa-chevron-down" aria-hidden="true"></i>
                        </button>
                        <button type="button" class="admin-danger-btn" data-admin-action="remove" data-index="${index}" aria-label="X\u00f3a phim">
                            <i class="fa-solid fa-trash" aria-hidden="true"></i>
                        </button>
                    </div>
                </article>
            `;
        }).join("");
    }

    async function loadSelectedMovies() {
        try {
            setSaveState("saving", "\u0110ang t\u1ea3i");
            const payload = await fetchJson(MANAGED_API_URL);
            state.selected = dedupeMovies(getItems(payload).map((movie) => normalizeMovie(movie, movie.source)));
            normalizeOrder();
            renderSelected();
            renderResults();
            setSaveState("saved", "\u0110\u00e3 s\u1eb5n s\u00e0ng");
        } catch (error) {
            console.error("Cannot load managed movies", error);
            setSaveState("error", "L\u1ed7i t\u1ea3i d\u1eef li\u1ec7u");
            notify("Kh\u00f4ng th\u1ec3 t\u1ea3i danh s\u00e1ch qu\u1ea3n l\u00fd.", "error");
        }
    }

    async function searchMovies(keyword) {
        const trimmedKeyword = text(keyword);
        if (!trimmedKeyword) {
            state.hasSearched = false;
            state.results = [];
            renderResults();
            notify("Nh\u1eadp t\u00ean phim tr\u01b0\u1edbc khi t\u00ecm.", "error");
            return;
        }

        const sources = state.source === "all" ? ["kk", "ophim"] : [state.source];
        state.hasSearched = true;
        els.resultCount.textContent = "\u0110ang t\u00ecm...";
        els.results.innerHTML = `
            <div class="admin-empty-state">
                <i class="fa-solid fa-spinner fa-spin" aria-hidden="true"></i>
                <span>\u0110ang t\u00ecm phim</span>
            </div>
        `;

        const requests = sources.map((source) => {
            const params = new URLSearchParams({ keyword: trimmedKeyword, page: "1" });
            return fetchJson(`${API_SEARCH_URLS[source]}?${params.toString()}`)
                .then((payload) => getItems(payload).map((movie) => normalizeMovie(movie, source)));
        });

        const settled = await Promise.allSettled(requests);
        const movies = settled.flatMap((result) => result.status === "fulfilled" ? result.value : []);
        const sortedMovies = typeof window.sortSearchResults === "function"
            ? window.sortSearchResults(movies)
            : movies;

        state.results = dedupeMovies(sortedMovies).slice(0, 24);
        renderResults();

        const hasError = settled.some((result) => result.status === "rejected");
        if (hasError && !state.results.length) {
            notify("API t\u00ecm ki\u1ebfm \u0111ang l\u1ed7i.", "error");
        }
    }

    function queueSave(message = "\u0110\u00e3 l\u01b0u") {
        setSaveState("saving", "\u0110ang l\u01b0u");
        state.saveQueue = state.saveQueue
            .catch(() => {})
            .then(async () => {
                normalizeOrder();
                const payload = state.selected.map((movie) => ({ ...movie }));
                const response = await fetchJson(MANAGED_API_URL, {
                    method: "PUT",
                    body: JSON.stringify(payload)
                });
                state.selected = dedupeMovies(getItems(response).map((movie) => normalizeMovie(movie, movie.source)));
                normalizeOrder();
                renderSelected();
                renderResults();
                setSaveState("saved", "\u0110\u00e3 l\u01b0u");
                notify(message);
            })
            .catch((error) => {
                console.error("Cannot save managed movies", error);
                setSaveState("error", "L\u1ed7i l\u01b0u");
                notify("Kh\u00f4ng th\u1ec3 l\u01b0u danh s\u00e1ch phim.", "error");
            });
    }

    function addMovie(index) {
        const movie = state.results[index];
        if (!movie || isSelected(movie)) {
            return;
        }

        state.selected.push({
            ...movie,
            enabled: true,
            order: state.selected.length + 1,
            added_at: new Date().toISOString()
        });
        renderSelected();
        renderResults();
        queueSave("\u0110\u00e3 th\u00eam phim.");
    }

    function removeMovie(index) {
        if (!state.selected[index]) {
            return;
        }
        state.selected.splice(index, 1);
        renderSelected();
        renderResults();
        queueSave("\u0110\u00e3 x\u00f3a phim.");
    }

    function moveMovie(fromIndex, toIndex) {
        if (fromIndex === toIndex || !state.selected[fromIndex] || toIndex < 0 || toIndex >= state.selected.length) {
            return;
        }
        const [movie] = state.selected.splice(fromIndex, 1);
        state.selected.splice(toIndex, 0, movie);
        renderSelected();
        queueSave("\u0110\u00e3 l\u01b0u th\u1ee9 t\u1ef1.");
    }

    function toggleMovie(index, enabled) {
        if (!state.selected[index]) {
            return;
        }
        state.selected[index].enabled = enabled;
        renderSelected();
        queueSave("\u0110\u00e3 c\u1eadp nh\u1eadt hi\u1ec3n th\u1ecb.");
    }

    function getSelectedItems() {
        return Array.from(els.selectedList?.querySelectorAll("[data-admin-selected-index]") || []);
    }

    function clearDragClasses() {
        getSelectedItems().forEach((item) => {
            item.classList.remove("is-dragging", "is-drop-target");
        });
        document.body.classList.remove("is-admin-dragging");
    }

    function getPointerInsertionIndex(clientY, fromIndex) {
        const items = getSelectedItems()
            .map((item) => ({
                item,
                index: Number.parseInt(item.dataset.adminSelectedIndex, 10)
            }))
            .filter(({ index }) => Number.isFinite(index) && index !== fromIndex);

        for (let position = 0; position < items.length; position += 1) {
            const rect = items[position].item.getBoundingClientRect();
            if (clientY < rect.top + rect.height / 2) {
                return position;
            }
        }

        return items.length;
    }

    function syncPointerDrag(clientY) {
        const drag = state.pointerDrag;
        if (!drag) {
            return;
        }

        drag.toIndex = getPointerInsertionIndex(clientY, drag.fromIndex);
        const items = getSelectedItems();
        const dropItems = items.filter((item) => (
            Number.parseInt(item.dataset.adminSelectedIndex, 10) !== drag.fromIndex
        ));
        const dropTarget = dropItems[Math.min(drag.toIndex, dropItems.length - 1)];
        items.forEach((item) => {
            const index = Number.parseInt(item.dataset.adminSelectedIndex, 10);
            item.classList.toggle("is-dragging", index === drag.fromIndex);
            item.classList.toggle("is-drop-target", item === dropTarget);
        });
    }

    function finishPointerDrag(event, shouldMove = true) {
        const drag = state.pointerDrag;
        if (!drag || drag.pointerId !== event.pointerId) {
            return;
        }

        event.preventDefault();
        const { fromIndex, toIndex, hasMoved } = drag;
        state.pointerDrag = null;
        clearDragClasses();

        if (shouldMove && hasMoved && fromIndex !== toIndex) {
            moveMovie(fromIndex, toIndex);
        }
    }

    function bindEvents() {
        els.searchForm?.addEventListener("submit", (event) => {
            event.preventDefault();
            searchMovies(els.searchInput?.value);
        });

        els.sourceButtons.forEach((button) => {
            button.addEventListener("click", () => {
                state.source = button.dataset.adminSource || "all";
                els.sourceButtons.forEach((item) => item.classList.toggle("is-active", item === button));
                if (text(els.searchInput?.value)) {
                    searchMovies(els.searchInput.value);
                }
            });
        });

        els.results?.addEventListener("click", (event) => {
            const button = event.target.closest("[data-admin-add]");
            if (!button) {
                return;
            }
            addMovie(Number.parseInt(button.dataset.adminAdd, 10));
        });

        els.selectedList?.addEventListener("click", (event) => {
            const button = event.target.closest("[data-admin-action]");
            if (!button) {
                return;
            }

            const index = Number.parseInt(button.dataset.index, 10);
            const action = button.dataset.adminAction;
            if (action === "remove") {
                removeMovie(index);
            } else if (action === "up") {
                moveMovie(index, index - 1);
            } else if (action === "down") {
                moveMovie(index, index + 1);
            }
        });

        els.selectedList?.addEventListener("change", (event) => {
            const input = event.target.closest("[data-admin-action='toggle']");
            if (!input) {
                return;
            }
            toggleMovie(Number.parseInt(input.dataset.index, 10), input.checked);
        });

        els.selectedList?.addEventListener("pointerdown", (event) => {
            const handle = event.target.closest("[data-admin-drag-handle]");
            const item = handle?.closest("[data-admin-selected-index]");
            if (!handle || !item || event.button !== 0) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();
            const fromIndex = Number.parseInt(item.dataset.adminSelectedIndex, 10);
            if (!Number.isFinite(fromIndex)) {
                return;
            }

            state.pointerDrag = {
                fromIndex,
                toIndex: fromIndex,
                pointerId: event.pointerId,
                startY: event.clientY,
                hasMoved: false
            };
            handle.setPointerCapture?.(event.pointerId);
            document.body.classList.add("is-admin-dragging");
            item.classList.add("is-dragging");
        });

        els.selectedList?.addEventListener("pointermove", (event) => {
            const drag = state.pointerDrag;
            if (!drag || drag.pointerId !== event.pointerId) {
                return;
            }

            event.preventDefault();
            if (Math.abs(event.clientY - drag.startY) > 4) {
                drag.hasMoved = true;
            }
            syncPointerDrag(event.clientY);
        });

        els.selectedList?.addEventListener("pointerup", (event) => {
            finishPointerDrag(event);
        });

        els.selectedList?.addEventListener("pointercancel", (event) => {
            finishPointerDrag(event, false);
        });

        document.addEventListener("pointerup", (event) => {
            finishPointerDrag(event);
        });

        els.saveOrder?.addEventListener("click", () => {
            queueSave("\u0110\u00e3 l\u01b0u danh s\u00e1ch.");
        });
    }

    bindEvents();
    loadSelectedMovies();
})();
