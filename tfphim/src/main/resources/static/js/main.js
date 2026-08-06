const API_BASE = "/api/movies";
const KK_IMAGE_BASE_URL = "https://img.phimapi.com/";
const KK_MOVIE_IMAGE_BASE_URL = `${KK_IMAGE_BASE_URL}uploads/movies/`;
const OPHIM_IMAGE_BASE_URL = "https://img.ophim.live/";
const OPHIM_MOVIE_IMAGE_BASE_URL = `${OPHIM_IMAGE_BASE_URL}uploads/movies/`;
const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original";

const MOVIE_TYPES = {
    "phim-moi": "phim-moi",
    "phim-viet-nam": "quoc-gia/viet-nam",
    "phim-nhat-ban": "quoc-gia/nhat-ban",
    "phim-bo": "phim-bo",
    "phim-le": "phim-le"
};

function byId(id) {
    return document.getElementById(id);
}

function faIcon(iconName, className = "") {
    const classes = ["fa-solid", iconName, className]
        .filter(Boolean)
        .map(escapeHtml)
        .join(" ");
    return `<i class="${classes}" aria-hidden="true"></i>`;
}

function toastIcon(type) {
    if (type === "close" || type === "error") {
        return `
            <svg class="app-toast__svg" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path d="M6 6l12 12M18 6L6 18"></path>
            </svg>
        `;
    }

    return `
        <svg class="app-toast__svg" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path d="M5 12.5l4.2 4.2L19 7"></path>
        </svg>
    `;
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
            ${toastIcon(type === "error" ? "error" : "success")}
        </span>
        <span class="app-toast__message">${escapeHtml(message)}</span>
        <button class="app-toast__close" type="button" aria-label="Dong thong bao">
            ${toastIcon("close")}
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
    const encodedKeyword = encodeURIComponent(keyword);
    const responses = await Promise.allSettled([
        getJson(`${API_BASE}/search?keyword=${encodedKeyword}`).then((payload) => ({ payload, source: "kk" })),
        getJson(`${API_BASE}/ophim/search?keyword=${encodedKeyword}`).then((payload) => ({ payload, source: "ophim" }))
    ]);

    const movies = responses.flatMap((response) => (
        response.status === "fulfilled"
            ? getMovieItems(response.value.payload).map((movie) => ({ ...movie, source: response.value.source }))
            : []
    ));

    return sortSearchResults(mergeSearchResults(movies));
}

function mergeSearchResults(movies) {
    if (!Array.isArray(movies) || !movies.length) {
        return [];
    }

    const mergedMovies = [];
    movies.forEach((movie) => {
        const candidate = withSourceMetadata(movie);
        const existingIndex = mergedMovies.findIndex((existing) => isSameSearchMovie(existing, candidate));
        if (existingIndex >= 0) {
            mergedMovies[existingIndex] = mergeSearchMovieData(mergedMovies[existingIndex], candidate);
            return;
        }

        mergedMovies.push(candidate);
    });

    return mergedMovies;
}

function withSourceMetadata(movie) {
    const source = normalizeSourceParam(movie?.source);
    const slug = String(movie?.slug || "").trim();
    return {
        ...movie,
        source,
        detail_url: movie?.detail_url || buildMovieDetailUrl(slug, source)
    };
}

function mergeSearchMovieData(existing, candidate) {
    const preferred = compareSearchPriority(candidate, existing) < 0 ? candidate : existing;
    const fallback = preferred === candidate ? existing : candidate;
    const sourceOptions = mergeSourceOptions(existing.source_options, candidate.source_options, existing, candidate);
    return {
        ...fallback,
        ...preferred,
        source_options: sourceOptions,
        source_count: sourceOptions.length || 1
    };
}

function mergeSourceOptions(firstOptions, secondOptions, firstMovie, secondMovie) {
    const result = [];
    [firstOptions, secondOptions].forEach((options) => {
        if (Array.isArray(options)) {
            options.forEach((option) => addSourceOption(result, option));
        }
    });
    addSourceOption(result, movieToSourceOption(firstMovie));
    addSourceOption(result, movieToSourceOption(secondMovie));
    return result;
}

function movieToSourceOption(movie) {
    const source = normalizeSourceParam(movie?.source);
    const slug = String(movie?.slug || "").trim();
    return {
        source,
        label: source === "ophim" ? "OPhim" : "KK",
        slug,
        detail_url: buildMovieDetailUrl(slug, source)
    };
}

function addSourceOption(target, option) {
    const source = normalizeSourceParam(option?.source);
    const slug = String(option?.slug || "").trim();
    if (!source || target.some((item) => item.source === source)) {
        return;
    }
    target.push({
        ...option,
        source,
        label: option?.label || (source === "ophim" ? "OPhim" : "KK"),
        slug,
        detail_url: option?.detail_url || buildMovieDetailUrl(slug, source)
    });
}

function isSameSearchMovie(first, second) {
    const firstSlug = normalizeMovieIdentity(first?.slug);
    const secondSlug = normalizeMovieIdentity(second?.slug);
    if (firstSlug && firstSlug === secondSlug) {
        return true;
    }

    const firstYear = String(getMovieYear(first) || "").match(/\b(18\d{2}|19\d{2}|20\d{2}|21\d{2})\b/)?.[1] || "";
    const secondYear = String(getMovieYear(second) || "").match(/\b(18\d{2}|19\d{2}|20\d{2}|21\d{2})\b/)?.[1] || "";
    if (firstYear && secondYear && firstYear !== secondYear) {
        return false;
    }

    const firstTitles = getMovieIdentityTitles(first);
    const secondTitles = getMovieIdentityTitles(second);
    const exactTitle = firstTitles.some((title) => secondTitles.includes(title));
    if (!exactTitle) {
        return false;
    }

    if (firstYear || secondYear) {
        return true;
    }

    const firstOriginal = normalizeMovieIdentity(getMovieOriginalName(first));
    const secondOriginal = normalizeMovieIdentity(getMovieOriginalName(second));
    if (firstOriginal && secondOriginal) {
        return firstOriginal === secondOriginal;
    }

    return firstTitles.some((title) => title.length >= 10 || title.split(" ").filter((token) => token.length > 1).length >= 3);
}

function getMovieIdentityTitles(movie) {
    return [movie?.name, getMovieOriginalName(movie)]
        .map(normalizeMovieIdentity)
        .filter((title, index, titles) => title.length >= 3 && titles.indexOf(title) === index);
}

function normalizeMovieIdentity(value) {
    return normalizeSearchText(value)
        .replace(/\b(tmdb|imdb|vietsub|thuyet minh|long tieng|phu de|hd|fhd|full|trailer)\b/g, " ")
        .replace(/[^\p{L}\p{N}]+/gu, " ")
        .replace(/\s+/g, " ")
        .trim();
}

function normalizeSourceParam(source) {
    const normalized = String(source || "").trim().toLowerCase();
    if (normalized === "ophim" || normalized.includes("ophim")) {
        return "ophim";
    }
    if (normalized === "kk" || normalized === "kkphim" || normalized.includes("phimapi")) {
        return "kk";
    }
    return "";
}

function buildMovieDetailUrl(slug, source = "") {
    if (!slug) {
        return "";
    }
    const sourceKey = normalizeSourceParam(source);
    const baseUrl = `/phim/${encodeURIComponent(slug)}`;
    return sourceKey ? `${baseUrl}?source=${encodeURIComponent(sourceKey)}` : baseUrl;
}

function compareSearchPriority(first, second) {
    const trailerCompare = getSearchTrailerScore(second) - getSearchTrailerScore(first);
    if (trailerCompare !== 0) {
        return trailerCompare;
    }

    const episodeCompare = getSearchEpisodeScore(second) - getSearchEpisodeScore(first);
    if (episodeCompare !== 0) {
        return episodeCompare;
    }

    return getSearchYearScore(second) - getSearchYearScore(first);
}

function sortSearchResults(movies) {
    if (!Array.isArray(movies) || movies.length <= 1) {
        return Array.isArray(movies) ? movies : [];
    }

    return [...movies].sort(compareSearchPriority);
}

function getSearchTrailerScore(movie) {
    const episode = normalizeSearchText(movie?.current_episode ?? movie?.episode_current);
    const status = normalizeSearchText(movie?.status ?? movie?.episode_status);
    const type = normalizeSearchText(movie?.type ?? movie?.movie_type ?? movie?.category_type);
    const quality = normalizeSearchText(movie?.quality);
    const combinedText = [episode, status, type, quality].join(" ");
    const trailerUrl = String(movie?.trailer_url ?? "").trim();

    return combinedText.includes("trailer")
        || combinedText.includes("sap chieu")
        || status.includes("sắp chiếu")
        || (trailerUrl && getSearchEpisodeScore(movie) === 0)
        ? 1
        : 0;
}

function getSearchEpisodeScore(movie) {
    const totalEpisode = extractNonNegativeInt(movie?.total_episodes ?? movie?.episode_total);
    if (totalEpisode > 0) {
        return totalEpisode;
    }

    const currentEpisode = extractEpisodeCountFromLabel(movie?.current_episode ?? movie?.episode_current);
    if (currentEpisode > 0) {
        return currentEpisode;
    }

    return isSeriesCardMovie(movie || {}) ? 1 : 0;
}

