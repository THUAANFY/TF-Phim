const API_BASE = "/api/movies";

const MOVIE_TYPES = {
    "phim-moi": "phim-moi",
    "phim-viet-nam": "quoc-gia/viet-nam",
    "phim-bo": "phim-bo",
    "phim-le": "phim-le"
};

function byId(id) {
    return document.getElementById(id);
}

async function getJson(url) {
    const response = await fetch(url, {
        headers: {
            "Accept": "application/json"
        }
    });

    if (!response.ok) {
        throw new Error(`Request failed: ${response.status}`);
    }

    return response.json();
}

async function requestJson(url, options = {}) {
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

    const contentType = response.headers.get("Content-Type") || "";
    if (!contentType.includes("application/json")) {
        return null;
    }

    return response.json();
}

function getToastRoot() {
    let root = document.querySelector("[data-toast-root]");
    if (root) {
        return root;
    }

    root = document.createElement("div");
    root.className = "toast-stack";
    root.dataset.toastRoot = "true";
    document.body.appendChild(root);
    return root;
}

function showToast(message, type = "success") {
    if (!message) {
        return;
    }

    const root = getToastRoot();
    const toast = document.createElement("div");
    toast.className = `app-toast app-toast--${type}`;
    toast.setAttribute("role", "status");
    toast.setAttribute("aria-live", "polite");
    toast.innerHTML = `
        <span class="app-toast__icon">
            <i class="fa-solid ${type === "error" ? "fa-xmark" : "fa-check"}"></i>
        </span>
        <span class="app-toast__message">${escapeHtml(message)}</span>
        <button class="app-toast__close" type="button" aria-label="Dong thong bao">
            <i class="fa-solid fa-xmark"></i>
        </button>
        <span class="app-toast__progress" aria-hidden="true"></span>
    `;

    root.appendChild(toast);
    window.requestAnimationFrame(() => toast.classList.add("is-visible"));

    const removeToast = () => {
        toast.classList.remove("is-visible");
        window.setTimeout(() => toast.remove(), 220);
    };

    toast.querySelector(".app-toast__close")?.addEventListener("click", removeToast);
    window.setTimeout(removeToast, 2600);
}

function getMovieItems(payload) {
    if (!payload) {
        return [];
    }

    if (Array.isArray(payload.items)) {
        return payload.items;
    }

    if (Array.isArray(payload.data?.items)) {
        return payload.data.items;
    }

    if (Array.isArray(payload.data)) {
        return payload.data;
    }

    return [];
}

async function fetchMovies(type, page = 1) {
    const apiType = MOVIE_TYPES[type] || type;
    const data = await getJson(`${API_BASE}?type=${encodeURIComponent(apiType)}&page=${page}`);
    return getMovieItems(data);
}

async function fetchMoviesWithLimit(type, limit = 12) {
    const safeLimit = Math.max(1, Number(limit) || 1);
    const collected = [];
    let page = 1;

    while (collected.length < safeLimit) {
        const items = await fetchMovies(type, page);
        if (!items.length) {
            break;
        }

        collected.push(...items);

        if (items.length < 10) {
            break;
        }

        page += 1;
    }

    return collected.slice(0, safeLimit);
}

async function searchMovies(keyword) {
    const data = await getJson(`${API_BASE}/search?keyword=${encodeURIComponent(keyword)}`);
    return getMovieItems(data);
}

async function fetchMovieDetail(slug) {
    const data = await getJson(`${API_BASE}/${encodeURIComponent(slug)}`);
    return extractMovieDetail(data);
}

