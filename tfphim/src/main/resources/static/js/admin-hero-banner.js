(() => {
    const page = document.querySelector("[data-admin-hero-page]");
    if (!page) {
        return;
    }

    const HERO_API_URL = "/api/quan-ly-phim/hero-banner";
    const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original";
    const MAX_HERO_ITEMS = 6;
    const MAX_SEARCH_RESULTS = 72;
    const API_SEARCH_URLS = {
        kk: "/api/movies/search",
        ophim: "/api/movies/ophim/search"
    };

    const state = {
        heroes: [],
        results: [],
        source: "all",
        hasSearched: false,
        saveQueue: Promise.resolve()
    };

    const els = {
        searchForm: document.querySelector("[data-admin-hero-search-form]"),
        searchInput: document.getElementById("adminHeroMovieSearch"),
        resultCount: document.querySelector("[data-admin-hero-result-count]"),
        results: document.querySelector("[data-admin-hero-results]"),
        editor: document.querySelector("[data-admin-hero-editor]"),
        saveState: document.querySelector("[data-admin-hero-save-state]"),
        saveButton: document.querySelector("[data-admin-hero-save]"),
        heroCounts: Array.from(document.querySelectorAll("[data-admin-hero-count]")),
        sourceInput: document.querySelector("[data-admin-hero-source]"),
        sourceWidget: document.querySelector("[data-admin-hero-source-widget]"),
        sourceTrigger: document.querySelector("[data-admin-hero-source-trigger]"),
        sourceLabel: document.querySelector("[data-admin-hero-source-label]"),
        sourceOptions: Array.from(document.querySelectorAll("[data-admin-hero-source-option]"))
    };

    const SOURCE_LABELS = {
        all: "T\u1ea5t c\u1ea3 ngu\u1ed3n",
        kk: "KK",
        ophim: "OPhim"
    };

    function notify(message, type = "success") {
        if (typeof window.showToast === "function") {
            window.showToast(message, type);
            return;
        }
        console[type === "error" ? "error" : "log"](message);
    }

    function refreshIcons() {
        if (window.lucide?.createIcons) {
            window.lucide.createIcons();
        }
    }

    function setSaveState(status, label) {
        if (!els.saveState) {
            return;
        }

        const icon = status === "saving"
            ? "loader-circle"
            : status === "error"
                ? "triangle-alert"
                : "circle-check";
        els.saveState.className = `admin-save-status is-${status}`;
        els.saveState.innerHTML = `<i data-lucide="${icon}"${status === "saving" ? " class=\"admin-icon-spin\"" : ""} aria-hidden="true"></i><span>${escapeHtml(label)}</span>`;
        refreshIcons();
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

    function resolveImageUrl(url, source) {
        const raw = text(url);
        if (!raw) {
            return "";
        }
        if (typeof window.resolveMovieImageUrl === "function") {
            return text(window.resolveMovieImageUrl(raw, source));
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        if (raw.startsWith("//")) {
            return `https:${raw}`;
        }

        const normalizedPath = raw.startsWith("/") ? raw.slice(1) : raw;
        if (normalizedPath.startsWith("uploads/movies/")) {
            return `${normalizeSource(source) === "ophim" ? "https://img.ophim.live/" : "https://img.phimapi.com/"}${normalizedPath}`;
        }
        if (normalizedPath.startsWith("upload/")) {
            return `https://img.phimapi.com/${normalizedPath}`;
        }
        if (/\.(jpe?g|png|webp|avif)$/i.test(normalizedPath)) {
            return `${normalizeSource(source) === "ophim" ? "https://img.ophim.live/uploads/movies/" : "https://img.phimapi.com/uploads/movies/"}${normalizedPath}`;
        }
        return raw;
    }

    function resolveTmdbAssetUrl(url, source = "") {
        const raw = text(url);
        if (!raw || raw.toLowerCase() === "null" || raw === "[object Object]") {
            return "";
        }
        if (raw.startsWith("/") && !raw.startsWith("/uploads/") && /\.(jpe?g|png|webp|avif)$/i.test(raw)) {
            return `${TMDB_IMAGE_BASE_URL}${raw}`;
        }
        return resolveImageUrl(raw, source);
    }

    function getImage(movie) {
        if (typeof window.getMovieImage === "function") {
            return window.getMovieImage(movie);
        }
        return resolveImageUrl(movie?.card_image_url || movie?.poster_url || movie?.thumb_url, movie?.source)
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

    function shouldSwapOphimImages(source, posterUrl, thumbUrl, isApiPayload) {
        if (source !== "ophim") {
            return false;
        }
        if (isApiPayload) {
            return true;
        }

        const posterPath = posterUrl.toLowerCase();
        const thumbPath = thumbUrl.toLowerCase();
        return posterPath.includes("-poster.") && thumbPath.includes("-thumb.");
    }

    function normalizeMovie(movie, source = "", isApiPayload = false) {
        const normalizedSource = normalizeSource(movie?.source || source);
        const originalName = getOriginalName(movie);
        const episode = text(movie?.episode_current || movie?.current_episode);
        const language = text(movie?.lang || movie?.language);
        const name = text(movie?.name || movie?.title || movie?.slug);
        const apiPosterUrl = resolveImageUrl(movie?.poster_url, normalizedSource);
        const apiThumbUrl = resolveImageUrl(movie?.thumb_url, normalizedSource);
        const swapOphimImages = shouldSwapOphimImages(normalizedSource, apiPosterUrl, apiThumbUrl, isApiPayload);
        const posterUrl = swapOphimImages ? (apiThumbUrl || apiPosterUrl) : apiPosterUrl;
        const thumbUrl = swapOphimImages ? (apiPosterUrl || apiThumbUrl) : apiThumbUrl;
        const cardImageUrl = resolveImageUrl(movie?.card_image_url, normalizedSource)
            || (normalizedSource === "ophim" ? (posterUrl || thumbUrl) : getImage({ ...movie, source: normalizedSource, poster_url: posterUrl, thumb_url: thumbUrl }));

        return {
            slug: text(movie?.slug),
            source: normalizedSource,
            name,
            origin_name: originalName,
            original_name: originalName,
            poster_url: posterUrl,
            thumb_url: thumbUrl,
            card_image_url: cardImageUrl,
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
            tmdb_thumb_url: text(movie?.tmdb_thumb_url || movie?.tmdb_backdrop_url || movie?.backdrop_url),
            tmdb_logo_url: text(movie?.tmdb_logo_url || movie?.logo_url || movie?.clear_logo || movie?.clearlogo),
            enabled: movie?.enabled !== false,
            order: Number.parseInt(movie?.order, 10) || 0,
            added_at: text(movie?.added_at) || new Date().toISOString(),
            updated_at: text(movie?.updated_at)
        };
    }

    function movieKey(movie) {
        return `${normalizeSource(movie?.source)}|${text(movie?.slug).toLowerCase()}`;
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

    function normalizeHeroOrder() {
        state.heroes = dedupeMovies(state.heroes)
            .slice(0, MAX_HERO_ITEMS)
            .map((movie, index) => ({
                ...movie,
                enabled: true,
                order: index + 1
            }));
    }

    function syncHeroCount() {
        const label = `${state.heroes.length}/${MAX_HERO_ITEMS}`;
        els.heroCounts.forEach((element) => {
            element.textContent = label;
        });
    }

    function isHeroSelected(movie) {
        const key = movieKey(movie);
        return state.heroes.some((hero) => movieKey(hero) === key);
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

    function renderResultStats(movie) {
        const rating = movie.movie_tmdb_rating || movie.movie_imdb_rating || movie.movie_rating;
        return [
            rating ? `<span><i data-lucide="star" aria-hidden="true"></i>${escapeHtml(rating)}</span>` : "",
            movie.year ? `<span>${escapeHtml(movie.year)}</span>` : "",
            movie.time ? `<span>${escapeHtml(movie.time)}</span>` : ""
        ].filter(Boolean).join("");
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

        syncHeroCount();
        els.resultCount.textContent = state.results.length
            ? `${state.results.length} k\u1ebft qu\u1ea3`
            : state.hasSearched
                ? "Kh\u00f4ng c\u00f3 k\u1ebft qu\u1ea3"
                : "Ch\u01b0a c\u00f3 k\u1ebft qu\u1ea3";

        if (!state.results.length) {
            els.results.innerHTML = `
                <div class="admin-empty-state">
                    <i data-lucide="film" aria-hidden="true"></i>
                    <span>${state.hasSearched ? "Kh\u00f4ng c\u00f3 phim ph\u00f9 h\u1ee3p" : "T\u00ecm phim t\u1eeb API"}</span>
                </div>
            `;
            refreshIcons();
            return;
        }

        const isFull = state.heroes.length >= MAX_HERO_ITEMS;
        els.results.innerHTML = state.results.map((movie, index) => {
            const selected = isHeroSelected(movie);
            const disabled = selected || isFull;
            const image = getImage(movie);
            const icon = selected ? "badge-check" : isFull ? "ban" : "image-plus";
            const label = selected ? "\u0110\u00e3 ch\u1ecdn" : isFull ? "T\u1ed1i \u0111a 6" : "Ch\u1ecdn hero";
            return `
                <article class="admin-result-card${selected ? " is-selected" : ""}">
                    <div class="admin-movie-thumb">
                        <img src="${escapeHtml(image)}" alt="${escapeHtml(movie.name)}" loading="lazy" decoding="async">
                        ${renderSourceChip(movie)}
                    </div>
                    <div class="admin-result-copy">
                        <h3 title="${escapeHtml(movie.name)}">${escapeHtml(movie.name)}</h3>
                        ${movie.original_name ? `<p title="${escapeHtml(movie.original_name)}">${escapeHtml(movie.original_name)}</p>` : ""}
                        <div class="admin-result-stats">${renderResultStats(movie)}</div>
                        <div class="admin-language-row">${renderLanguageBadges(movie)}</div>
                    </div>
                    <button class="admin-select-btn" type="button" data-admin-hero-pick="${index}" ${disabled ? "disabled" : ""}>
                        <i data-lucide="${icon}" aria-hidden="true"></i>
                        <span>${label}</span>
                    </button>
                </article>
            `;
        }).join("");
        refreshIcons();
    }

    function getHeroBackdrop(movie) {
        return resolveTmdbAssetUrl(movie?.tmdb_thumb_url, movie?.source)
            || resolveImageUrl(movie?.thumb_url, movie?.source)
            || resolveImageUrl(movie?.poster_url, movie?.source)
            || "https://via.placeholder.com/1280x720?text=Hero";
    }

    function getHeroLogo(movie) {
        return resolveTmdbAssetUrl(movie?.tmdb_logo_url, movie?.source);
    }

    function syncHeroPreview(index) {
        const movie = state.heroes[index];
        const preview = els.editor?.querySelector(`[data-admin-hero-preview][data-admin-hero-index="${index}"]`);
        if (!preview || !movie) {
            return;
        }

        const backdrop = getHeroBackdrop(movie).replace(/["\\]/g, "\\$&");
        preview.style.backgroundImage = `
            linear-gradient(90deg, rgba(8, 11, 16, 0.92), rgba(8, 11, 16, 0.22)),
            url("${backdrop}")
        `;

        const logoImage = preview.querySelector("[data-admin-hero-logo]");
        const fallbackTitle = preview.querySelector("[data-admin-hero-fallback-title]");
        const logoUrl = getHeroLogo(movie);
        if (!logoImage || !fallbackTitle) {
            return;
        }

        if (logoUrl) {
            logoImage.hidden = false;
            logoImage.src = logoUrl;
            logoImage.onerror = () => {
                logoImage.hidden = true;
                fallbackTitle.hidden = false;
            };
            fallbackTitle.hidden = true;
        } else {
            logoImage.hidden = true;
            fallbackTitle.hidden = false;
        }
    }

    function syncHeroPreviews() {
        state.heroes.forEach((movie, index) => syncHeroPreview(index));
    }

    function renderHeroCard(movie, index) {
        const name = movie.name || movie.slug;
        const originalName = movie.original_name || movie.origin_name || "";
        const meta = [movie.year, movie.quality, movie.episode_current || movie.current_episode]
            .map(text)
            .filter(Boolean)
            .join(" - ");

        return `
            <article class="admin-hero-card" data-admin-hero-index="${index}">
                <div class="admin-hero-preview" data-admin-hero-preview data-admin-hero-index="${index}">
                    <span class="admin-hero-order">${index + 1}</span>
                    <span class="admin-hero-preview__content">
                        <img class="admin-hero-logo" data-admin-hero-logo alt="${escapeHtml(name)}" hidden>
                        <strong class="admin-hero-preview__title" data-admin-hero-fallback-title>${escapeHtml(name)}</strong>
                        ${meta ? `<span class="admin-hero-preview__meta">${escapeHtml(meta)}</span>` : ""}
                    </span>
                </div>
                <div class="admin-hero-form">
                    <div class="admin-hero-selected">
                        <div class="admin-movie-thumb">
                            <img src="${escapeHtml(getImage(movie))}" alt="${escapeHtml(name)}" loading="lazy" decoding="async">
                            ${renderSourceChip(movie)}
                        </div>
                        <div>
                            <h3 title="${escapeHtml(name)}">${escapeHtml(name)}</h3>
                            ${originalName ? `<p title="${escapeHtml(originalName)}">${escapeHtml(originalName)}</p>` : ""}
                        </div>
                        <a class="admin-preview-link" href="${movie.slug ? `/phim/${encodeURIComponent(movie.slug)}` : "#"}">
                            <i data-lucide="external-link" aria-hidden="true"></i>
                            <span>Chi ti\u1ebft</span>
                        </a>
                    </div>

                    <div class="admin-hero-fields">
                        <label class="admin-hero-field">
                            <span>TMDB thumb / backdrop URL</span>
                            <input type="url"
                                   inputmode="url"
                                   autocomplete="off"
                                   spellcheck="false"
                                   data-admin-hero-field="tmdb_thumb_url"
                                   data-admin-hero-index="${index}"
                                   value="${escapeHtml(movie.tmdb_thumb_url || "")}"
                                   placeholder="https://image.tmdb.org/t/p/original/...">
                        </label>
                        <label class="admin-hero-field">
                            <span>TMDB logo URL</span>
                            <input type="url"
                                   inputmode="url"
                                   autocomplete="off"
                                   spellcheck="false"
                                   data-admin-hero-field="tmdb_logo_url"
                                   data-admin-hero-index="${index}"
                                   value="${escapeHtml(movie.tmdb_logo_url || "")}"
                                   placeholder="https://image.tmdb.org/t/p/original/...">
                        </label>
                    </div>

                    <div class="admin-hero-actions">
                        <button class="admin-action-btn" type="button" data-admin-hero-action="up" data-admin-hero-index="${index}" ${index === 0 ? "disabled" : ""} aria-label="L\u00ean tr\u00ean">
                            <i data-lucide="chevron-up" aria-hidden="true"></i>
                        </button>
                        <button class="admin-action-btn" type="button" data-admin-hero-action="down" data-admin-hero-index="${index}" ${index === state.heroes.length - 1 ? "disabled" : ""} aria-label="Xu\u1ed1ng d\u01b0\u1edbi">
                            <i data-lucide="chevron-down" aria-hidden="true"></i>
                        </button>
                        <button class="admin-action-btn admin-action-btn--save" type="button" data-admin-hero-action="save" data-admin-hero-index="${index}">
                            <i data-lucide="save" aria-hidden="true"></i>
                            <span>L\u01b0u</span>
                        </button>
                        <button class="admin-action-btn admin-action-btn--danger" type="button" data-admin-hero-action="remove" data-admin-hero-index="${index}">
                            <i data-lucide="trash-2" aria-hidden="true"></i>
                            <span>X\u00f3a</span>
                        </button>
                    </div>
                </div>
            </article>
        `;
    }

    function renderHero() {
        if (!els.editor) {
            return;
        }

        normalizeHeroOrder();
        syncHeroCount();
        if (!state.heroes.length) {
            els.editor.innerHTML = `
                <div class="admin-empty-state">
                    <i data-lucide="image-plus" aria-hidden="true"></i>
                    <span>Ch\u01b0a ch\u1ecdn hero banner</span>
                </div>
            `;
            refreshIcons();
            return;
        }

        els.editor.innerHTML = `
            <div class="admin-hero-list">
                ${state.heroes.map(renderHeroCard).join("")}
            </div>
        `;
        syncHeroPreviews();
        refreshIcons();
    }

    function applyHeroPayload(payload) {
        state.heroes = payload?.enabled === false
            ? []
            : getItems(payload).map((movie) => normalizeMovie(movie, movie.source));
        normalizeHeroOrder();
    }

    async function loadHero() {
        try {
            setSaveState("saving", "\u0110ang t\u1ea3i");
            const payload = await fetchJson(HERO_API_URL);
            applyHeroPayload(payload);
            renderHero();
            renderResults();
            setSaveState("saved", "\u0110\u00e3 s\u1eb5n s\u00e0ng");
        } catch (error) {
            console.error("Cannot load hero banner", error);
            setSaveState("error", "L\u1ed7i t\u1ea3i d\u1eef li\u1ec7u");
            notify("Kh\u00f4ng th\u1ec3 t\u1ea3i hero banner.", "error");
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
                <i data-lucide="loader-circle" class="admin-icon-spin" aria-hidden="true"></i>
                <span>\u0110ang t\u00ecm phim</span>
            </div>
        `;
        refreshIcons();

        const requests = sources.map((source) => {
            const params = new URLSearchParams({ keyword: trimmedKeyword, page: "1" });
            return fetchJson(`${API_SEARCH_URLS[source]}?${params.toString()}`)
                .then((payload) => getItems(payload).map((movie) => normalizeMovie(movie, source, true)));
        });

        const settled = await Promise.allSettled(requests);
        const movies = settled.flatMap((result) => result.status === "fulfilled" ? result.value : []);
        const sortedMovies = typeof window.sortSearchResults === "function"
            ? window.sortSearchResults(movies)
            : movies;

        state.results = dedupeMovies(sortedMovies).slice(0, MAX_SEARCH_RESULTS);
        renderResults();

        const hasError = settled.some((result) => result.status === "rejected");
        if (hasError && !state.results.length) {
            notify("API t\u00ecm ki\u1ebfm \u0111ang l\u1ed7i.", "error");
        }
    }

    function queueSaveHero(message = "\u0110\u00e3 l\u01b0u hero banner.") {
        setSaveState("saving", "\u0110ang l\u01b0u");
        state.saveQueue = state.saveQueue
            .catch(() => {})
            .then(async () => {
                normalizeHeroOrder();
                const response = await fetchJson(HERO_API_URL, {
                    method: "PUT",
                    body: JSON.stringify({
                        enabled: state.heroes.length > 0,
                        items: state.heroes.map((movie, index) => ({
                            ...movie,
                            enabled: true,
                            order: index + 1
                        }))
                    })
                });
                applyHeroPayload(response);
                renderHero();
                renderResults();
                setSaveState("saved", "\u0110\u00e3 l\u01b0u");
                notify(message);
            })
            .catch((error) => {
                console.error("Cannot save hero banner", error);
                setSaveState("error", "L\u1ed7i l\u01b0u");
                notify("Kh\u00f4ng th\u1ec3 l\u01b0u hero banner.", "error");
            });
    }

    function addHeroMovie(index) {
        const movie = state.results[index];
        if (!movie || isHeroSelected(movie)) {
            return;
        }
        if (state.heroes.length >= MAX_HERO_ITEMS) {
            notify("Ch\u1ec9 ch\u1ecdn t\u1ed1i \u0111a 6 phim cho hero banner.", "error");
            renderResults();
            return;
        }

        state.heroes.push(normalizeMovie({
            ...movie,
            tmdb_thumb_url: "",
            tmdb_logo_url: "",
            enabled: true,
            order: state.heroes.length + 1,
            added_at: new Date().toISOString()
        }, movie.source));
        normalizeHeroOrder();
        renderHero();
        renderResults();
        queueSaveHero("\u0110\u00e3 th\u00eam phim v\u00e0o hero banner.");
    }

    function removeHeroMovie(index) {
        if (!state.heroes[index]) {
            return;
        }
        state.heroes.splice(index, 1);
        normalizeHeroOrder();
        renderHero();
        renderResults();
        queueSaveHero("\u0110\u00e3 x\u00f3a phim kh\u1ecfi hero banner.");
    }

    function moveHeroMovie(fromIndex, toIndex) {
        if (fromIndex === toIndex || !state.heroes[fromIndex] || toIndex < 0 || toIndex >= state.heroes.length) {
            return;
        }
        const [movie] = state.heroes.splice(fromIndex, 1);
        state.heroes.splice(toIndex, 0, movie);
        normalizeHeroOrder();
        renderHero();
        renderResults();
        queueSaveHero("\u0110\u00e3 l\u01b0u th\u1ee9 t\u1ef1 hero banner.");
    }

    function updateHeroField(input) {
        const index = Number.parseInt(input?.dataset?.adminHeroIndex, 10);
        const field = input?.dataset?.adminHeroField;
        if (!Number.isFinite(index) || !field || !state.heroes[index]) {
            return;
        }

        state.heroes[index][field] = text(input.value);
        syncHeroPreview(index);
    }

    function closeSourceMenu() {
        els.sourceWidget?.classList.remove("is-open");
        els.sourceTrigger?.setAttribute("aria-expanded", "false");
    }

    function setSource(value, shouldSearch = false) {
        const source = Object.prototype.hasOwnProperty.call(SOURCE_LABELS, value) ? value : "all";
        state.source = source;
        if (els.sourceInput) {
            els.sourceInput.value = source;
        }
        if (els.sourceLabel) {
            els.sourceLabel.textContent = SOURCE_LABELS[source];
        }
        els.sourceOptions.forEach((option) => {
            const isSelected = option.dataset.adminHeroSourceOption === source;
            option.classList.toggle("is-selected", isSelected);
            option.setAttribute("aria-selected", String(isSelected));
        });
        if (shouldSearch && text(els.searchInput?.value)) {
            searchMovies(els.searchInput.value);
        }
    }

    function bindEvents() {
        els.searchForm?.addEventListener("submit", (event) => {
            event.preventDefault();
            state.source = els.sourceInput?.value || "all";
            searchMovies(els.searchInput?.value);
        });

        els.sourceTrigger?.addEventListener("click", () => {
            const isOpen = els.sourceWidget?.classList.toggle("is-open");
            els.sourceTrigger.setAttribute("aria-expanded", String(Boolean(isOpen)));
        });

        els.sourceOptions.forEach((option) => {
            option.addEventListener("click", () => {
                setSource(option.dataset.adminHeroSourceOption || "all", true);
                closeSourceMenu();
                els.sourceTrigger?.focus();
            });
        });

        document.addEventListener("click", (event) => {
            if (!els.sourceWidget?.contains(event.target)) {
                closeSourceMenu();
            }
        });

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                closeSourceMenu();
            }
        });

        els.results?.addEventListener("click", (event) => {
            const button = event.target.closest("[data-admin-hero-pick]");
            if (!button) {
                return;
            }
            addHeroMovie(Number.parseInt(button.dataset.adminHeroPick, 10));
        });

        els.editor?.addEventListener("input", (event) => {
            const input = event.target.closest("[data-admin-hero-field]");
            if (!input) {
                return;
            }
            updateHeroField(input);
        });

        els.editor?.addEventListener("change", (event) => {
            const input = event.target.closest("[data-admin-hero-field]");
            if (!input) {
                return;
            }
            updateHeroField(input);
            queueSaveHero("\u0110\u00e3 c\u1eadp nh\u1eadt hero banner.");
        });

        els.editor?.addEventListener("click", (event) => {
            const button = event.target.closest("[data-admin-hero-action]");
            if (!button) {
                return;
            }

            const index = Number.parseInt(button.dataset.adminHeroIndex, 10);
            const action = button.dataset.adminHeroAction;
            if (action === "save") {
                queueSaveHero("\u0110\u00e3 l\u01b0u hero banner.");
            } else if (action === "remove") {
                removeHeroMovie(index);
            } else if (action === "up") {
                moveHeroMovie(index, index - 1);
            } else if (action === "down") {
                moveHeroMovie(index, index + 1);
            }
        });

        els.saveButton?.addEventListener("click", () => {
            queueSaveHero("\u0110\u00e3 l\u01b0u hero banner.");
        });
    }

    bindEvents();
    setSource(els.sourceInput?.value || state.source);
    loadHero();
})();