function getSearchYearScore(movie) {
    return extractYear(movie?.year)
        || extractYear(movie?.release_year)
        || extractYear(movie?.modified?.time)
        || extractYear(movie?.created)
        || 0;
}

function extractNonNegativeInt(value) {
    const number = Number.parseInt(String(value ?? "").trim(), 10);
    return Number.isFinite(number) ? Math.max(number, 0) : 0;
}

function extractEpisodeCountFromLabel(value) {
    const normalized = String(value ?? "")
        .replace(/[^0-9/]+/g, " ")
        .trim();
    if (!normalized) {
        return 0;
    }

    const firstSlashPart = normalized.split("/")[0] || normalized;
    return extractNonNegativeInt(firstSlashPart);
}

function extractYear(value) {
    if (typeof value === "number" && Number.isFinite(value)) {
        return value >= 1800 && value <= 3000 ? value : 0;
    }

    const match = String(value ?? "").match(/\b(18\d{2}|19\d{2}|20\d{2}|21\d{2})\b/);
    return match ? extractNonNegativeInt(match[1]) : 0;
}

function normalizeSearchText(value) {
    return String(value ?? "")
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/\u0111/g, "d")
        .replace(/\u0110/g, "D")
        .toLowerCase()
        .trim();
}

async function fetchMovieDetail(slug, source = "") {
    const sourceKey = normalizeSourceParam(source);
    const params = sourceKey ? `?source=${encodeURIComponent(sourceKey)}` : "";
    const data = await getJson(`${API_BASE}/${encodeURIComponent(slug)}${params}`);
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

function isSeriesCardMovie(movie, listingType = "") {
    const normalizedListingType = String(listingType || "").trim().toLowerCase();
    if (normalizedListingType === "phim-le") {
        return false;
    }
    if (normalizedListingType === "phim-bo") {
        return true;
    }

    const normalizedType = String(movie.type || movie.movie_type || movie.category_type || "")
        .trim()
        .toLowerCase();

    if (["single", "phim-le"].includes(normalizedType)) {
        return false;
    }
    if (["series", "tvshows", "phim-bo"].includes(normalizedType)) {
        return true;
    }

    const totalEpisodes = Number(movie.total_episodes || movie.episode_total || 0);
    return Number.isFinite(totalEpisodes) && totalEpisodes > 1;
}

function getCardEpisodeLabel(movie, listingType = "") {
    const episode = String(movie.episode_current || movie.current_episode || "").trim();
    if (!episode) {
        return "";
    }
    if (!isSeriesCardMovie(movie, listingType)) {
        return episode;
    }

    const normalized = episode
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/\u0111/g, "d")
        .replace(/\u0110/g, "D")
        .toLowerCase();

    return normalized.includes("hoan tat") || normalized.includes("full") || normalized.includes("completed")
        ? "HO\u00c0N T\u1ea4T"
        : episode;
}

function getLanguageBadges(language) {
    const normalized = normalizeSearchText(language).replace(/\s+/g, " ");
    const badges = [];

    if (normalized.includes("vietsub")) {
        badges.push({ label: "P.\u0110\u1ec1", className: "lang-sub" });
    }
    if (normalized.includes("thuyet minh")) {
        badges.push({ label: "T.Minh", className: "lang-dub" });
    }
    if (normalized.includes("long tieng")) {
        badges.push({ label: "L.Ti\u1ebfng", className: "lang-voice" });
    }

    return badges;
}

function getMovieLanguageBadgeSource(movie) {
    return [
        movie?.lang,
        movie?.language,
        movie?.episode_current,
        movie?.current_episode,
        movie?.status,
        movie?.episode_status
    ]
        .map((value) => String(value || "").trim())
        .filter(Boolean)
        .join(" ");
}
function getMovieImage(movie) {
    const source = movie?.source || "";
    const cardImage = resolveMovieImageUrl(movie?.card_image_url, source);
    if (cardImage) {
        return cardImage;
    }

    if (isOphimSource(source)) {
        return resolveMovieImageUrl(movie.thumb_url, source)
            || resolveMovieImageUrl(movie.poster_url, source)
            || "https://via.placeholder.com/600x900?text=No+Image";
    }

    return resolveMovieImageUrl(movie.poster_url, source)
        || resolveMovieImageUrl(movie.thumb_url, source)
        || "https://via.placeholder.com/600x900?text=No+Image";
}

function resolveMovieImageUrl(url, source = "") {
    const raw = String(url || "").trim();
    if (!raw) {
        return "";
    }

    if (raw.startsWith("http://") || raw.startsWith("https://")) {
        return raw;
    }

    if (raw.startsWith("//")) {
        return `https:${raw}`;
    }

    const normalizedPath = raw.startsWith("/") ? raw.slice(1) : raw;
    if (normalizedPath.startsWith("uploads/movies/")) {
        return `${isOphimSource(source) ? OPHIM_IMAGE_BASE_URL : KK_IMAGE_BASE_URL}${normalizedPath}`;
    }
    if (normalizedPath.startsWith("upload/")) {
        return `${KK_IMAGE_BASE_URL}${normalizedPath}`;
    }
    if (looksLikeImageFile(normalizedPath)) {
        return `${isOphimSource(source) ? OPHIM_MOVIE_IMAGE_BASE_URL : KK_MOVIE_IMAGE_BASE_URL}${normalizedPath}`;
    }

    return raw;
}

function isOphimSource(source) {
    return String(source || "").trim().toLowerCase() === "ophim";
}

function looksLikeImageFile(path) {
    const lowerPath = String(path || "").toLowerCase();
    return lowerPath.endsWith(".jpg")
        || lowerPath.endsWith(".jpeg")
        || lowerPath.endsWith(".png")
        || lowerPath.endsWith(".webp")
        || lowerPath.endsWith(".avif");
}

function resolveMovieLogoUrl(url, source = "") {
    const raw = String(url || "").trim();
    if (!raw || raw.toLowerCase() === "null" || raw === "[object Object]") {
        return "";
    }

    if (raw.startsWith("/") && !raw.startsWith("/uploads/") && looksLikeImageFile(raw)) {
        return `${TMDB_IMAGE_BASE_URL}${raw}`;
    }

    return resolveMovieImageUrl(raw, source);
}

function getMovieLogoUrl(movie) {
    const candidates = [
        movie?.tmdb_logo_url,
        movie?.tmdbLogoUrl,
        movie?.tmdb?.logo_url,
        movie?.tmdb?.logoUrl,
        movie?.tmdb?.logo_path,
        movie?.logo_url,
        movie?.logoUrl,
        movie?.movie_logo,
        movie?.movieLogo,
        movie?.title_logo,
        movie?.titleLogo,
        movie?.clear_logo,
        movie?.clearLogo,
        movie?.clearlogo
    ];

    const logo = movie?.logo;
    if (logo && typeof logo === "object") {
        candidates.push(logo.url, logo.src, logo.logo_url, logo.logoUrl, logo.file_path, logo.filePath);
    } else {
        candidates.push(logo);
    }

    const images = movie?.images;
    if (images && typeof images === "object" && !Array.isArray(images)) {
        candidates.push(images.logo, images.logo_url, images.logoUrl, images.clearlogo, images.clear_logo);
        if (Array.isArray(images.logos)) {
            images.logos.forEach((item) => {
                if (item && typeof item === "object") {
                    candidates.push(item.url, item.src, item.file_path, item.filePath);
                } else {
                    candidates.push(item);
                }
            });
        }
    }

    for (const candidate of candidates) {
        const logoUrl = resolveMovieLogoUrl(candidate, movie?.source);
        if (logoUrl) {
            return logoUrl;
        }
    }

    return "";
}