function extractMovieDetail(payload) {
    if (!payload || typeof payload !== "object") {
        return {};
    }

    if (payload.movie && typeof payload.movie === "object" && !Array.isArray(payload.movie)) {
        return payload.movie;
    }

    if (payload.data?.movie && typeof payload.data.movie === "object" && !Array.isArray(payload.data.movie)) {
        return payload.data.movie;
    }

    if (payload.data && typeof payload.data === "object" && !Array.isArray(payload.data)) {
        return payload.data;
    }

    return {};
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function getLanguageBadges(language) {
    const normalized = String(language || "").toLowerCase();
    const badges = [];

    if (normalized.includes("vietsub")) {
        badges.push({ label: "P.Đề", className: "lang-sub" });
    }
    if (normalized.includes("thuyết minh") || normalized.includes("thuyet minh")) {
        badges.push({ label: "T.Minh", className: "lang-dub" });
    }
    if (normalized.includes("lồng tiếng") || normalized.includes("long tieng")) {
        badges.push({ label: "L.Tiếng", className: "lang-voice" });
    }

    return badges;
}

function getLanguageBadges(language) {
    const normalized = String(language || "").toLowerCase();
    const badges = [];

    if (normalized.includes("vietsub")) {
        badges.push({ label: "P.Đề", className: "lang-sub" });
    }
    if (normalized.includes("thuyet minh") || normalized.includes("thuyết minh")) {
        badges.push({ label: "T.Minh", className: "lang-dub" });
    }
    if (normalized.includes("long tieng") || normalized.includes("lồng tiếng")) {
        badges.push({ label: "L.Tiếng", className: "lang-voice" });
    }

    return badges;
}

function getMovieImage(movie) {
    return movie.poster_url
        || movie.thumb_url
        || "https://via.placeholder.com/600x900?text=No+Image";
}

function getMovieOriginalName(movie) {
    return movie.original_name || movie.origin_name || "";
}

function getMovieDescription(movie) {
    return movie.description
        || movie.content
        || movie.excerpt
        || "";
}

function getMovieYear(movie) {
    return movie.year
        || movie.release_year
        || movie.modified?.time?.slice?.(0, 4)
        || "";
}

function getMovieRating(movie) {
    const rating = movie.tmdb_vote_average || movie.imdb?.vote_average || movie.rating || "";
    return String(rating || "").trim();
}

function getMovieDuration(movie) {
    return movie.time || movie.runtime || movie.duration || "";
}

function getMovieAgeRating(movie) {
    const rating = movie.content_rating || movie.age_rating || movie.age || movie.mpaa || movie.quality_label || "";
    const normalized = String(rating || "").trim();
    return /^(T|K|C|P)\d*$/i.test(normalized) ? normalized.toUpperCase() : "";
}

function getMovieStatusLine(movie) {
    return movie.status
        || movie.episode_current
        || movie.current_episode
        || movie.lang
        || movie.language
        || "";
}

function createMovieDatasetAttributes(movie) {
    return [
        ["slug", movie.slug || ""],
        ["name", movie.name || ""],
        ["original-name", getMovieOriginalName(movie)],
        ["thumb", movie.thumb_url || ""],
        ["poster", movie.poster_url || ""],
        ["quality", movie.quality || ""],
        ["episode", movie.episode_current || movie.current_episode || ""],
        ["language", movie.language || ""],
        ["year", getMovieYear(movie)],
        ["description", getMovieDescription(movie)],
        ["time", getMovieDuration(movie)],
        ["status", getMovieStatusLine(movie)],
        ["rating", getMovieRating(movie)]
    ]
        .filter(([, value]) => String(value || "").trim() !== "")
        .map(([key, value]) => `data-${key}="${escapeHtml(value)}"`)
        .join(" ");
}

function readMovieDataFromCard(card) {
    return {
        slug: card.dataset.slug || "",
        name: card.dataset.name || "",
        original_name: card.dataset.originalName || "",
        thumb_url: card.dataset.thumb || "",
        poster_url: card.dataset.poster || "",
        quality: card.dataset.quality || "",
        episode_current: card.dataset.episode || "",
        current_episode: card.dataset.episode || "",
        language: card.dataset.language || "",
        year: card.dataset.year || "",
        description: card.dataset.description || "",
        time: card.dataset.time || "",
        status: card.dataset.status || "",
        tmdb_vote_average: card.dataset.rating || ""
    };
}

function mergeMovieData(baseMovie, detailMovie) {
    return {
        ...baseMovie,
        ...detailMovie,
        slug: detailMovie.slug || baseMovie.slug,
        name: detailMovie.name || baseMovie.name,
        original_name: getMovieOriginalName(detailMovie) || getMovieOriginalName(baseMovie),
        thumb_url: detailMovie.thumb_url || baseMovie.thumb_url,
        poster_url: detailMovie.poster_url || baseMovie.poster_url,
        quality: detailMovie.quality || baseMovie.quality,
        episode_current: detailMovie.episode_current || detailMovie.current_episode || baseMovie.episode_current,
        current_episode: detailMovie.current_episode || detailMovie.episode_current || baseMovie.current_episode,
        language: detailMovie.language || baseMovie.language,
        year: getMovieYear(detailMovie) || getMovieYear(baseMovie),
        description: getMovieDescription(detailMovie) || getMovieDescription(baseMovie),
        time: getMovieDuration(detailMovie) || getMovieDuration(baseMovie),
        status: getMovieStatusLine(detailMovie) || getMovieStatusLine(baseMovie),
        tmdb_vote_average: getMovieRating(detailMovie) || getMovieRating(baseMovie)
    };
}

const favoriteStore = {
    loaded: true,
    slugs: new Set(),
    pending: new Set()
};

function normalizeFavoritePayload(movie) {
    return {
        movieSlug: movie?.slug || "",
        movieName: movie?.name || "",
        originalName: movie?.original_name || movie?.origin_name || "",
        posterUrl: movie?.poster_url || "",
        thumbUrl: movie?.thumb_url || "",
        language: movie?.language || "",
        quality: movie?.quality || "",
        year: movie?.year || ""
    };
}

function isFavoriteSlug(slug) {
    return Boolean(slug) && favoriteStore.slugs.has(slug);
}

function isFavoritePending(slug) {
    return Boolean(slug) && favoriteStore.pending.has(slug);
}

function syncFavoriteButton(button, movie) {
    if (!button) {
        return;
    }

    const slug = movie?.slug || "";
    button.dataset.favoriteSlug = slug;
    button.classList.remove("is-active", "is-pending");
    button.disabled = true;
    button.setAttribute("aria-pressed", "false");
    button.setAttribute("aria-disabled", "true");
}

async function ensureFavoritesLoaded() {
    return favoriteStore.slugs;
}

async function toggleFavoriteMovie() {
    showToast("Tinh nang yeu thich da duoc go bo.", "error");
    return false;
}

function updateFavoriteCount() {
    document.querySelectorAll(".favorite-page .category-page-indicator").forEach((element) => {
        element.textContent = "0 phim";
    });

    const stat = document.querySelector(".favorite-page .category-stat strong");
    if (stat) {
        stat.textContent = "0";
    }
}

function syncFavoriteEmptyState() {
    document.querySelectorAll("[data-favorites-empty]").forEach((emptyState) => {
        emptyState.hidden = false;
    });
    updateFavoriteCount();
}

function bindFavoritePageActions() {
    const favoritePage = document.querySelector(".favorite-page");
    if (!favoritePage) {
        return;
    }

    syncFavoriteEmptyState();
    favoritePage.querySelectorAll("[data-favorite-remove='true']").forEach((button) => {
        button.disabled = true;
        button.setAttribute("aria-disabled", "true");
    });
}

function createMovieCard(movie) {
    const thumb = movie.thumb_url || movie.poster_url || "https://via.placeholder.com/200x300?text=No+Image";
    const name = movie.name || "Khong ro";
    const episode = movie.episode_current || movie.current_episode || "";
    const quality = movie.quality || "HD";
    const slug = movie.slug || "";
    const languageBadges = getLanguageBadges(movie.language);
    const href = slug ? `/phim/${encodeURIComponent(slug)}` : "#";
    const disabledClass = slug ? "" : " is-disabled";
    const datasetAttrs = createMovieDatasetAttributes(movie);

    return `
        <a class="movie-card-link${disabledClass}" href="${escapeHtml(href)}" ${slug ? "" : 'aria-disabled="true" tabindex="-1"'}>
            <article class="movie-card" ${datasetAttrs}>
                <div class="card-thumb">
                    <img src="${escapeHtml(thumb)}" alt="${escapeHtml(name)}" loading="lazy" decoding="async" fetchpriority="low"
                         onerror="this.src='https://via.placeholder.com/200x300?text=No+Image'">
                    <div class="overlay">
                        <div class="play-btn"><i class="fa-solid fa-play"></i></div>
                    </div>
                    <span class="badge hd">${escapeHtml(quality)}</span>
                    ${episode ? `<span class="badge ep" style="top:8px;left:auto;right:8px;">${escapeHtml(episode)}</span>` : ""}
                </div>
                <div class="card-info">
                    <div class="card-title" title="${escapeHtml(name)}">${escapeHtml(name)}</div>
                    ${languageBadges.length ? `
                        <div class="card-language-badges">
                            ${languageBadges.map((badge) => `<span class="mini-badge ${badge.className}">${escapeHtml(badge.label)}</span>`).join("")}
                        </div>
                    ` : ""}
                </div>
            </article>
        </a>
    `;
}

function getHeroDescription(movie) {
    return movie.description
        || movie.content
        || movie.origin_name
        || "Khám phá bộ phim đang được quan tâm nhất tuần này trên TF-Phim.";
}

function getHeroTags(movie) {
    const rawCategory = movie.category;
    if (Array.isArray(rawCategory)) {
        return rawCategory
            .map((item) => item?.name || item)
            .filter(Boolean)
            .slice(0, 4);
    }

    return [];
}

function createHeroMeta(movie) {
    const episode = movie.episode_current || movie.current_episode || "";
    const quality = movie.quality || "HD";
    const year = movie.year || movie.release_year || movie.modified?.time?.slice(0, 4) || "";
    const serverLabel = movie.lang || movie.language || "Phần 1";
    const rating = movie.tmdb_vote_average || movie.imdb?.vote_average || "0.0";

    return [
        `<span class="hero-meta-chip hero-meta-rating">IMDb ${escapeHtml(rating)}</span>`,
        quality ? `<span class="hero-meta-chip">${escapeHtml(quality)}</span>` : "",
        year ? `<span class="hero-meta-chip">${escapeHtml(year)}</span>` : "",
        serverLabel ? `<span class="hero-meta-chip">${escapeHtml(serverLabel)}</span>` : "",
        episode ? `<span class="hero-meta-chip">${escapeHtml(episode)}</span>` : ""
    ].filter(Boolean).join("");
}

function createHeroTags(movie) {
    return getHeroTags(movie)
        .map((tag) => `<span class="hero-tag-chip">${escapeHtml(tag)}</span>`)
        .join("");
}

function renderHeroThumbs(movies, activeIndex) {
    return movies.map((movie, index) => {
        const thumb = movie.thumb_url || movie.poster_url || "https://via.placeholder.com/160x90?text=No+Image";
        const name = movie.name || "Phim nổi bật";
        return `
            <button type="button" class="hero-thumb ${index === activeIndex ? "is-active" : ""}" data-hero-index="${index}" aria-label="${escapeHtml(name)}">
                <img src="${escapeHtml(thumb)}" alt="${escapeHtml(name)}" loading="lazy" decoding="async" fetchpriority="low">
            </button>
        `;
    }).join("");
}

async function loadHeroSlider() {
    const heroSlider = byId("heroSlider");
    if (!heroSlider) {
        return;
    }

    const heroBackdropLayers = [byId("heroBackdrop"), byId("heroBackdropAlt")].filter(Boolean);
    const heroTitle = byId("heroTitle");
    const heroOriginal = byId("heroOriginal");
    const heroMeta = byId("heroMeta");
    const heroTags = byId("heroTags");
    const heroDescription = byId("heroDescription");
    const heroPlayBtn = byId("heroPlayBtn");
    const heroInfoBtn = byId("heroInfoBtn");
    const heroLikeBtn = byId("heroLikeBtn");
    const heroThumbs = byId("heroThumbs");
    const heroCopy = byId("heroCopy");

    try {
        const items = (await fetchMovies("phim-moi")).slice(0, 6);
        if (!items.length) {
            return;
        }

        await ensureFavoritesLoaded();

        let activeIndex = 0;
        let autoRotateId = null;
        let activeBackdropIndex = 0;
        let renderToken = 0;
        let pointerStartX = 0;
        let pointerStartY = 0;
        let activePointerId = null;
        let pointerMoved = false;
        let swipedDuringPointer = false;
        const autoplayDelay = 20000;
        const contentRevealDelay = 500;

        const getBackdropStyle = (imageUrl) => `
            linear-gradient(90deg, rgba(12,14,20,0.96) 0%, rgba(12,14,20,0.54) 34%, rgba(12,14,20,0.16) 56%, rgba(12,14,20,0.88) 100%),
            radial-gradient(circle at center, rgba(255,255,255,0.14), transparent 42%),
            url("${imageUrl}")
        `;

        const getHeroImage = (movie) => (
            movie?.poster_url
            || movie?.thumb_url
            || "https://via.placeholder.com/1280x720?text=No+Image"
        );

        const updateHeroContent = (movie) => {
            if (!movie) {
                return;
            }

            const name = movie.name || "Phim noi bat";
            const originalName = movie.origin_name || movie.original_name || "TF-Phim Spotlight";

            if (heroTitle) {
                heroTitle.textContent = name;
            }
            if (heroOriginal) {
                heroOriginal.textContent = originalName;
            }
            if (heroMeta) {
                heroMeta.innerHTML = createHeroMeta(movie);
            }
            if (heroTags) {
                heroTags.innerHTML = createHeroTags(movie);
            }
            if (heroDescription) {
                heroDescription.textContent = getHeroDescription(movie);
            }
            if (heroPlayBtn) {
                heroPlayBtn.href = movie.slug ? `/xem/${movie.slug}` : "/";
            }
            if (heroInfoBtn) {
                heroInfoBtn.href = movie.slug ? `/phim/${movie.slug}` : "/";
            }
            if (heroLikeBtn) {
                syncFavoriteButton(heroLikeBtn, movie);
            }
            if (heroThumbs) {
                heroThumbs.innerHTML = renderHeroThumbs(items, activeIndex);
                heroThumbs.querySelectorAll("[data-hero-index]").forEach((button) => {
                    button.addEventListener("click", () => {
                        renderSlide(Number(button.dataset.heroIndex));
                        restartAutoRotate();
                    });
                });
            }
        };

        const syncBackdrop = (movie, immediate = false) => {
            if (!heroBackdropLayers.length) {
                return;
            }

            const backdrop = getHeroImage(movie);
            const nextBackdropIndex = immediate || heroBackdropLayers.length === 1
                ? activeBackdropIndex
                : (activeBackdropIndex + 1) % heroBackdropLayers.length;
            const nextLayer = heroBackdropLayers[nextBackdropIndex];

            nextLayer.style.backgroundImage = getBackdropStyle(backdrop);

            if (immediate || heroBackdropLayers.length === 1) {
                heroBackdropLayers.forEach((layer, layerIndex) => {
                    layer.classList.toggle("is-active", layerIndex === nextBackdropIndex);
                });
                activeBackdropIndex = nextBackdropIndex;
                return;
            }

            window.requestAnimationFrame(() => {
                heroBackdropLayers.forEach((layer, layerIndex) => {
                    layer.classList.toggle("is-active", layerIndex === nextBackdropIndex);
                });
                activeBackdropIndex = nextBackdropIndex;
            });
        };

        const renderSlide = (index, { immediate = false } = {}) => {
            const movie = items[index];
            if (!movie) {
                return;
            }

            activeIndex = index;
            renderToken += 1;
            const currentToken = renderToken;
            syncBackdrop(movie, immediate);

            if (!heroCopy || immediate) {
                updateHeroContent(movie);
                if (heroCopy) {
                    heroCopy.classList.remove("is-transitioning", "is-visible");
                    void heroCopy.offsetWidth;
                    heroCopy.classList.add("is-visible");
                }
                return;
            }

            heroCopy.classList.remove("is-visible");
            heroCopy.classList.add("is-transitioning");

            window.setTimeout(() => {
                if (currentToken !== renderToken) {
                    return;
                }

                updateHeroContent(movie);
                heroCopy.classList.remove("is-transitioning");
                void heroCopy.offsetWidth;
                heroCopy.classList.add("is-visible");
            }, contentRevealDelay);
        };

        const goToRelativeSlide = (direction) => {
            const nextIndex = (activeIndex + direction + items.length) % items.length;
            renderSlide(nextIndex);
            restartAutoRotate();
        };

        const openActiveMovieDetail = () => {
            const movie = items[activeIndex];
            if (!movie?.slug) {
                return;
            }

            window.location.href = `/phim/${movie.slug}`;
        };

        const startAutoRotate = () => {
            autoRotateId = window.setInterval(() => {
                renderSlide((activeIndex + 1) % items.length);
            }, autoplayDelay);
        };

        const restartAutoRotate = () => {
            if (autoRotateId) {
                window.clearInterval(autoRotateId);
            }
            startAutoRotate();
        };

        if (heroLikeBtn) {
            heroLikeBtn.addEventListener("click", async () => {
                const movie = items[activeIndex];
                try {
                    syncFavoriteButton(heroLikeBtn, movie);
                    const nextState = await toggleFavoriteMovie(movie);
                    heroLikeBtn.classList.toggle("is-active", nextState);
                } catch (error) {
                    console.error("Toggle hero favorite failed:", error);
                } finally {
                    syncFavoriteButton(heroLikeBtn, movie);
                }
            });
        }

        heroSlider.addEventListener("pointerdown", (event) => {
            if (event.pointerType === "mouse" && event.button !== 0) {
                return;
            }

            if (event.target.closest("a, button")) {
                return;
            }

            activePointerId = event.pointerId;
            pointerStartX = event.clientX;
            pointerStartY = event.clientY;
            pointerMoved = false;
            swipedDuringPointer = false;
            if (heroSlider.setPointerCapture) {
                heroSlider.setPointerCapture(event.pointerId);
            }
        });

        heroSlider.addEventListener("pointermove", (event) => {
            if (activePointerId !== event.pointerId) {
                return;
            }

            const deltaX = event.clientX - pointerStartX;
            const deltaY = event.clientY - pointerStartY;
            if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                pointerMoved = true;
            }
        });

        const finishSwipe = (event) => {
            if (activePointerId !== event.pointerId) {
                return;
            }

            const deltaX = event.clientX - pointerStartX;
            const deltaY = event.clientY - pointerStartY;
            const isHorizontalSwipe = Math.abs(deltaX) > 60 && Math.abs(deltaX) > Math.abs(deltaY) * 1.25;

            activePointerId = null;
            if (heroSlider.releasePointerCapture && heroSlider.hasPointerCapture?.(event.pointerId)) {
                heroSlider.releasePointerCapture(event.pointerId);
            }

            if (!pointerMoved || !isHorizontalSwipe) {
                return;
            }

            swipedDuringPointer = true;
            goToRelativeSlide(deltaX < 0 ? 1 : -1);
        };

        heroSlider.addEventListener("pointerup", finishSwipe);
        heroSlider.addEventListener("pointercancel", finishSwipe);
        heroSlider.addEventListener("pointerleave", finishSwipe);
        heroSlider.addEventListener("click", (event) => {
            if (swipedDuringPointer) {
                swipedDuringPointer = false;
                return;
            }

            if (event.target.closest("a, button, [data-hero-index]")) {
                return;
            }

            openActiveMovieDetail();
        });

        renderSlide(0, { immediate: true });
        startAutoRotate();
    } catch (error) {
        console.error("Load hero slider failed:", error);
    }
}

function renderMovies(containerId, movies, emptyMessage = "Khong tim thay phim.") {
    const grid = byId(containerId);
    if (!grid) {
        return;
    }

    if (!movies.length) {
        grid.innerHTML = `<div class="loading-spinner">${emptyMessage}</div>`;
        return;
    }

    grid.innerHTML = movies.map(createMovieCard).join("");
    grid.querySelectorAll(".movie-card-link.is-disabled").forEach((link) => {
        link.addEventListener("click", (event) => {
            event.preventDefault();
        });
    });
    bindMovieHoverPopup(grid);
}

function bindMovieHoverPopup(root = document) {
    const desktopQuery = window.matchMedia("(min-width: 1025px)");
    const popupId = "movieHoverPopup";
    const detailCache = bindMovieHoverPopup.detailCache || new Map();
    bindMovieHoverPopup.detailCache = detailCache;

    const clearTimer = (timerId) => {
        if (timerId) {
            window.clearTimeout(timerId);
        }
        return null;
    };

    if (!bindMovieHoverPopup.state) {
        let showTimer = null;
        let hideTimer = null;
        let activeCard = null;
        let popup = document.getElementById(popupId);

        if (!popup) {
            popup = document.createElement("div");
            popup.id = popupId;
            popup.className = "movie-hover-popup";
            popup.innerHTML = `
                <div class="movie-hover-popup__media">
                    <img class="movie-hover-popup__image" src="" alt="">
                    <div class="movie-hover-popup__shade"></div>
                </div>
                <div class="movie-hover-popup__body">
                    <h3 class="movie-hover-popup__title"></h3>
                    <p class="movie-hover-popup__original"></p>
                    <div class="movie-hover-popup__actions">
                        <a class="movie-hover-popup__btn movie-hover-popup__btn--primary" data-popup-action="watch" href="#">
                            <i class="fa-solid fa-play"></i>
                            <span>Xem ngay</span>
                        </a>
                        <button type="button" class="movie-hover-popup__btn" data-popup-action="like">
                            <i class="fa-solid fa-heart"></i>
                            <span>Thích</span>
                        </button>
                        <a class="movie-hover-popup__btn" data-popup-action="detail" href="#">
                            <i class="fa-solid fa-circle-info"></i>
                            <span>Chi tiết</span>
                        </a>
                    </div>
                    <div class="movie-hover-popup__chips"></div>
                    <p class="movie-hover-popup__status"></p>
                </div>
            `;
            document.body.appendChild(popup);
        }

        let isPointerInsidePopup = false;

        const setPopupPosition = (card) => {
            const rect = card.getBoundingClientRect();
            const popupRect = popup.getBoundingClientRect();
            const popupWidth = popupRect.width || 420;
            const popupHeight = popupRect.height || 560;
            const gutter = 16;
            let left = rect.right + gutter;

            if (left + popupWidth > window.innerWidth - gutter) {
                left = rect.left - popupWidth - gutter;
            }
            if (left < gutter) {
                left = Math.max(gutter, Math.min(rect.left + (rect.width - popupWidth) / 2, window.innerWidth - popupWidth - gutter));
            }

            let top = rect.top + (rect.height - popupHeight) / 2;
            top = Math.max(gutter, Math.min(top, window.innerHeight - popupHeight - gutter));

            popup.style.left = `${Math.round(left)}px`;
            popup.style.top = `${Math.round(top)}px`;
        };

        const setPopupContent = (movie) => {
            const name = movie.name || "Khong ro";
            const originalName = getMovieOriginalName(movie);
            const image = getMovieImage(movie);
            const rating = getMovieRating(movie);
            const quality = movie.quality || "";
            const ageRating = getMovieAgeRating(movie);
            const year = getMovieYear(movie);
            const duration = getMovieDuration(movie);
            const statusLine = getMovieStatusLine(movie);
            const slug = movie.slug || "";
            const watchHref = slug ? `/xem/${encodeURIComponent(slug)}` : "#";
            const detailHref = slug ? `/phim/${encodeURIComponent(slug)}` : "#";
            const chips = [
                rating ? `<span class="movie-hover-popup__chip movie-hover-popup__chip--rating">IMDb ${escapeHtml(rating)}</span>` : "",
                quality ? `<span class="movie-hover-popup__chip">${escapeHtml(quality)}</span>` : "",
                ageRating ? `<span class="movie-hover-popup__chip">${escapeHtml(ageRating)}</span>` : "",
                year ? `<span class="movie-hover-popup__chip">${escapeHtml(year)}</span>` : "",
                duration ? `<span class="movie-hover-popup__chip">${escapeHtml(duration)}</span>` : ""
            ].filter(Boolean).join("");

            const imageEl = popup.querySelector(".movie-hover-popup__image");
            const titleEl = popup.querySelector(".movie-hover-popup__title");
            const originalEl = popup.querySelector(".movie-hover-popup__original");
            const chipsEl = popup.querySelector(".movie-hover-popup__chips");
            const statusEl = popup.querySelector(".movie-hover-popup__status");
            const watchEl = popup.querySelector('[data-popup-action="watch"]');
            const detailEl = popup.querySelector('[data-popup-action="detail"]');
            const likeEl = popup.querySelector('[data-popup-action="like"]');

            if (imageEl) {
                imageEl.src = image;
                imageEl.alt = name;
            }
            if (titleEl) {
                titleEl.textContent = name;
            }
            if (originalEl) {
                originalEl.textContent = originalName;
                originalEl.hidden = !originalName;
            }
            if (chipsEl) {
                chipsEl.innerHTML = chips;
                chipsEl.hidden = !chips;
            }
            if (statusEl) {
                statusEl.textContent = statusLine;
                statusEl.hidden = !statusLine;
            }
            if (watchEl) {
                watchEl.href = watchHref;
                watchEl.classList.toggle("is-disabled", !slug);
                watchEl.setAttribute("aria-disabled", String(!slug));
            }
            if (detailEl) {
                detailEl.href = detailHref;
                detailEl.classList.toggle("is-disabled", !slug);
                detailEl.setAttribute("aria-disabled", String(!slug));
            }
            if (likeEl) {
                likeEl.dataset.movie = JSON.stringify(normalizeFavoritePayload({
                    ...movie,
                    slug
                }));
                syncFavoriteButton(likeEl, { slug });
            }
        };

        const hidePopup = () => {
            showTimer = clearTimer(showTimer);
            hideTimer = clearTimer(hideTimer);
            popup.classList.remove("is-visible");
            activeCard?.classList.remove("is-hover-popup-active");
            activeCard = null;
        };

        const showPopup = async (card) => {
            if (!desktopQuery.matches || !card?.dataset.slug) {
                return;
            }

            const baseMovie = readMovieDataFromCard(card);
            activeCard?.classList.remove("is-hover-popup-active");
            activeCard = card;
            activeCard.classList.add("is-hover-popup-active");

            setPopupContent(baseMovie);
            popup.classList.add("is-visible");
            popup.style.visibility = "hidden";
            setPopupPosition(card);
            popup.style.visibility = "";

            try {
                const cachedDetail = detailCache.get(baseMovie.slug);
                const detailMovie = cachedDetail || await fetchMovieDetail(baseMovie.slug);
                if (!cachedDetail) {
                    detailCache.set(baseMovie.slug, detailMovie);
                }

                if (activeCard !== card) {
                    return;
                }

                setPopupContent(mergeMovieData(baseMovie, detailMovie));
                setPopupPosition(card);
            } catch (error) {
                console.error("Load movie hover detail failed:", error);
            }
        };

        const scheduleShow = (card) => {
            if (!desktopQuery.matches || !card?.dataset.slug) {
                return;
            }

            hideTimer = clearTimer(hideTimer);
            showTimer = clearTimer(showTimer);
            showTimer = window.setTimeout(() => {
                showPopup(card);
            }, 500);

            const slug = card.dataset.slug;
            if (slug && !detailCache.has(slug)) {
                fetchMovieDetail(slug)
                    .then((detailMovie) => detailCache.set(slug, detailMovie))
                    .catch((error) => console.error("Preload movie hover detail failed:", error));
            }
        };

        const scheduleHide = () => {
            showTimer = clearTimer(showTimer);
            hideTimer = clearTimer(hideTimer);
            hideTimer = window.setTimeout(() => {
                hidePopup();
            }, 120);
        };

        popup.addEventListener("mouseenter", () => {
            isPointerInsidePopup = true;
            hideTimer = clearTimer(hideTimer);
        });
        popup.addEventListener("mouseleave", () => {
            isPointerInsidePopup = false;
            scheduleHide();
        });
        popup.addEventListener("click", (event) => {
            const action = event.target.closest("[data-popup-action]");
            if (!action) {
                return;
            }

            if (action.matches("button[data-popup-action='like']")) {
                const moviePayload = action.dataset.movie ? JSON.parse(action.dataset.movie) : null;
                if (!moviePayload?.movieSlug) {
                    return;
                }

                const movie = {
                    slug: moviePayload.movieSlug,
                    name: moviePayload.movieName,
                    original_name: moviePayload.originalName,
                    poster_url: moviePayload.posterUrl,
                    thumb_url: moviePayload.thumbUrl,
                    language: moviePayload.language,
                    quality: moviePayload.quality,
                    year: moviePayload.year
                };
                syncFavoriteButton(action, movie);
                toggleFavoriteMovie(movie)
                    .then(() => syncFavoriteButton(action, movie))
                    .catch((error) => console.error("Toggle popup favorite failed:", error));
                return;
            }

            if (action.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
            }
        });

        document.addEventListener("scroll", () => {
            if (isPointerInsidePopup) {
                return;
            }
            hidePopup();
        }, true);
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                hidePopup();
            }
        });
        window.addEventListener("resize", () => {
            if (!desktopQuery.matches) {
                hidePopup();
                return;
            }

            if (activeCard && popup.classList.contains("is-visible")) {
                setPopupPosition(activeCard);
            }
        });

        bindMovieHoverPopup.state = {
            scheduleShow,
            scheduleHide
        };
    }

    root.querySelectorAll(".movie-card").forEach((card) => {
        if (card.dataset.hoverPopupBound === "true") {
            return;
        }

        card.dataset.hoverPopupBound = "true";
        card.addEventListener("mouseenter", () => bindMovieHoverPopup.state.scheduleShow(card));
        card.addEventListener("mouseleave", bindMovieHoverPopup.state.scheduleHide);

        const link = card.closest(".movie-card-link");
        if (link) {
            link.addEventListener("mouseleave", bindMovieHoverPopup.state.scheduleHide);
        }
    });
}