function preloadImage(url) {
    const imageUrl = String(url || "").trim();
    if (!imageUrl) {
        return Promise.resolve("");
    }

    return new Promise((resolve) => {
        const image = new Image();
        image.onload = () => resolve(imageUrl);
        image.onerror = () => resolve("");
        image.src = imageUrl;
        if (image.decode) {
            image.decode().then(() => resolve(imageUrl)).catch(() => {});
        }
    });
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

function cleanTextSnippet(value, maxLength = 220) {
    const cleaned = String(value || "")
        .replace(/<[^>]*>/g, " ")
        .replace(/&nbsp;/gi, " ")
        .replace(/&amp;/gi, "&")
        .replace(/&quot;/gi, "\"")
        .replace(/&#39;/gi, "'")
        .replace(/\s+/g, " ")
        .trim();

    if (cleaned.length <= maxLength) {
        return cleaned;
    }

    const sliced = cleaned.slice(0, maxLength).trim();
    const lastSpace = sliced.lastIndexOf(" ");
    return `${(lastSpace > 120 ? sliced.slice(0, lastSpace) : sliced).trim()}...`;
}

function getMovieYear(movie) {
    return movie.year
        || movie.release_year
        || movie.modified?.time?.slice?.(0, 4)
        || "";
}

async function fetchTmdbMovieLogo(movie, cache = new Map()) {
    const directLogoUrl = getMovieLogoUrl(movie);
    if (directLogoUrl) {
        return directLogoUrl;
    }

    const name = String(movie?.name || "").trim();
    const originalName = String(getMovieOriginalName(movie) || "").trim();
    const year = String(getMovieYear(movie) || "").trim();
    const tmdbId = String(movie?.tmdb?.id || movie?.tmdb_id || "").trim();
    const tmdbType = String(movie?.tmdb?.type || movie?.tmdb_type || movie?.type || movie?.movie_type || "").trim();
    const imdbId = String(movie?.imdb?.id || movie?.imdb_id || "").trim();
    const cacheKey = [name, originalName, year, tmdbId, tmdbType, imdbId].join("|").toLowerCase();

    if (!name && !originalName && !tmdbId && !imdbId) {
        return "";
    }
    if (cache.has(cacheKey)) {
        return cache.get(cacheKey);
    }

    const params = new URLSearchParams();
    if (name) {
        params.set("name", name);
    }
    if (originalName) {
        params.set("originalName", originalName);
    }
    if (year) {
        params.set("year", year);
    }
    if (tmdbId) {
        params.set("tmdbId", tmdbId);
    }
    if (tmdbType) {
        params.set("tmdbType", tmdbType);
    }
    if (imdbId) {
        params.set("imdbId", imdbId);
    }

    const request = getJson(`${API_BASE}/tmdb/logo?${params.toString()}`)
        .then((payload) => {
            const logoUrl = resolveMovieLogoUrl(payload?.logoUrl, movie?.source);
            cache.set(cacheKey, logoUrl);
            return logoUrl;
        })
        .catch((error) => {
            cache.delete(cacheKey);
            throw error;
        });

    cache.set(cacheKey, request);
    return request;
}

function getMovieRating(movie) {
    return getMovieImdbRating(movie) || getMovieTmdbRating(movie) || String(movie?.rating || "").trim();
}

function getMovieImdbRating(movie) {
    return firstVisibleRating([
        movie?.movie_imdb_rating,
        movie?.imdb_vote_average,
        movie?.imdb_rating,
        movie?.rating_imdb,
        movie?.imdb_score,
        movie?.imdb?.vote_average,
        movie?.imdb?.rating,
        movie?.imdb?.score,
        movie?.ratings?.imdb,
        movie?.movie_rating
    ]);
}

function getMovieTmdbRating(movie) {
    return firstVisibleRating([
        movie?.movie_tmdb_rating,
        movie?.tmdb_vote_average,
        movie?.tmdb_rating,
        movie?.rating_tmdb,
        movie?.tmdb_score,
        movie?.tmdb?.vote_average,
        movie?.tmdb?.rating,
        movie?.tmdb?.score,
        movie?.ratings?.tmdb
    ]);
}

function firstVisibleRating(values) {
    for (const value of values) {
        const rating = String(value || "").trim();
        if (hasVisibleRating(rating)) {
            return rating;
        }
    }

    return "";
}

function hasVisibleRating(rating) {
    const normalized = String(rating || "").trim();
    if (!normalized || normalized.toLowerCase() === "null" || normalized.toLowerCase() === "n/a") {
        return false;
    }

    const numericRating = Number(normalized.replace(",", "."));
    return Number.isFinite(numericRating) ? numericRating > 0 : true;
}

function createRatingBadges(movie, badgeClass = "badge") {
    const imdbRating = getMovieImdbRating(movie);
    const tmdbRating = getMovieTmdbRating(movie);

    return [
        imdbRating ? `<span class="${badgeClass} badge-rating badge-rating--imdb">IMDb ${escapeHtml(imdbRating)}</span>` : "",
        tmdbRating ? `<span class="${badgeClass} badge-rating badge-rating--tmdb">TMDB ${escapeHtml(tmdbRating)}</span>` : ""
    ].filter(Boolean).join("");
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

function extractMovieCategories(movie) {
    const rawCategory = movie?.category;
    if (Array.isArray(rawCategory)) {
        return rawCategory
            .map((item) => (typeof item === "string" ? item : item?.name || ""))
            .map((item) => String(item || "").trim())
            .filter(Boolean);
    }

    const text = String(rawCategory || "").trim();
    if (!text) {
        return [];
    }

    try {
        const parsed = JSON.parse(text);
        if (Array.isArray(parsed)) {
            return parsed
                .map((item) => (typeof item === "string" ? item : item?.name || ""))
                .map((item) => String(item || "").trim())
                .filter(Boolean);
        }
    } catch (error) {
        // Ignore JSON parse error and fallback to pattern parsing.
    }

    const extractedNames = [];
    const namePattern = /name=([^,}\]]+)/g;
    let match = namePattern.exec(text);
    while (match) {
        const name = String(match[1] || "").trim();
        if (name) {
            extractedNames.push(name);
        }
        match = namePattern.exec(text);
    }
    if (extractedNames.length) {
        return extractedNames;
    }

    return text.split("|").map((item) => item.trim()).filter(Boolean);
}

function getMovieCategoryLine(movie, limit = 3) {
    return extractMovieCategories(movie).slice(0, limit).join(" - ");
}

function createMovieDatasetAttributes(movie) {
    return [
        ["slug", movie.slug || ""],
        ["source", normalizeSourceParam(movie.source) || ""],
        ["detail-url", movie.detail_url || buildMovieDetailUrl(movie.slug || "", movie.source)],
        ["name", movie.name || ""],
        ["original-name", getMovieOriginalName(movie)],
        ["thumb", movie.thumb_url || ""],
        ["poster", movie.poster_url || ""],
        ["card-image", movie.card_image_url || getMovieImage(movie)],
        ["quality", movie.quality || ""],
        ["episode", movie.episode_current || movie.current_episode || ""],
        ["language", movie.lang || movie.language || ""],
        ["year", getMovieYear(movie)],
        ["description", getMovieDescription(movie)],
        ["time", getMovieDuration(movie)],
        ["category", getMovieCategoryLine(movie)],
        ["status", getMovieStatusLine(movie)],
        ["trailer-url", movie.trailer_url || ""],
        ["rating", getMovieRating(movie)],
        ["imdb-rating", getMovieImdbRating(movie)],
        ["tmdb-rating", getMovieTmdbRating(movie)]
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
        card_image_url: card.dataset.cardImage || "",
        quality: card.dataset.quality || "",
        episode_current: card.dataset.episode || "",
        current_episode: card.dataset.episode || "",
        language: card.dataset.language || "",
        year: card.dataset.year || "",
        description: card.dataset.description || "",
        time: card.dataset.time || "",
        category: card.dataset.category || "",
        status: card.dataset.status || "",
        trailer_url: card.dataset.trailerUrl || "",
        movie_rating: card.dataset.rating || "",
        movie_imdb_rating: card.dataset.imdbRating || "",
        movie_tmdb_rating: card.dataset.tmdbRating || "",
        source: normalizeSourceParam(card.dataset.source || ""),
        detail_url: card.dataset.detailUrl || ""
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
        card_image_url: detailMovie.card_image_url || baseMovie.card_image_url,
        tmdb_thumb_url: detailMovie.tmdb_thumb_url || baseMovie.tmdb_thumb_url,
        tmdb_backdrop_url: detailMovie.tmdb_backdrop_url || baseMovie.tmdb_backdrop_url,
        tmdb_logo_url: detailMovie.tmdb_logo_url || baseMovie.tmdb_logo_url,
        quality: detailMovie.quality || baseMovie.quality,
        episode_current: detailMovie.episode_current || detailMovie.current_episode || baseMovie.episode_current,
        current_episode: detailMovie.current_episode || detailMovie.episode_current || baseMovie.current_episode,
        language: detailMovie.lang || detailMovie.language || baseMovie.lang || baseMovie.language,
        year: getMovieYear(detailMovie) || getMovieYear(baseMovie),
        description: getMovieDescription(detailMovie) || getMovieDescription(baseMovie),
        time: getMovieDuration(detailMovie) || getMovieDuration(baseMovie),
        category: detailMovie.category || baseMovie.category,
        status: getMovieStatusLine(detailMovie) || getMovieStatusLine(baseMovie),
        trailer_url: detailMovie.trailer_url || baseMovie.trailer_url,
        movie_rating: getMovieRating(detailMovie) || getMovieRating(baseMovie),
        movie_imdb_rating: getMovieImdbRating(detailMovie) || getMovieImdbRating(baseMovie),
        movie_tmdb_rating: getMovieTmdbRating(detailMovie) || getMovieTmdbRating(baseMovie),
        source: normalizeSourceParam(detailMovie.source) || normalizeSourceParam(baseMovie.source),
        detail_url: detailMovie.detail_url || baseMovie.detail_url
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
        language: movie?.lang || movie?.language || "",
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

function createMovieCard(movie, listingType = "", index = 0) {
    const thumb = getMovieImage(movie);
    const name = movie.name || "Khong ro";
    const episode = getCardEpisodeLabel(movie, listingType);
    const quality = movie.quality || "HD";
    const year = getMovieYear(movie);
    const ratingBadges = createRatingBadges(movie);
    const slug = movie.slug || "";
    const languageBadges = getLanguageBadges(getMovieLanguageBadgeSource(movie));
    const href = slug ? (movie.detail_url || buildMovieDetailUrl(slug, movie.source)) : "#";
    const disabledClass = slug ? "" : " is-disabled";
    const datasetAttrs = createMovieDatasetAttributes(movie);
    const isPriorityImage = index < 6;
    const loading = isPriorityImage ? "eager" : "lazy";
    const fetchPriority = isPriorityImage ? "high" : "low";

    return `
        <a class="movie-card-link${disabledClass}" href="${escapeHtml(href)}" ${slug ? "" : 'aria-disabled="true" tabindex="-1"'}>
            <article class="movie-card" ${datasetAttrs}>
                <div class="card-thumb">
                    <img src="${escapeHtml(thumb)}" alt="${escapeHtml(name)}" width="360" height="540"
                         sizes="(max-width: 480px) 46vw, (max-width: 900px) 30vw, 180px"
                         loading="${loading}" decoding="async" fetchpriority="${fetchPriority}"
                         onerror="this.src='https://via.placeholder.com/200x300?text=No+Image'">
                    <div class="overlay">
                        <div class="play-btn">${faIcon("fa-play")}</div>
                    </div>
                    <span class="badge hd">${escapeHtml(quality)}</span>
                    ${episode ? `<span class="badge ep" style="top:8px;left:auto;right:8px;">${escapeHtml(episode)}</span>` : ""}
                    ${(year || ratingBadges) ? `
                        <div class="card-thumb-badges">
                            ${year ? `<span class="badge badge-year">${escapeHtml(year)}</span>` : ""}
                            ${ratingBadges}
                        </div>
                    ` : ""}
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
    return cleanTextSnippet(movie.content || movie.description || movie.excerpt, 230)
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
    const imdbRating = getMovieImdbRating(movie);
    const tmdbRating = getMovieTmdbRating(movie);

    return [
        imdbRating ? `<span class="hero-meta-chip hero-meta-rating hero-meta-rating--imdb">IMDb ${escapeHtml(imdbRating)}</span>` : "",
        tmdbRating ? `<span class="hero-meta-chip hero-meta-rating hero-meta-rating--tmdb">TMDB ${escapeHtml(tmdbRating)}</span>` : "",
        quality ? `<span class="fhd">${escapeHtml(quality)}</span>` : "",
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

function renderHeroTitle(heroTitle, movieName, logoUrl) {
    if (!heroTitle) {
        return;
    }

    const canUseLogo = Boolean(logoUrl);
    if (!canUseLogo) {
        heroTitle.textContent = movieName;
        heroTitle.classList.remove("has-logo");
        return;
    }

    const logo = document.createElement("img");
    logo.className = "hero-title-logo";
    logo.src = logoUrl;
    logo.alt = movieName;
    logo.loading = "eager";
    logo.decoding = "async";
    logo.addEventListener("error", () => {
        heroTitle.textContent = movieName;
        heroTitle.classList.remove("has-logo");
    }, { once: true });

    heroTitle.replaceChildren(logo);
    heroTitle.classList.add("has-logo");
}

function renderHeroThumbs(movies, activeIndex) {
    return movies.map((movie, index) => {
        const thumb = resolveMovieLogoUrl(movie.tmdb_thumb_url || movie.tmdb_backdrop_url, movie.source) || resolveMovieImageUrl(movie.thumb_url, movie.source) || resolveMovieImageUrl(movie.poster_url, movie.source) || "https://via.placeholder.com/160x90?text=No+Image";
        const name = movie.name || "Phim nổi bật";
        return `
            <button type="button" class="hero-thumb ${index === activeIndex ? "is-active" : ""}" data-hero-index="${index}" aria-label="${escapeHtml(name)}">
                <img src="${escapeHtml(thumb)}" alt="${escapeHtml(name)}" loading="lazy" decoding="async" fetchpriority="low">
            </button>
        `;
    }).join("");
}

async function fetchManagedHeroBannerMovies() {
    try {
        const data = await getJson("/api/quan-ly-phim/hero-banner?enabledOnly=true");
        if (data?.enabled === false) {
            return [];
        }
        const items = getMovieItems(data);
        return items.slice(0, 6);
    } catch (error) {
        console.warn("Load managed hero banner failed:", error);
        return [];
    }
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
    const heroShareBtn = byId("heroShareBtn");
    const heroThumbs = byId("heroThumbs");
    const heroCopy = byId("heroCopy");

    try {
        const managedHeroMovies = await fetchManagedHeroBannerMovies();
        const items = managedHeroMovies.length ? managedHeroMovies : (await fetchMovies("phim-moi")).slice(0, 6);
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
        let currentHeroMovie = null;
        const heroDetailCache = new Map();
        const heroLogoCache = new Map();
        const autoplayDelay = 20000;
        const contentRevealDelay = 500;

        const getBackdropStyle = (imageUrl) => `
            linear-gradient(90deg, rgba(12,14,20,0.96) 0%, rgba(12,14,20,0.54) 34%, rgba(12,14,20,0.16) 56%, rgba(12,14,20,0.88) 100%),
            radial-gradient(circle at center, rgba(255,255,255,0.14), transparent 42%),
            url("${imageUrl}")
        `;

        const getHeroImage = (movie) => (
            resolveMovieLogoUrl(movie?.tmdb_thumb_url || movie?.tmdb_backdrop_url, movie?.source)
            || resolveMovieImageUrl(movie?.thumb_url, movie?.source)
            || resolveMovieImageUrl(movie?.poster_url, movie?.source)
            || "https://via.placeholder.com/1280x720?text=No+Image"
        );

        const getPreloadedHeroLogo = async (movie) => {
            const logoUrl = await fetchTmdbMovieLogo(movie, heroLogoCache);
            return preloadImage(logoUrl);
        };

        const applyHeroLogo = async (movie, token, { preserveExistingLogo = true } = {}) => {
            if (!movie) {
                return;
            }

            const name = movie.name || "Phim noi bat";
            try {
                const logoUrl = await getPreloadedHeroLogo(movie);
                if (token === renderToken) {
                    if (!logoUrl && preserveExistingLogo && heroTitle?.classList.contains("has-logo")) {
                        return;
                    }
                    renderHeroTitle(heroTitle, name, logoUrl);
                }
            } catch (error) {
                console.error("Load TMDB hero logo failed:", error);
                if (token === renderToken) {
                    if (preserveExistingLogo && heroTitle?.classList.contains("has-logo")) {
                        return;
                    }
                    renderHeroTitle(heroTitle, name, "");
                }
            }
        };

        const updateHeroContent = (movie, { resetTitle = true } = {}) => {
            if (!movie) {
                return;
            }

            const name = movie.name || "Phim noi bat";
            const originalName = movie.origin_name || movie.original_name || "TF-Phim Spotlight";
            currentHeroMovie = movie;

            if (resetTitle || !heroTitle?.classList.contains("has-logo")) {
                renderHeroTitle(heroTitle, name, "");
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
                if (items.length <= 1) {
                    heroThumbs.hidden = true;
                    heroThumbs.innerHTML = "";
                } else {
                    heroThumbs.hidden = false;
                    heroThumbs.innerHTML = renderHeroThumbs(items, activeIndex);
                    heroThumbs.querySelectorAll("[data-hero-index]").forEach((button) => {
                        button.addEventListener("click", () => {
                            renderSlide(Number(button.dataset.heroIndex));
                            restartAutoRotate();
                        });
                    });
                }
            }
        };

        const getHeroMovieWithContent = async (movie) => {
            if (!movie?.slug || movie.content) {
                return movie;
            }

            if (heroDetailCache.has(movie.slug)) {
                return mergeMovieData(movie, await heroDetailCache.get(movie.slug));
            }

            const detailRequest = fetchMovieDetail(movie.slug, movie.source)
                .then((detailMovie) => {
                    const resolvedDetailMovie = detailMovie || {};
                    heroDetailCache.set(movie.slug, resolvedDetailMovie);
                    return resolvedDetailMovie;
                })
                .catch((error) => {
                    heroDetailCache.delete(movie.slug);
                    throw error;
                });
            heroDetailCache.set(movie.slug, detailRequest);
            const detailMovie = await detailRequest;
            return mergeMovieData(movie, detailMovie || {});
        };

        const warmHeroIndexes = new Set();
        const warmHeroMovieAssets = (index, includeDetail = false) => {
            const movie = items[index];
            if (!movie) {
                return;
            }

            preloadImage(getHeroImage(movie));
            getPreloadedHeroLogo(movie).catch((error) => console.error("Preload TMDB hero logo failed:", error));

            if (includeDetail && movie.slug) {
                getHeroMovieWithContent(movie)
                    .then((movieWithContent) => getPreloadedHeroLogo(movieWithContent))
                    .catch((error) => console.error("Preload hero content failed:", error));
            }
        };

        const warmUpcomingHeroAssets = (index) => {
            [0, 1, 2].forEach((offset) => {
                const nextIndex = (index + offset) % items.length;
                if (warmHeroIndexes.has(nextIndex)) {
                    return;
                }

                warmHeroIndexes.add(nextIndex);
                window.setTimeout(() => {
                    warmHeroMovieAssets(nextIndex, offset <= 1);
                }, offset * 120);
            });
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
            const movieWithContentPromise = getHeroMovieWithContent(movie)
                .catch((error) => {
                    console.error("Load hero content failed:", error);
                    return movie;
                });
            syncBackdrop(movie, immediate);
            warmUpcomingHeroAssets(activeIndex);

            if (!heroCopy || immediate) {
                updateHeroContent(movie);
                applyHeroLogo(movie, currentToken);
                movieWithContentPromise
                    .then((movieWithContent) => {
                        if (currentToken === renderToken) {
                            updateHeroContent(movieWithContent, { resetTitle: false });
                            applyHeroLogo(movieWithContent, currentToken);
                        }
                    });
                if (heroCopy) {
                    heroCopy.classList.remove("is-transitioning", "is-visible");
                    void heroCopy.offsetWidth;
                    heroCopy.classList.add("is-visible");
                }
                return;
            }

            heroCopy.classList.remove("is-visible");
            heroCopy.classList.add("is-transitioning");
            updateHeroContent(movie);
            applyHeroLogo(movie, currentToken);

            movieWithContentPromise
                .then((movieWithContent) => {
                    if (currentToken !== renderToken) {
                        return;
                    }

                    updateHeroContent(movieWithContent, { resetTitle: false });
                    applyHeroLogo(movieWithContent, currentToken);
                });

            window.setTimeout(() => {
                if (currentToken !== renderToken) {
                    return;
                }

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

        const getHeroShareUrl = (movie) => {
            if (!movie?.slug) {
                return window.location.href;
            }
            return new URL(`/phim/${movie.slug}`, window.location.origin).toString();
        };

        const copyHeroShareUrl = async (url) => {
            if (navigator.clipboard?.writeText) {
                await navigator.clipboard.writeText(url);
                return;
            }

            const textarea = document.createElement("textarea");
            textarea.value = url;
            textarea.setAttribute("readonly", "");
            textarea.style.position = "fixed";
            textarea.style.opacity = "0";
            textarea.style.pointerEvents = "none";
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand("copy");
            textarea.remove();
        };

        const shareActiveHeroMovie = async () => {
            const movie = currentHeroMovie || items[activeIndex];
            const url = getHeroShareUrl(movie);
            const title = movie?.name || "TF-Phim";
            const text = getMovieDescription(movie) ? cleanTextSnippet(getMovieDescription(movie), 120) : title;

            try {
                if (navigator.share) {
                    await navigator.share({ title, text, url });
                    return;
                }

                await copyHeroShareUrl(url);
                heroShareBtn?.classList.add("is-copied");
                window.setTimeout(() => heroShareBtn?.classList.remove("is-copied"), 900);
                showToast("Da sao chep lien ket phim.");
            } catch (error) {
                if (error?.name === "AbortError") {
                    return;
                }
                console.error("Share hero movie failed:", error);
                showToast("Khong the chia se phim luc nay.", "error");
            }
        };

        const startAutoRotate = () => {
            if (items.length <= 1) {
                return;
            }
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

        if (heroShareBtn) {
            heroShareBtn.addEventListener("click", () => {
                shareActiveHeroMovie();
                restartAutoRotate();
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

function renderMovies(containerId, movies, emptyMessage = "Khong tim thay phim.", listingType = "") {
    const grid = byId(containerId);
    if (!grid) {
        return;
    }

    if (!movies.length) {
        grid.innerHTML = `<div class="loading-spinner">${emptyMessage}</div>`;
        return;
    }

    grid.innerHTML = movies.map((movie, index) => createMovieCard(movie, listingType, index)).join("");
    grid.querySelectorAll(".movie-card-link.is-disabled").forEach((link) => {
        link.addEventListener("click", (event) => {
            event.preventDefault();
        });
    });
    bindMovieHoverPopup(grid);
}

function getJapanMazeEpisodeNumber(movie, episodeLabel = "") {
    const values = [
        movie?.total_episodes,
        movie?.episode_total,
        movie?.current_episode,
        movie?.episode_current,
        episodeLabel
    ];

    for (const value of values) {
        const number = extractEpisodeCountFromLabel(value);
        if (number > 0) {
            return String(number);
        }
    }

    return "";
}

function getJapanMazeEpisodeBadges(movie, episodeLabel) {
    const episodeNumber = getJapanMazeEpisodeNumber(movie, episodeLabel);
    const languageBadges = getLanguageBadges(getMovieLanguageBadgeSource(movie));
    const isSeriesMovie = isSeriesCardMovie(movie);
    const badgeLabelMap = {
        "lang-sub": "P\u0110.",
        "lang-dub": "TM.",
        "lang-voice": "LT."
    };

    if (languageBadges.length) {
        if (!isSeriesMovie) {
            return languageBadges.map((badge) => (
                `<span class="japan-maze-episode-badge ${escapeHtml(badge.className)}">${escapeHtml(badge.label)}</span>`
            )).join("");
        }

        return languageBadges.slice(0, 2).map((badge) => {
            const label = badgeLabelMap[badge.className] || badge.label;
            const value = episodeNumber || episodeLabel;
            return `<span class="japan-maze-episode-badge ${escapeHtml(badge.className)}">${escapeHtml(label)} ${escapeHtml(value)}</span>`;
        }).join("");
    }

    return isSeriesMovie && episodeLabel
        ? `<span class="japan-maze-episode-badge">${escapeHtml(episodeLabel)}</span>`
        : "";
}

function createJapanMazeCard(movie, index = 0) {
    const rank = index + 1;
    const slug = movie.slug || "";
    const name = movie.name || "Khong ro";
    const originalName = getMovieOriginalName(movie);
    const image = getMovieImage(movie);
    const episodeLabel = getCardEpisodeLabel(movie, "phim-bo");
    const episodeNumber = getJapanMazeEpisodeNumber(movie, episodeLabel);
    const episodeMeta = episodeNumber ? `T\u1eadp ${episodeNumber}` : episodeLabel;
    const ageRating = getMovieAgeRating(movie) || "T13";
    const href = slug ? `/phim/${encodeURIComponent(slug)}` : "#";
    const disabledClass = slug ? "" : " is-disabled";
    const datasetAttrs = createMovieDatasetAttributes(movie);

    return `
        <a class="japan-maze-card-link${disabledClass}" href="${escapeHtml(href)}" ${slug ? "" : 'aria-disabled="true" tabindex="-1"'}>
            <article class="japan-maze-card movie-card" ${datasetAttrs}>
                <div class="japan-maze-poster">
                    <img src="${escapeHtml(image)}" alt="${escapeHtml(name)}" width="360" height="540"
                         sizes="(max-width: 640px) 62vw, (max-width: 1200px) 32vw, 340px"
                         loading="${index < 5 ? "eager" : "lazy"}" decoding="async" fetchpriority="${index < 5 ? "high" : "low"}"
                         onerror="this.src='https://via.placeholder.com/360x540?text=No+Image'">
                    <div class="japan-maze-poster-shade" aria-hidden="true"></div>
                    <div class="japan-maze-episode-badges">${getJapanMazeEpisodeBadges(movie, episodeLabel)}</div>
                </div>
                <div class="japan-maze-info">
                    <span class="japan-maze-rank" data-rank="${escapeHtml(String(rank))}">${rank}</span>
                    <span class="japan-maze-copy">
                        <span class="japan-maze-title" title="${escapeHtml(name)}">${escapeHtml(name)}</span>
                        ${originalName ? `<span class="japan-maze-original" title="${escapeHtml(originalName)}">${escapeHtml(originalName)}</span>` : ""}
                        <span class="japan-maze-meta">
                            <strong>${escapeHtml(ageRating)}</strong>
                            ${episodeMeta ? `<span aria-hidden="true"></span><em>${escapeHtml(episodeMeta)}</em>` : ""}
                        </span>
                    </span>
                </div>
            </article>
        </a>
    `;
}

function getJapanMazeScrollStep(strip) {
    const firstCard = strip?.querySelector(".japan-maze-card-link");
    if (!firstCard) {
        return strip?.clientWidth || 0;
    }

    const stripStyles = window.getComputedStyle(strip);
    const gap = Number.parseFloat(stripStyles.columnGap || stripStyles.gap || "0") || 0;
    const cardWidth = firstCard.getBoundingClientRect().width;
    return cardWidth + gap;
}

function bindJapanMazeControls(strip) {
    if (!(strip instanceof HTMLElement)) {
        return;
    }

    const section = strip.closest(".japan-maze-section");
    const controls = section?.querySelector(".japan-maze-controls");
    const prevButton = section?.querySelector("[data-japan-maze-prev]");
    const nextButton = section?.querySelector("[data-japan-maze-next]");
    if (!controls || !prevButton || !nextButton) {
        return;
    }

    const syncControlHeight = () => {
        const poster = strip.querySelector(".japan-maze-poster");
        const posterHeight = poster?.getBoundingClientRect().height || 0;
        if (posterHeight > 0) {
            controls.style.setProperty("--japan-maze-control-height", `${Math.round(posterHeight)}px`);
        }
    };

    const updateControls = () => {
        syncControlHeight();
        const maxScroll = Math.max(0, strip.scrollWidth - strip.clientWidth - 1);
        const canScroll = maxScroll > 1;
        controls.hidden = !canScroll;
        prevButton.disabled = !canScroll || strip.scrollLeft <= 1;
        nextButton.disabled = !canScroll || strip.scrollLeft >= maxScroll;
    };

    strip.japanMazeUpdateControls = updateControls;

    if (strip.dataset.japanMazeControlsBound === "true") {
        window.requestAnimationFrame(updateControls);
        return;
    }

    const moveByCard = (direction) => {
        strip.scrollBy({
            left: direction * getJapanMazeScrollStep(strip),
            behavior: "smooth"
        });
    };

    prevButton.addEventListener("click", () => moveByCard(-1));
    nextButton.addEventListener("click", () => moveByCard(1));
    strip.addEventListener("scroll", () => {
        window.requestAnimationFrame(updateControls);
    }, { passive: true });
    window.addEventListener("resize", updateControls);

    strip.dataset.japanMazeControlsBound = "true";
    window.requestAnimationFrame(updateControls);
}

function renderJapanMazeMovies(containerId, movies, emptyMessage = "Khong tim thay phim Nhat.") {
    const strip = byId(containerId);
    if (!strip) {
        return;
    }

    if (!movies.length) {
        strip.innerHTML = `<div class="loading-spinner">${emptyMessage}</div>`;
        bindJapanMazeControls(strip);
        return;
    }

    strip.innerHTML = movies.slice(0, 10).map(createJapanMazeCard).join("");
    strip.querySelectorAll(".japan-maze-card-link.is-disabled").forEach((link) => {
        link.addEventListener("click", (event) => {
            event.preventDefault();
        });
    });
    bindMovieHoverPopup(strip);
    bindJapanMazeControls(strip);
}

async function fetchManagedJapanMazeMovies() {
    try {
        const data = await getJson("/api/quan-ly-phim/japan-maze?enabledOnly=true");
        return getMovieItems(data);
    } catch (error) {
        console.warn("Load managed Japan maze movies failed:", error);
        return [];
    }
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
                            ${faIcon("fa-play")}
                            <span>Xem ngay</span>
                        </a>
                        <button type="button" class="movie-hover-popup__btn" data-popup-action="like">
                            ${faIcon("fa-heart")}
                            <span>Thích</span>
                        </button>
                        <a class="movie-hover-popup__btn" data-popup-action="detail" href="#">
                            ${faIcon("fa-circle-info")}
                            <span>Chi tiết</span>
                        </a>
                    </div>
                    <div class="movie-hover-popup__chips"></div>
                    <p class="movie-hover-popup__status"></p>
                </div>
            `;
            document.body.appendChild(popup);
        }

        let suppressHideUntil = 0;

        const isInsidePopup = (target) => target instanceof Node && popup.contains(target);
        const isInsideActiveCardArea = (target) => {
            if (!(target instanceof Node) || !activeCard) {
                return false;
            }

            const activeLink = activeCard.closest(".movie-card-link, .japan-maze-card-link");
            return activeCard.contains(target) || Boolean(activeLink?.contains(target));
        };
        const getCardArea = (card) => card?.querySelector(".japan-maze-poster, .card-thumb")
            || card?.closest(".movie-card-link, .japan-maze-card-link")
            || card;
        const getCardRect = (card) => getCardArea(card)?.getBoundingClientRect();
        const clampPosition = (value, min, max) => Math.max(min, Math.min(value, max));
        const isCardVisible = (card) => {
            const rect = getCardRect(card);
            if (!card?.isConnected || !rect) {
                return false;
            }

            const visibleWidth = Math.min(rect.right, window.innerWidth) - Math.max(rect.left, 0);
            const visibleHeight = Math.min(rect.bottom, window.innerHeight) - Math.max(rect.top, 0);
            return visibleWidth > 8 && visibleHeight > 8;
        };

        const setPopupPosition = (card) => {
            const rect = getCardRect(card);
            if (!rect) {
                return;
            }

            const popupRect = popup.getBoundingClientRect();
            const popupWidth = popupRect.width || 420;
            const popupHeight = popupRect.height || 560;
            const viewportGutter = 16;
            const cardOverlap = 2;
            const scrollX = window.scrollX || document.documentElement.scrollLeft || 0;
            const scrollY = window.scrollY || document.documentElement.scrollTop || 0;
            const minLeft = scrollX + viewportGutter;
            const maxLeft = Math.max(minLeft, scrollX + window.innerWidth - popupWidth - viewportGutter);
            const maxTop = Math.max(0, document.documentElement.scrollHeight - popupHeight);
            const rightLeft = scrollX + rect.right - cardOverlap;
            const leftLeft = scrollX + rect.left - popupWidth + cardOverlap;
            let left = rightLeft;

            if (rightLeft + popupWidth > scrollX + window.innerWidth - viewportGutter && leftLeft >= minLeft) {
                left = leftLeft;
            } else if (rightLeft + popupWidth > scrollX + window.innerWidth - viewportGutter) {
                left = clampPosition(scrollX + rect.left + (rect.width - popupWidth) / 2, minLeft, maxLeft);
            }

            const top = clampPosition(scrollY + rect.top + (rect.height - popupHeight) / 2, viewportGutter, maxTop);

            popup.style.left = `${Math.round(left)}px`;
            popup.style.top = `${Math.round(top)}px`;
        };

        const setPopupContent = (movie) => {
            const name = movie.name || "Khong ro";
            const originalName = getMovieOriginalName(movie);
            const image = resolveMovieImageUrl(movie.thumb_url, movie.source) || resolveMovieImageUrl(movie.poster_url, movie.source) || "https://via.placeholder.com/600x900?text=No+Image";
            const quality = movie.quality || "";
            const ageRating = getMovieAgeRating(movie);
            const year = getMovieYear(movie);
            const duration = getMovieDuration(movie);
            const categoryLine = getMovieCategoryLine(movie);
            const slug = movie.slug || "";
            const currentEpisode = String(movie.episode_current || movie.current_episode || "").trim();
            const detailHref = slug ? `/phim/${encodeURIComponent(slug)}` : "#";
            const trailerUrl = String(movie.trailer_url || "").trim();
            const isTrailerMovie = currentEpisode.toLowerCase() === "trailer";
            const watchLabel = isTrailerMovie ? "Xem trailer" : "Xem ngay";
            const watchHref = isTrailerMovie
                ? (trailerUrl || detailHref)
                : (slug ? `/xem/${encodeURIComponent(slug)}` : "#");
            const chips = [
                createRatingBadges(movie, "movie-hover-popup__chip"),
                quality ? `<span class="movie-hover-popup__chiphd">${escapeHtml(quality)}</span>` : "",
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
                statusEl.textContent = categoryLine;
                statusEl.hidden = !categoryLine;
            }
            if (watchEl) {
                watchEl.href = watchHref;
                const hasWatchHref = watchHref && watchHref !== "#";
                watchEl.classList.toggle("is-disabled", !hasWatchHref);
                watchEl.setAttribute("aria-disabled", String(!hasWatchHref));
                const watchTextEl = watchEl.querySelector("span");
                if (watchTextEl) {
                    watchTextEl.textContent = watchLabel;
                }
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
            popup.classList.add("is-hiding");
            popup.classList.remove("is-visible");
            activeCard?.classList.remove("is-hover-popup-active");
            activeCard = null;
        };

        const showPopup = async (card) => {
            if (!desktopQuery.matches || !card?.dataset.slug || !isCardVisible(card)) {
                return;
            }

            const baseMovie = readMovieDataFromCard(card);
            activeCard?.classList.remove("is-hover-popup-active");
            activeCard = card;
            activeCard.classList.add("is-hover-popup-active");

            setPopupContent(baseMovie);
            popup.classList.remove("is-hiding");
            popup.classList.add("is-visible");
            popup.style.visibility = "hidden";
            setPopupPosition(card);
            popup.style.visibility = "";

            try {
                const detailCacheKey = `${baseMovie.source || ""}|${baseMovie.slug}`;
                const cachedDetail = detailCache.get(detailCacheKey);
                const detailMovie = cachedDetail || await fetchMovieDetail(baseMovie.slug, baseMovie.source);
                if (!cachedDetail) {
                    detailCache.set(detailCacheKey, detailMovie);
                }

                if (activeCard !== card) {
                    return;
                }

                setPopupContent(mergeMovieData(baseMovie, detailMovie));
                if (isCardVisible(card)) {
                    setPopupPosition(card);
                } else {
                    hidePopup();
                }
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
            if (activeCard === card && popup.classList.contains("is-visible")) {
                return;
            }

            showTimer = window.setTimeout(() => {
                if (!isCardVisible(card)) {
                    return;
                }

                showPopup(card);
            }, 850);

            const slug = card.dataset.slug;
            const source = normalizeSourceParam(card.dataset.source || "");
            const detailCacheKey = `${source || ""}|${slug}`;
            if (slug && !detailCache.has(detailCacheKey)) {
                fetchMovieDetail(slug, source)
                    .then((detailMovie) => detailCache.set(detailCacheKey, detailMovie))
                    .catch((error) => console.error("Preload movie hover detail failed:", error));
            }
        };

        const scheduleHide = (event) => {
            showTimer = clearTimer(showTimer);
            hideTimer = clearTimer(hideTimer);
            if (Date.now() < suppressHideUntil) {
                return;
            }

            const relatedTarget = event?.relatedTarget;
            if (isInsidePopup(relatedTarget) || isInsideActiveCardArea(relatedTarget)) {
                return;
            }

            hidePopup();
        };

        popup.addEventListener("mouseenter", () => {
            hideTimer = clearTimer(hideTimer);
        });
        popup.addEventListener("mouseleave", (event) => {
            scheduleHide(event);
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
            suppressHideUntil = Date.now() + 180;
            if (activeCard && popup.classList.contains("is-visible") && !isCardVisible(activeCard)) {
                hidePopup();
            }
        }, { capture: true, passive: true });
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
        card.addEventListener("mouseleave", (event) => bindMovieHoverPopup.state.scheduleHide(event));

        const link = card.closest(".movie-card-link, .japan-maze-card-link");
        if (link) {
            link.addEventListener("mouseleave", (event) => bindMovieHoverPopup.state.scheduleHide(event));
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

async function loadSection(type, containerId, options = {}) {
    const grid = byId(containerId);
    if (!grid) {
        return;
    }

    try {
        const limit = options.limit || getHomeSectionLimit(grid);
        if (options.renderer === "japan-maze") {
            const managedMovies = await fetchManagedJapanMazeMovies();
            const movies = managedMovies.length ? managedMovies : await fetchMoviesWithLimit(type, limit);
            renderJapanMazeMovies(containerId, movies);
            return;
        }

        const movies = await fetchMoviesWithLimit(type, limit);
        renderMovies(containerId, movies, "Khong tim thay phim.", type);
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
        { type: "phim-nhat-ban", containerId: "phimNhatBanMaze", sectionId: "section-phim-nhat-ban", limit: 10, renderer: "japan-maze" },
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
    const getSectionLimit = (section) => section.limit || getHomeSectionLimit(byId(section.containerId));
    const loadOnce = (section) => {
        if (!section || loadedSections.has(section.containerId)) {
            return;
        }

        loadedSections.add(section.containerId);
        const limit = getSectionLimit(section);
        loadedSectionLimits.set(section.containerId, limit);
        loadSection(section.type, section.containerId, {
            limit,
            renderer: section.renderer
        });
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

                const nextLimit = getSectionLimit(section);
                const prevLimit = loadedSectionLimits.get(section.containerId);
                if (nextLimit === prevLimit) {
                    return;
                }

                loadedSectionLimits.set(section.containerId, nextLimit);
                loadSection(section.type, section.containerId, {
                    limit: nextLimit,
                    renderer: section.renderer
                });
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
                    Xem kết quả đầy đủ ${faIcon("fa-arrow-right")}
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
            serverBlock.dispatchEvent(new CustomEvent("episode-page-change", {
                bubbles: true,
                detail: { pageNumber: normalizedPage }
            }));
        };

        rangeTabs.forEach((tab) => {
            tab.addEventListener("click", () => {
                setPage(tab.dataset.pageTarget || "1");
            });
        });

        setPage(activePage);
    });
}

function bindDetailContentTabs() {
    document.querySelectorAll(".detail-content-tabs").forEach((tabsRoot) => {
        const tabButtons = Array.from(tabsRoot.querySelectorAll("[data-detail-tab]"));
        const panelScope = tabsRoot.parentElement;
        if (!tabButtons.length || !panelScope) {
            return;
        }

        const panels = Array.from(panelScope.querySelectorAll("[data-detail-panel]"));

        const setActiveTab = (target) => {
            tabButtons.forEach((button) => {
                const isActive = button.dataset.detailTab === target;
                button.classList.toggle("active", isActive);
                button.setAttribute("aria-selected", String(isActive));
            });

            panels.forEach((panel) => {
                const isActive = panel.dataset.detailPanel === target;
                panel.classList.toggle("active", isActive);
                panel.hidden = !isActive;
            });
        };

        tabButtons.forEach((button) => {
            button.addEventListener("click", () => {
                setActiveTab(button.dataset.detailTab);
            });
        });

        const activeButton = tabButtons.find((button) => button.classList.contains("active")) || tabButtons[0];
        setActiveTab(activeButton.dataset.detailTab);
    });
}

function bindDetailGalleryLightbox() {
    const galleryItems = Array.from(document.querySelectorAll(".detail-image-gallery-item[data-gallery-src]"));
    if (!galleryItems.length) {
        return;
    }

    const images = galleryItems
        .map((item) => ({
            src: item.dataset.gallerySrc || "",
            alt: item.querySelector("img")?.alt || "Ảnh phim"
        }))
        .filter((item) => item.src);
    if (!images.length) {
        return;
    }

    let lightbox = document.querySelector(".image-lightbox");
    if (!lightbox) {
        lightbox = document.createElement("div");
        lightbox.className = "image-lightbox";
        lightbox.setAttribute("role", "dialog");
        lightbox.setAttribute("aria-modal", "true");
        lightbox.setAttribute("aria-label", "Xem ảnh phim");
        lightbox.innerHTML = `
            <div class="image-lightbox__top">
                <span class="image-lightbox__counter"></span>
                <button type="button" class="image-lightbox__close" aria-label="Đóng">
                    ${faIcon("fa-xmark")}
                </button>
            </div>
            <div class="image-lightbox__stage">
                <img class="image-lightbox__image" src="" alt="">
            </div>
            <div class="image-lightbox__bottom">
                <button type="button" class="image-lightbox__nav" data-lightbox-prev aria-label="Ảnh trước">
                    ${faIcon("fa-chevron-left")}
                </button>
                <button type="button" class="image-lightbox__nav" data-lightbox-next aria-label="Ảnh tiếp theo">
                    ${faIcon("fa-chevron-right")}
                </button>
            </div>
        `;
        document.body.appendChild(lightbox);
    }

    const imageEl = lightbox.querySelector(".image-lightbox__image");
    const counterEl = lightbox.querySelector(".image-lightbox__counter");
    const closeButton = lightbox.querySelector(".image-lightbox__close");
    const prevButton = lightbox.querySelector("[data-lightbox-prev]");
    const nextButton = lightbox.querySelector("[data-lightbox-next]");
    let activeIndex = 0;

    const renderImage = () => {
        const image = images[activeIndex];
        if (!image || !imageEl) {
            return;
        }
        imageEl.src = image.src;
        imageEl.alt = image.alt;
        if (counterEl) {
            counterEl.textContent = `${activeIndex + 1} / ${images.length}`;
        }
    };

    const openLightbox = (index) => {
        activeIndex = Math.max(0, Math.min(index, images.length - 1));
        renderImage();
        lightbox.classList.add("is-open");
        document.body.classList.add("is-lightbox-open");
        closeButton?.focus();
    };

    const closeLightbox = () => {
        lightbox.classList.remove("is-open");
        document.body.classList.remove("is-lightbox-open");
    };

    const goToRelativeImage = (direction) => {
        activeIndex = (activeIndex + direction + images.length) % images.length;
        renderImage();
    };

    galleryItems.forEach((item, index) => {
        item.addEventListener("click", () => openLightbox(index));
    });

    closeButton?.addEventListener("click", closeLightbox);
    prevButton?.addEventListener("click", () => goToRelativeImage(-1));
    nextButton?.addEventListener("click", () => goToRelativeImage(1));
    lightbox.addEventListener("click", (event) => {
        if (event.target === lightbox || event.target.classList.contains("image-lightbox__stage")) {
            closeLightbox();
        }
    });
    document.addEventListener("keydown", (event) => {
        if (!lightbox.classList.contains("is-open")) {
            return;
        }
        if (event.key === "Escape") {
            closeLightbox();
        } else if (event.key === "ArrowLeft") {
            goToRelativeImage(-1);
        } else if (event.key === "ArrowRight") {
            goToRelativeImage(1);
        }
    });
}

function bindEpisodePartDropdownV2() {
    const dropdowns = Array.from(document.querySelectorAll(".episode-browser [data-episode-part-dropdown]"));
    if (!dropdowns.length) return;

    const closeSiblingDropdowns = (browser, exceptDropdown) => {
        if (!browser) return;

        browser.querySelectorAll("[data-episode-part-dropdown], [data-episode-server-dropdown]").forEach((dropdown) => {
            if (dropdown !== exceptDropdown) {
                const toggle = dropdown.querySelector(".episode-part-dropdown-toggle, .episode-server-dropdown-toggle");
                const menu = dropdown.querySelector(".episode-part-dropdown-menu, .episode-server-dropdown-menu");
                if (!toggle || !menu) return;
                dropdown.classList.remove("is-open");
                toggle.setAttribute("aria-expanded", "false");
                menu.hidden = true;
            }
        });
    };

    const closeDropdown = (dropdown) => {
        const toggle = dropdown.querySelector(".episode-part-dropdown-toggle");
        const menu = dropdown.querySelector(".episode-part-dropdown-menu");
        if (!toggle || !menu) return;
        dropdown.classList.remove("is-open");
        toggle.setAttribute("aria-expanded", "false");
        menu.hidden = true;
    };

    const syncDropdown = (dropdown) => {
        const browser = dropdown.closest(".episode-browser");
        const label = dropdown.querySelector(".episode-part-dropdown-label");
        const itemsContainer = dropdown.querySelector(".episode-part-dropdown-items");
        if (!browser || !label || !itemsContainer) return;

        const activeServer = browser.querySelector(".episode-server.active") || browser.querySelector(".episode-server");
        if (!activeServer) return;

        const currentPage = String(activeServer.dataset.activePage || "1");
        label.textContent = `Phần ${currentPage}`;
        itemsContainer.innerHTML = "";

        const tabs = Array.from(activeServer.querySelectorAll(".episode-range-tab"));
        if (!tabs.length) {
            const fallback = document.createElement("button");
            fallback.type = "button";
            fallback.className = "episode-part-dropdown-item active";
            fallback.textContent = "Phần 1";
            fallback.addEventListener("click", (event) => event.preventDefault());
            itemsContainer.appendChild(fallback);
            return;
        }

        tabs.forEach((tab, index) => {
            const page = String(tab.dataset.pageTarget || index + 1);
            const item = document.createElement("button");
            item.type = "button";
            item.className = "episode-part-dropdown-item";
            item.textContent = `Phần ${page}`;
            item.classList.toggle("active", tab.classList.contains("active") || page === currentPage);
            item.addEventListener("click", (event) => {
                event.preventDefault();
                tab.click();
                closeDropdown(dropdown);
            });
            itemsContainer.appendChild(item);
        });
    };

    dropdowns.forEach((dropdown) => {
        const toggle = dropdown.querySelector(".episode-part-dropdown-toggle");
        const menu = dropdown.querySelector(".episode-part-dropdown-menu");
        const browser = dropdown.closest(".episode-browser");
        if (!toggle || !menu || !browser) return;

        toggle.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            const willOpen = !dropdown.classList.contains("is-open");
            closeSiblingDropdowns(browser, dropdown);

            if (willOpen) {
                syncDropdown(dropdown);
                dropdown.classList.add("is-open");
                toggle.setAttribute("aria-expanded", "true");
                menu.hidden = false;
            } else {
                closeDropdown(dropdown);
            }
        });

        browser.addEventListener("episode-browser-server-change", () => syncDropdown(dropdown));
        browser.addEventListener("episode-page-change", () => syncDropdown(dropdown));
        browser.querySelectorAll(".episode-server").forEach((serverBlock) => {
            serverBlock.addEventListener("episode-page-change", () => syncDropdown(dropdown));
        });

        closeDropdown(dropdown);
        syncDropdown(dropdown);
    });

    document.addEventListener("click", (event) => {
        dropdowns.forEach((dropdown) => {
            if (!dropdown.contains(event.target)) closeDropdown(dropdown);
        });
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") dropdowns.forEach((dropdown) => closeDropdown(dropdown));
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

            browser.dispatchEvent(new Event("episode-browser-server-change"));
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

function bindEpisodeServerDropdowns() {
    const dropdowns = Array.from(document.querySelectorAll("[data-episode-server-dropdown]"));
    if (!dropdowns.length) {
        return;
    }

    const closeSiblingDropdowns = (browser, exceptDropdown) => {
        if (!browser) return;

        browser.querySelectorAll("[data-episode-part-dropdown], [data-episode-server-dropdown]").forEach((dropdown) => {
            if (dropdown !== exceptDropdown) {
                const toggle = dropdown.querySelector(".episode-part-dropdown-toggle, .episode-server-dropdown-toggle");
                const menu = dropdown.querySelector(".episode-part-dropdown-menu, .episode-server-dropdown-menu");
                if (!toggle || !menu) return;
                dropdown.classList.remove("is-open");
                toggle.setAttribute("aria-expanded", "false");
                menu.hidden = true;
            }
        });
    };

    const closeDropdown = (dropdown) => {
        const toggle = dropdown.querySelector(".episode-server-dropdown-toggle");
        const menu = dropdown.querySelector(".episode-server-dropdown-menu");
        if (!toggle || !menu) {
            return;
        }

        dropdown.classList.remove("is-open");
        toggle.setAttribute("aria-expanded", "false");
        menu.hidden = true;
    };

    dropdowns.forEach((dropdown) => {
        const browser = dropdown.closest(".episode-browser");
        const toggle = dropdown.querySelector(".episode-server-dropdown-toggle");
        const label = dropdown.querySelector(".episode-server-dropdown-label");
        const menu = dropdown.querySelector(".episode-server-dropdown-menu");
        const itemsContainer = dropdown.querySelector(".episode-server-dropdown-items");
        if (!browser || !toggle || !label || !menu || !itemsContainer) {
            return;
        }

        const syncDropdown = () => {
            const tabs = Array.from(browser.querySelectorAll(".episode-server-tab"));
            const activeTab = browser.querySelector(".episode-server-tab.active") || tabs[0];
            if (!activeTab) {
                label.textContent = "Server 1";
                itemsContainer.innerHTML = "";
                return;
            }

                label.textContent = (activeTab.textContent || "").trim() || "Server 1";
                itemsContainer.innerHTML = "";

            tabs.forEach((tab) => {
                const item = document.createElement("button");
                item.type = "button";
                item.className = "episode-server-dropdown-item";
                item.textContent = (tab.textContent || "").trim();
                item.classList.toggle("active", tab.classList.contains("active"));
                item.addEventListener("click", (event) => {
                    event.preventDefault();
                    tab.click();
                    closeDropdown(dropdown);
                });
                itemsContainer.appendChild(item);
            });
        };

        toggle.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();

            const willOpen = !dropdown.classList.contains("is-open");
            closeSiblingDropdowns(browser, dropdown);

            if (willOpen) {
                syncDropdown();
                dropdown.classList.add("is-open");
                toggle.setAttribute("aria-expanded", "true");
                menu.hidden = false;
            } else {
                closeDropdown(dropdown);
            }
        });

        browser.addEventListener("episode-browser-server-change", syncDropdown);
        browser.addEventListener("click", (event) => {
            if (!dropdown.contains(event.target)) {
                closeDropdown(dropdown);
            }
        });

        syncDropdown();
        closeDropdown(dropdown);
    });

    document.addEventListener("click", (event) => {
        dropdowns.forEach((dropdown) => {
            if (!dropdown.contains(event.target)) {
                closeDropdown(dropdown);
            }
        });
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            dropdowns.forEach((dropdown) => closeDropdown(dropdown));
        }
    });
}

function bindMovieSourceSelects() {
    document.querySelectorAll("[data-movie-source-select]").forEach((select) => {
        select.addEventListener("change", () => {
            const targetUrl = select.value;
            if (targetUrl) {
                window.location.href = targetUrl;
            }
        });
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

function runInitSafely(name, initFn) {
    try {
        const result = initFn();
        if (result && typeof result.then === "function") {
            result.catch((error) => {
                console.error(`Init failed: ${name}`, error);
            });
        }
    } catch (error) {
        console.error(`Init failed: ${name}`, error);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    runInitSafely("bindSearchEvents", bindSearchEvents);
    runInitSafely("bindLiveSearch", bindLiveSearch);
    runInitSafely("bindCategoryLinks", bindCategoryLinks);
    runInitSafely("bindCategoryPagination", bindCategoryPagination);
    runInitSafely("bindDetailContentTabs", bindDetailContentTabs);
    runInitSafely("bindDetailGalleryLightbox", bindDetailGalleryLightbox);
    runInitSafely("bindEpisodePagination", bindEpisodePagination);
    runInitSafely("bindEpisodePartDropdownV2", bindEpisodePartDropdownV2);
    runInitSafely("bindEpisodeBrowser", bindEpisodeBrowser);
    runInitSafely("bindEpisodeServerDropdowns", bindEpisodeServerDropdowns);
    runInitSafely("bindMovieSourceSelects", bindMovieSourceSelects);
    runInitSafely("bindMobileNavbar", bindMobileNavbar);
    runInitSafely("bindMobileDetailInfoToggle", bindMobileDetailInfoToggle);
    runInitSafely("bindDesktopNavbarDropdown", bindDesktopNavbarDropdown);
    runInitSafely("bindNavbarScrollState", bindNavbarScrollState);
    runInitSafely("bindBackToTop", bindBackToTop);
    runInitSafely("bindMovieHoverPopup", bindMovieHoverPopup);
    runInitSafely("bindDetailFavoriteButton", bindDetailFavoriteButton);
    runInitSafely("bindFavoritePageActions", bindFavoritePageActions);
    runInitSafely("loadHeroSlider", loadHeroSlider);
    runInitSafely("initHomeSections", initHomeSections);
});