function bindDetailFavoriteButton() {
    const favoriteButton = document.querySelector("[data-favorite-disabled='true']");
    if (!favoriteButton) {
        return;
    }

    syncFavoriteButton(favoriteButton, { slug: favoriteButton.dataset.slug || "" });
    favoriteButton.addEventListener("click", () => {
        showToast("Tinh nang yeu thich da duoc go bo.", "error");
    });
}

async function loadSection(type, containerId) {
    const grid = byId(containerId);
    if (!grid) {
        return;
    }

    try {
        const movies = await fetchMoviesWithLimit(type, getHomeSectionLimit(grid));
        renderMovies(containerId, movies);
    } catch (error) {
        console.error("Load section failed:", type, error);
        if (grid) {
            grid.innerHTML = '<div class="loading-spinner">Loi tai du lieu. Vui long thu lai.</div>';
        }
    }
}

function getGridColumnCount(grid) {
    if (!grid) {
        return 1;
    }

    const columnsText = window.getComputedStyle(grid).gridTemplateColumns;
    if (!columnsText || columnsText === "none") {
        return 1;
    }

    const columns = columnsText.split(" ").map((item) => item.trim()).filter(Boolean);
    return Math.max(1, columns.length);
}

function getHomeSectionLimit(grid) {
    const fallbackLimit = 12;
    if (!(grid instanceof HTMLElement)) {
        return fallbackLimit;
    }

    if (window.innerWidth < 1025) {
        return fallbackLimit;
    }

    const columns = getGridColumnCount(grid);
    const rows = 2;
    const desktopLimit = columns * rows;
    return Math.max(fallbackLimit, Math.min(desktopLimit, 36));
}

function initHomeSections() {
    const homeSections = [
        { type: "phim-moi", containerId: "phimMoiGrid", sectionId: "section-phim-moi", eager: true },
        { type: "phim-viet-nam", containerId: "phimVietNamGrid", sectionId: "section-phim-viet-nam" },
        { type: "phim-le", containerId: "phimLeGrid", sectionId: "section-phim-le" },
        { type: "phim-bo", containerId: "phimBoGrid", sectionId: "section-phim-bo" }
    ];

    const availableSections = homeSections.filter((section) => byId(section.containerId) && byId(section.sectionId));
    if (!availableSections.length) {
        return;
    }

    const loadedSections = new Set();
    const loadedSectionLimits = new Map();
    const loadOnce = (section) => {
        if (!section || loadedSections.has(section.containerId)) {
            return;
        }

        loadedSections.add(section.containerId);
        const grid = byId(section.containerId);
        loadedSectionLimits.set(section.containerId, getHomeSectionLimit(grid));
        loadSection(section.type, section.containerId);
    };

    availableSections.filter((section) => section.eager).forEach(loadOnce);

    if (!("IntersectionObserver" in window)) {
        availableSections.forEach(loadOnce);
        return;
    }

    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (!entry.isIntersecting) {
                return;
            }

            const section = availableSections.find((item) => item.sectionId === entry.target.id);
            loadOnce(section);
            observer.unobserve(entry.target);
        });
    }, { rootMargin: "280px 0px" });

    availableSections
        .filter((section) => !section.eager)
        .forEach((section) => {
            const element = byId(section.sectionId);
            if (element) {
                observer.observe(element);
            }
        });

    let resizeTimer = null;
    window.addEventListener("resize", () => {
        if (resizeTimer) {
            window.clearTimeout(resizeTimer);
        }

        resizeTimer = window.setTimeout(() => {
            availableSections.forEach((section) => {
                if (!loadedSections.has(section.containerId)) {
                    return;
                }

                const grid = byId(section.containerId);
                const nextLimit = getHomeSectionLimit(grid);
                const prevLimit = loadedSectionLimits.get(section.containerId);
                if (nextLimit === prevLimit) {
                    return;
                }

                loadedSectionLimits.set(section.containerId, nextLimit);
                loadSection(section.type, section.containerId);
            });
        }, 220);
    });
}

async function performSearch(rawKeyword) {
    const keyword = rawKeyword.trim();
    if (!keyword) {
        return;
    }
    window.location.href = `/tim-kiem?keyword=${encodeURIComponent(keyword)}`;
}

function renderLiveSearchItem(movie) {
    const slug = movie?.slug || "";
    if (!slug) {
        return "";
    }

    const name = movie.name || "Khong ro";
    const originalName = getMovieOriginalName(movie);
    const image = getMovieImage(movie);
    const metaParts = [
        getMovieYear(movie),
        movie.quality || "",
        movie.episode_current || movie.current_episode || ""
    ].filter(Boolean).slice(0, 3);

    return `
        <a class="live-search-item" href="/phim/${encodeURIComponent(slug)}">
            <img class="live-search-thumb" src="${escapeHtml(image)}" alt="${escapeHtml(name)}" loading="lazy">
            <span class="live-search-body">
                <span class="live-search-title">${escapeHtml(name)}</span>
                ${originalName ? `<span class="live-search-subtitle">${escapeHtml(originalName)}</span>` : ""}
                ${metaParts.length ? `
                    <span class="live-search-meta">
                        ${metaParts.map((part) => `<span class="live-search-chip">${escapeHtml(part)}</span>`).join("")}
                    </span>
                ` : ""}
            </span>
        </a>
    `;
}

function bindLiveSearch() {
    const searchInput = byId("searchInput");
    const liveSearchPanel = byId("liveSearchPanel");
    if (!(searchInput instanceof HTMLInputElement) || !liveSearchPanel) {
        return;
    }

    const MIN_QUERY_LENGTH = 2;
    const MAX_ITEMS = 9;
    let debounceTimer = null;
    let latestRequestId = 0;

    const closePanel = () => {
        liveSearchPanel.hidden = true;
        liveSearchPanel.innerHTML = "";
        searchInput.setAttribute("aria-expanded", "false");
    };

    window.closeLiveSearchPanel = closePanel;

    const openPanel = () => {
        liveSearchPanel.hidden = false;
        searchInput.setAttribute("aria-expanded", "true");
    };

    const setState = (message) => {
        liveSearchPanel.innerHTML = `<div class="live-search-state">${escapeHtml(message)}</div>`;
        openPanel();
    };

    const renderResults = (keyword, items) => {
        const filteredItems = items
            .filter((movie) => movie?.slug && movie?.name)
            .slice(0, MAX_ITEMS);

        if (!filteredItems.length) {
            setState("Không tìm thấy phim phù hợp.");
            return;
        }

        liveSearchPanel.innerHTML = `
            <div class="live-search-head">
                <span class="live-search-label">Gợi ý nhanh</span>
                <span class="live-search-count">${filteredItems.length} kết quả</span>
            </div>
            <div class="live-search-list">
                ${filteredItems.map(renderLiveSearchItem).join("")}
            </div>
            <div class="live-search-footer">
                <a class="live-search-more" href="/tim-kiem?keyword=${encodeURIComponent(keyword)}">
                    Xem kết quả đầy đủ <i class="fa-solid fa-arrow-right"></i>
                </a>
            </div>
        `;
        openPanel();
    };

    const runSearch = async (keyword) => {
        const requestId = ++latestRequestId;
        setState("Đang tìm kiếm...");

        try {
            const items = await searchMovies(keyword);
            if (requestId !== latestRequestId) {
                return;
            }
            renderResults(keyword, Array.isArray(items) ? items : []);
        } catch (error) {
            if (requestId !== latestRequestId) {
                return;
            }
            console.error("Live search failed:", error);
            setState("Không thể tải gợi ý lúc này.");
        }
    };

    const queueSearch = () => {
        const keyword = searchInput.value.trim();
        debounceTimer = debounceTimer ? window.clearTimeout(debounceTimer) : null;

        if (keyword.length < MIN_QUERY_LENGTH) {
            closePanel();
            return;
        }

        debounceTimer = window.setTimeout(() => {
            runSearch(keyword);
        }, 220);
    };

    searchInput.addEventListener("input", queueSearch);
    searchInput.addEventListener("focus", () => {
        if (searchInput.value.trim().length >= MIN_QUERY_LENGTH && liveSearchPanel.innerHTML.trim()) {
            openPanel();
        }
    });
    searchInput.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closePanel();
            searchInput.blur();
        }
    });

    liveSearchPanel.addEventListener("mousedown", (event) => {
        event.preventDefault();
    });

    document.addEventListener("click", (event) => {
        const target = event.target;
        if (!(target instanceof Element)) {
            return;
        }

        if (!target.closest(".nav-search-shell")) {
            closePanel();
        }
    });

    window.addEventListener("resize", closePanel);
}

function bindSearchEvents() {
    const searchBtn = byId("searchBtn");
    const searchInput = byId("searchInput");
    const heroSearch = byId("heroSearch");

    if (searchBtn && searchInput) {
        searchBtn.addEventListener("click", () => performSearch(searchInput.value));
        searchInput.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
                performSearch(searchInput.value);
            }
        });
    }

    if (heroSearch) {
        heroSearch.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
                performSearch(heroSearch.value);
            }
        });
    }

    window.searchFromHero = () => {
        performSearch(heroSearch?.value || "");
    };
}

function bindCategoryPagination() {
    document.querySelectorAll(".pagination-form").forEach((paginationForm) => {
        const pageInput = paginationForm.querySelector(".pagination-input");
        if (!(pageInput instanceof HTMLInputElement)) {
            return;
        }

        const totalPages = Number(pageInput.getAttribute("max") || 1);
        const normalizePage = (value) => {
            const parsed = Number(value);
            if (Number.isNaN(parsed)) {
                return 1;
            }

            return Math.min(Math.max(parsed, 1), totalPages);
        };

        const syncAndSubmit = () => {
            pageInput.value = String(normalizePage(pageInput.value));
            paginationForm.requestSubmit ? paginationForm.requestSubmit() : paginationForm.submit();
        };

        pageInput.addEventListener("focus", () => {
            pageInput.select();
        });

        pageInput.addEventListener("keydown", (event) => {
            if (event.key !== "Enter") {
                return;
            }

            event.preventDefault();
            syncAndSubmit();
            pageInput.blur();
        });

        pageInput.addEventListener("change", syncAndSubmit);
    });

    document.querySelectorAll(".category-pagination .pagination-arrow.disabled").forEach((link) => {
        link.addEventListener("click", (event) => {
            event.preventDefault();
        });
    });
}

function bindEpisodePagination() {
    document.querySelectorAll(".episode-server").forEach((serverBlock) => {
        const activePage = serverBlock.dataset.activePage || "1";
        const pages = serverBlock.querySelectorAll(".episode-page");
        const rangeTabs = serverBlock.querySelectorAll(".episode-range-tab");
        const totalPages = Number(pages.length || 1);

        const normalizePage = (value) => {
            const parsed = Number(value);
            if (Number.isNaN(parsed)) {
                return 1;
            }

            return Math.min(Math.max(parsed, 1), totalPages);
        };

        const setPage = (pageNumber) => {
            const normalizedPage = String(normalizePage(pageNumber));
            pages.forEach((page) => {
                page.style.display = page.dataset.page === normalizedPage ? "block" : "none";
            });

            rangeTabs.forEach((tab) => {
                const isActive = tab.dataset.pageTarget === normalizedPage;
                tab.classList.toggle("active", isActive);
                tab.setAttribute("aria-selected", String(isActive));
            });

            serverBlock.dataset.activePage = normalizedPage;
        };

        rangeTabs.forEach((tab) => {
            tab.addEventListener("click", () => {
                setPage(tab.dataset.pageTarget || "1");
            });
        });

        setPage(activePage);
    });
}

function bindEpisodeBrowser() {
    document.querySelectorAll(".episode-browser").forEach((browser) => {
        const tabButtons = browser.querySelectorAll(".episode-server-tab");
        const panels = browser.querySelectorAll(".episode-server");

        const setActiveServer = (targetId) => {
            tabButtons.forEach((button) => {
                const isActive = button.dataset.serverTarget === targetId;
                button.classList.toggle("active", isActive);
                button.setAttribute("aria-selected", String(isActive));
            });

            panels.forEach((panel) => {
                panel.classList.toggle("active", panel.id === targetId);
            });
        };

        tabButtons.forEach((button) => {
            button.addEventListener("click", () => {
                setActiveServer(button.dataset.serverTarget);
            });
        });

        const activeButton = browser.querySelector(".episode-server-tab.active") || tabButtons[0];
        if (activeButton) {
            setActiveServer(activeButton.dataset.serverTarget);
        }
    });
}

function bindCategoryLinks() {
    const sectionTargets = {
        "phim-moi": "section-phim-moi",
        "phim-bo": "section-phim-bo",
        "phim-le": "section-phim-le",
        "hoat-hinh": "section-hoat-hinh"
    };

    document.querySelectorAll("[data-type]").forEach((link) => {
        link.addEventListener("click", (event) => {
            const type = link.dataset.type;
            const targetId = sectionTargets[type];
            if (!targetId) {
                return;
            }

            const target = byId(targetId);
            if (target) {
                event.preventDefault();
                target.scrollIntoView({ behavior: "smooth", block: "start" });
                return;
            }

            link.href = `/#${targetId}`;
        });
    });
}

function bindMobileNavbar() {
    const menuToggle = document.querySelector(".nav-menu-toggle");
    const searchToggle = document.querySelector(".nav-search-toggle");
    const navLinks = document.querySelector(".nav-links");
    const navActions = document.querySelector(".nav-actions");
    const searchClose = document.querySelector(".nav-search-close");
    const searchInput = byId("searchInput");

    if (!menuToggle || !searchToggle || !navLinks || !navActions || !searchClose) {
        return;
    }

    const navDropdowns = navLinks.querySelectorAll(".nav-dropdown");
    const closeDropdowns = () => {
        navDropdowns.forEach((dropdown) => {
            dropdown.classList.remove("is-open");
            const toggle = dropdown.querySelector(".nav-dropdown-toggle");
            toggle?.setAttribute("aria-expanded", "false");
        });
    };

    const closeMenu = () => {
        closeDropdowns();
        navLinks.classList.remove("is-open");
        menuToggle.setAttribute("aria-expanded", "false");
    };

    const closeSearch = () => {
        navActions.classList.remove("is-open");
        searchToggle.setAttribute("aria-expanded", "false");
        window.closeLiveSearchPanel?.();
    };

    menuToggle.addEventListener("click", () => {
        const willOpen = !navLinks.classList.contains("is-open");
        closeSearch();
        navLinks.classList.toggle("is-open", willOpen);
        menuToggle.setAttribute("aria-expanded", String(willOpen));
    });

    searchToggle.addEventListener("click", () => {
        const willOpen = !navActions.classList.contains("is-open");
        closeMenu();
        navActions.classList.toggle("is-open", willOpen);
        searchToggle.setAttribute("aria-expanded", String(willOpen));
        if (willOpen) {
            window.setTimeout(() => searchInput?.focus(), 80);
        }
    });

    searchClose.addEventListener("click", () => {
        closeSearch();
        searchInput?.blur();
    });

    navLinks.querySelectorAll(".nav-dropdown-toggle").forEach((toggle) => {
        toggle.addEventListener("click", (event) => {
            if (window.innerWidth > 768) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();
            const dropdown = toggle.closest(".nav-dropdown");
            if (!dropdown) {
                return;
            }

            const willOpen = !dropdown.classList.contains("is-open");
            closeDropdowns();
            dropdown.classList.toggle("is-open", willOpen);
            toggle.setAttribute("aria-expanded", String(willOpen));
        });
    });

    navLinks.querySelectorAll("a").forEach((link) => {
        link.addEventListener("click", closeMenu);
    });

    document.addEventListener("click", (event) => {
        const target = event.target;
        if (!(target instanceof Element)) {
            return;
        }

        if (!target.closest(".navbar")) {
            closeMenu();
            closeSearch();
        }
    });

    window.addEventListener("resize", () => {
        if (window.innerWidth > 768) {
            closeMenu();
            closeSearch();
        }
    });
}

function bindDesktopNavbarDropdown() {
    const navbar = document.querySelector(".navbar");
    const navLinks = document.querySelector(".nav-links");

    if (!navbar || !navLinks) {
        return;
    }

    const navDropdowns = Array.from(navLinks.querySelectorAll(".nav-dropdown"));
    if (!navDropdowns.length) {
        return;
    }

    const closeDropdowns = (exceptDropdown = null) => {
        navDropdowns.forEach((dropdown) => {
            const shouldStayOpen = dropdown === exceptDropdown;
            dropdown.classList.toggle("is-open", shouldStayOpen);
            dropdown
                .querySelector(".nav-dropdown-toggle")
                ?.setAttribute("aria-expanded", String(shouldStayOpen));
        });
    };

    navDropdowns.forEach((dropdown) => {
        const toggle = dropdown.querySelector(".nav-dropdown-toggle");
        if (!toggle) {
            return;
        }

        toggle.addEventListener("click", (event) => {
            if (window.innerWidth <= 768) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();

            const willOpen = !dropdown.classList.contains("is-open");
            closeDropdowns(willOpen ? dropdown : null);
        });
    });

    document.addEventListener("click", (event) => {
        if (window.innerWidth <= 768) {
            return;
        }

        const target = event.target;
        if (!(target instanceof Element) || !target.closest(".navbar")) {
            closeDropdowns();
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeDropdowns();
        }
    });

    window.addEventListener("resize", () => {
        if (window.innerWidth <= 768) {
            closeDropdowns();
        }
    });
}

function bindMobileDetailInfoToggle() {
    const toggle = document.querySelector(".detail-info-toggle");
    const panel = document.querySelector(".detail-mobile-description");
    if (!toggle || !panel) {
        return;
    }

    const setExpanded = (expanded) => {
        toggle.classList.toggle("is-open", expanded);
        toggle.setAttribute("aria-expanded", String(expanded));
        panel.hidden = !expanded;
        panel.classList.toggle("is-open", expanded);
    };

    setExpanded(false);

    toggle.addEventListener("click", () => {
        const isExpanded = toggle.getAttribute("aria-expanded") === "true";
        setExpanded(!isExpanded);
    });

    window.addEventListener("resize", () => {
        if (window.innerWidth > 480) {
            setExpanded(false);
        }
    });
}

function bindNavbarScrollState() {
    const navbar = document.querySelector(".navbar");
    if (!navbar) {
        return;
    }

    const syncScrollState = () => {
        const isDesktop = window.innerWidth > 768;
        navbar.classList.toggle("is-scrolled", isDesktop && window.scrollY > 8);
    };

    syncScrollState();
    window.addEventListener("scroll", syncScrollState, { passive: true });
    window.addEventListener("resize", syncScrollState);
}

function bindBackToTop() {
    const backToTop = document.querySelector(".back-to-top");
    if (!backToTop) {
        return;
    }

    const syncBackToTop = () => {
        const scrollTop = window.scrollY || document.documentElement.scrollTop || 0;
        const scrollHeight = document.documentElement.scrollHeight - window.innerHeight;
        const progress = scrollHeight > 0 ? Math.min(scrollTop / scrollHeight, 1) : 0;

        backToTop.classList.toggle("is-visible", scrollTop > 24);
        backToTop.style.setProperty("--scroll-progress", `${progress * 100}%`);
    };

    backToTop.addEventListener("click", (event) => {
        event.preventDefault();
        window.scrollTo({ top: 0, behavior: "smooth" });
    });

    syncBackToTop();
    window.addEventListener("scroll", syncBackToTop, { passive: true });
    window.addEventListener("resize", syncBackToTop);
}

document.addEventListener("DOMContentLoaded", () => {
    bindSearchEvents();
    bindLiveSearch();
    bindCategoryLinks();
    bindCategoryPagination();
    bindEpisodePagination();
    bindEpisodeBrowser();
    bindMobileNavbar();
    bindMobileDetailInfoToggle();
    bindDesktopNavbarDropdown();
    bindNavbarScrollState();
    bindBackToTop();
    bindMovieHoverPopup();
    bindDetailFavoriteButton();
    bindFavoritePageActions();
    loadHeroSlider();
    initHomeSections();
});
