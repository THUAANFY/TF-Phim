package tfphim.tfphim.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MovieApiService {
    private static final int DEFAULT_LIST_LIMIT = 24;
    private static final int SEARCH_FALLBACK_PAGE_COUNT = 3;
    private static final int SEARCH_RECENT_FALLBACK_PAGE_COUNT = 6;
    private static final int SEARCH_FALLBACK_ITEM_LIMIT = 72;
    private static final int SEARCH_FALLBACK_VARIANT_LIMIT = 8;
    private static final long MOVIE_LIST_TTL_MILLIS = 60 * 1000;
    private static final long MOVIE_DETAIL_TTL_MILLIS = 10 * 60 * 1000;
    private static final long SEARCH_TTL_MILLIS = 2 * 60 * 1000;
    private static final long TEXT_RESPONSE_TTL_MILLIS = 20 * 1000;
    private static final long COUNTRY_LIST_TTL_MILLIS = 10 * 60 * 1000;
    private static final long GENRE_LIST_TTL_MILLIS = 10 * 60 * 1000;
    private static final long TMDB_LOGO_TTL_MILLIS = 6 * 60 * 60 * 1000;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, CacheEntry<String>> movieListCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> movieDetailJsonCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Map<String, Object>>> movieDetailCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> textCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<Map<String, String>>>> countryListCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<Map<String, String>>>> genreListCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Map<String, String>>> tmdbLogoCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<Map<String, Object>>>> tmdbGalleryCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Map<String, String>>> tmdbActorImageCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> tmdbJsonCache = new ConcurrentHashMap<>();
    private static final String DEFAULT_KK_BASE_URL = "https://phimapi.com";
    private static final String DEFAULT_OPHIM_BASE_URL = "https://ophim1.com";
    private static final String DEFAULT_TMDB_BASE_URL = "https://api.themoviedb.org/3";
    private static final String TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original";
    private static final List<String> TMDB_LOGO_LANGUAGE_PRIORITY = List.of("vi", "en", "ko", "zh", "ja", "th", "null");
    private static final String TMDB_MATCH_CACHE_VERSION = "tmdb-match-v4";
    private static final String TMDB_GALLERY_CACHE_VERSION = "tmdb-gallery-v3";
    private static final String TMDB_ACTOR_CACHE_VERSION = "tmdb-actor-v5";
    private static final double TMDB_STRONG_TITLE_SCORE = 900;
    private static final double TMDB_WEAK_TITLE_SCORE = 650;
    private static final double TMDB_QUERY_TOKEN_SCORE = 140;
    private static final Map<String, String> MOVIE_PATHS = Map.of(
            "phim-moi", "/danh-sach/phim-moi-cap-nhat-v3",
            "phim-bo", "/v1/api/danh-sach/phim-bo",
            "phim-le", "/v1/api/danh-sach/phim-le",
            "hoat-hinh", "/v1/api/danh-sach/hoat-hinh"
    );
    private static final Map<String, String> OPHIM_MOVIE_PATHS = Map.of(
            "phim-moi", "/danh-sach/phim-moi-cap-nhat",
            "phim-bo", "/v1/api/danh-sach/phim-bo",
            "phim-le", "/v1/api/danh-sach/phim-le",
            "hoat-hinh", "/v1/api/danh-sach/hoat-hinh"
    );
    private final String kkBaseUrl;
    private final String ophimBaseUrl;
    private final String tmdbBaseUrl;
    private final String tmdbApiKey;

    public MovieApiService(
            @org.springframework.beans.factory.annotation.Value("${kkphim.api.base-url:https://phimapi.com}") String kkBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${ophim.api.base-url:https://ophim1.com}") String ophimBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${tmdb.api.base-url:https://api.themoviedb.org/3}") String tmdbBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${tmdb.api.key:}") String tmdbApiKey
    ) {
        this.kkBaseUrl = normalizeBaseUrl(kkBaseUrl, DEFAULT_KK_BASE_URL);
        this.ophimBaseUrl = normalizeBaseUrl(ophimBaseUrl, DEFAULT_OPHIM_BASE_URL);
        this.tmdbBaseUrl = normalizeBaseUrl(tmdbBaseUrl, DEFAULT_TMDB_BASE_URL);
        this.tmdbApiKey = tmdbApiKey == null ? "" : tmdbApiKey.trim();
    }

    public String getMovies(String type, int page) {
        if (type != null && type.startsWith("quoc-gia/")) {
            String countrySlug = type.substring("quoc-gia/".length()).trim();
            if (!countrySlug.isBlank()) {
                return getMoviesByCountry(countrySlug, page);
            }
        }

        if (type != null && type.startsWith("the-loai/")) {
            String genreSlug = type.substring("the-loai/".length()).trim();
            if (!genreSlug.isBlank()) {
                return getMoviesByGenre(genreSlug, page);
            }
        }

        String path = MOVIE_PATHS.getOrDefault(type, "/v1/api/danh-sach/" + type);
        String url = kkBaseUrl + path + "?page=" + page;
        if (path.startsWith("/v1/api/")) {
            url = url + "&limit=" + DEFAULT_LIST_LIMIT;
        }
        return fetchCachedJson(url, movieListCache, MOVIE_LIST_TTL_MILLIS);
    }

    public String getOphimMovies(String type, int page) {
        String path = OPHIM_MOVIE_PATHS.getOrDefault(type, "/v1/api/danh-sach/" + type);
        String url = ophimBaseUrl + path + "?page=" + Math.max(page, 1);
        if (path.startsWith("/v1/api/")) {
            url = url + "&limit=" + DEFAULT_LIST_LIMIT;
        }
        return fetchCachedJson(url, movieListCache, MOVIE_LIST_TTL_MILLIS);
    }

    public String searchMovies(String keyword, int page) {
        return searchMoviesWithFallback(keyword, page, false);
    }

    public String searchOphimMovies(String keyword, int page) {
        return searchMoviesWithFallback(keyword, page, true);
    }

    private String searchMoviesWithFallback(String keyword, int page, boolean ophimSource) {
        int safePage = Math.max(page, 1);
        String primaryResponse = fetchSearchPage(keyword, safePage, ophimSource);
        if (safePage > 1) {
            return primaryResponse;
        }

        Map<String, Object> primaryPayload = parseJsonObject(primaryResponse);
        LinkedHashMap<String, Map<String, Object>> mergedItems = new LinkedHashMap<>();
        appendSearchItems(mergedItems, primaryPayload);

        if (!shouldRunSearchFallback(mergedItems, keyword)) {
            return buildSearchResponse(primaryPayload, mergedItems, keyword, ophimSource);
        }

        for (String searchKeyword : buildSearchKeywordVariants(keyword)) {
            if (mergedItems.size() >= SEARCH_FALLBACK_ITEM_LIMIT) {
                break;
            }
            for (int fallbackPage = 1; fallbackPage <= SEARCH_FALLBACK_PAGE_COUNT; fallbackPage++) {
                if (mergedItems.size() >= SEARCH_FALLBACK_ITEM_LIMIT) {
                    break;
                }
                if (fallbackPage == 1 && normalizeQueryText(searchKeyword).equals(normalizeQueryText(keyword))) {
                    continue;
                }
                appendSearchItems(
                        mergedItems,
                        parseJsonObject(fetchSearchPage(searchKeyword, fallbackPage, ophimSource))
                );
            }
        }

        appendRecentUpdateFallbackItems(mergedItems, keyword, ophimSource);
        appendDetailFallbackItems(mergedItems, keyword, ophimSource);
        return buildSearchResponse(primaryPayload, mergedItems, keyword, ophimSource);
    }

    private boolean shouldRunSearchFallback(LinkedHashMap<String, Map<String, Object>> primaryItems, String keyword) {
        if (primaryItems.isEmpty()) {
            return true;
        }

        List<String> strictQueries = buildStrictSearchMatchQueries(keyword);
        if (strictQueries.isEmpty()) {
            return false;
        }

        for (Map<String, Object> item : primaryItems.values()) {
            if (matchesRecentFallbackKeyword(item, strictQueries)) {
                return false;
            }
        }

        return true;
    }

    private List<String> buildStrictSearchMatchQueries(String keyword) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addRecentFallbackMatchQuery(queries, keyword);
        String normalized = normalizeMatchText(keyword);
        if (!normalized.isBlank()) {
            addRecentFallbackMatchQuery(queries, normalized.replaceAll("\\s+", "-"));
        }
        return new ArrayList<>(queries);
    }

    private String fetchSearchPage(String keyword, int page, boolean ophimSource) {
        String baseUrl = ophimSource ? ophimBaseUrl : kkBaseUrl;
        String url = baseUrl + "/v1/api/tim-kiem?keyword={keyword}&page={page}&limit=" + DEFAULT_LIST_LIMIT;
        String encodedKeyword = org.springframework.web.util.UriUtils.encodeQueryParam(
                keyword == null ? "" : keyword.trim(),
                StandardCharsets.UTF_8
        );
        return fetchCachedJson(
                url.replace("{keyword}", encodedKeyword).replace("{page}", String.valueOf(Math.max(page, 1))),
                searchCache,
                SEARCH_TTL_MILLIS
        );
    }

    private List<String> buildSearchKeywordVariants(String keyword) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String normalizedKeyword = normalizeQueryText(keyword);
        addSearchKeywordVariant(variants, normalizedKeyword);
        addSearchKeywordVariant(variants, stripTmdbBracketText(normalizedKeyword));
        addSearchKeywordVariant(variants, removeTmdbTitleNoise(normalizedKeyword));

        String matchText = normalizeMatchText(normalizedKeyword);
        addSearchKeywordVariant(variants, matchText);
        addSearchKeywordVariant(variants, matchText.replaceAll("\\s+", "-"));

        for (String part : normalizedKeyword.split("\\s+(?:-|\\u2013|\\u2014)\\s+|\\s*[:|/]\\s*")) {
            addSearchKeywordVariant(variants, part);
            addSearchKeywordVariant(variants, removeTmdbTitleNoise(part));
            String normalizedPart = normalizeMatchText(part);
            addSearchKeywordVariant(variants, normalizedPart);
            addSearchKeywordVariant(variants, normalizedPart.replaceAll("\\s+", "-"));
        }

        return variants.stream()
                .limit(SEARCH_FALLBACK_VARIANT_LIMIT)
                .toList();
    }

    private void addSearchKeywordVariant(LinkedHashSet<String> variants, String keyword) {
        String normalized = normalizeQueryText(keyword);
        if (normalized.length() >= 2) {
            variants.add(normalized);
        }
    }

    private void appendSearchItems(
            LinkedHashMap<String, Map<String, Object>> mergedItems,
            Map<String, Object> payload
    ) {
        for (Map<String, Object> item : extractSearchItems(payload)) {
            appendSearchItem(mergedItems, item);
            if (mergedItems.size() >= SEARCH_FALLBACK_ITEM_LIMIT) {
                return;
            }
        }
    }

    private void appendSearchItem(
            LinkedHashMap<String, Map<String, Object>> mergedItems,
            Map<String, Object> item
    ) {
        if (item == null || item.isEmpty()) {
            return;
        }

        String key = searchItemKey(item);
        if (key.isBlank() || mergedItems.containsKey(key)) {
            return;
        }
        mergedItems.put(key, new LinkedHashMap<>(item));
    }

    private String searchItemKey(Map<String, Object> item) {
        String slug = normalizeQueryText(String.valueOf(item.getOrDefault("slug", ""))).toLowerCase();
        if (!slug.isBlank()) {
            return slug;
        }
        return normalizeMatchText(String.valueOf(item.getOrDefault("name", item.getOrDefault("title", ""))));
    }

    private List<Map<String, Object>> extractSearchItems(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Collections.emptyList();
        }

        Object directItems = payload.get("items");
        if (directItems instanceof List<?>) {
            return safeMapList(directItems);
        }

        Object rawData = payload.get("data");
        if (rawData instanceof Map<?, ?> dataMapRaw) {
            Map<String, Object> dataMap = safeMap(dataMapRaw);
            Object dataItems = dataMap.get("items");
            if (dataItems instanceof List<?>) {
                return safeMapList(dataItems);
            }
        }

        if (rawData instanceof List<?>) {
            return safeMapList(rawData);
        }

        return Collections.emptyList();
    }

    private void appendDetailFallbackItems(
            LinkedHashMap<String, Map<String, Object>> mergedItems,
            String keyword,
            boolean ophimSource
    ) {
        for (String slug : buildDetailSlugCandidates(keyword)) {
            if (mergedItems.size() >= SEARCH_FALLBACK_ITEM_LIMIT) {
                return;
            }
            try {
                Map<String, Object> payload = parseJsonObject(
                        ophimSource ? getOphimMovieDetail(slug) : getMovieDetail(slug)
                );
                Map<String, Object> movie = extractDetailMovie(payload);
                if (!movie.isEmpty()) {
                    appendSearchItem(mergedItems, movie);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void appendRecentUpdateFallbackItems(
            LinkedHashMap<String, Map<String, Object>> mergedItems,
            String keyword,
            boolean ophimSource
    ) {
        List<String> matchQueries = buildRecentFallbackMatchQueries(keyword);
        if (matchQueries.isEmpty()) {
            return;
        }

        for (int page = 1; page <= SEARCH_RECENT_FALLBACK_PAGE_COUNT; page++) {
            if (mergedItems.size() >= SEARCH_FALLBACK_ITEM_LIMIT) {
                return;
            }

            Map<String, Object> payload = parseJsonObject(
                    ophimSource ? getOphimMovies("phim-moi", page) : getMovies("phim-moi", page)
            );
            for (Map<String, Object> item : extractSearchItems(payload)) {
                if (mergedItems.size() >= SEARCH_FALLBACK_ITEM_LIMIT) {
                    return;
                }
                if (matchesRecentFallbackKeyword(item, matchQueries)) {
                    appendSearchItem(mergedItems, item);
                }
            }
        }
    }

    private List<String> buildRecentFallbackMatchQueries(String keyword) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        for (String variant : buildSearchKeywordVariants(keyword)) {
            addRecentFallbackMatchQuery(queries, variant);
            addRecentFallbackMatchQuery(queries, stripTmdbBracketText(variant));
            addRecentFallbackMatchQuery(queries, removeTmdbTitleNoise(variant));
        }
        return new ArrayList<>(queries);
    }

    private void addRecentFallbackMatchQuery(LinkedHashSet<String> queries, String value) {
        String query = normalizeMatchText(value);
        if (query.length() >= 2) {
            queries.add(query);
        }
    }

    private boolean matchesRecentFallbackKeyword(Map<String, Object> item, List<String> matchQueries) {
        String movieText = buildRecentFallbackMovieText(item);
        if (movieText.isBlank()) {
            return false;
        }

        for (String query : matchQueries) {
            if (query.isBlank()) {
                continue;
            }
            if (isDirectRecentFallbackMatch(movieText, query) || hasAllSearchTokens(movieText, query)) {
                return true;
            }
        }

        return false;
    }

    private String buildRecentFallbackMovieText(Map<String, Object> item) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String key : List.of(
                "slug",
                "name",
                "title",
                "origin_name",
                "original_name",
                "eng_name",
                "other_name"
        )) {
            String value = normalizeMatchText(String.valueOf(item.getOrDefault(key, "")));
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return String.join(" ", values);
    }

    private boolean isDirectRecentFallbackMatch(String movieText, String query) {
        if (movieText.equals(query)) {
            return true;
        }
        if (query.length() < 3) {
            return false;
        }
        return containsSearchPhrase(movieText, query);
    }

    private boolean containsSearchPhrase(String movieText, String query) {
        String paddedText = " " + movieText + " ";
        String paddedQuery = " " + query + " ";
        return paddedText.contains(paddedQuery);
    }

    private boolean hasAllSearchTokens(String movieText, String query) {
        List<String> tokens = new ArrayList<>();
        for (String token : query.split("\\s+")) {
            if (token.length() >= 2 || token.matches("\\d+")) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            return false;
        }
        if (tokens.size() == 1 && tokens.get(0).length() < 3 && !tokens.get(0).matches("\\d+")) {
            return false;
        }

        String paddedText = " " + movieText + " ";
        for (String token : tokens) {
            if (!paddedText.contains(" " + token + " ")) {
                return false;
            }
        }
        return true;
    }

    private List<String> buildDetailSlugCandidates(String keyword) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String variant : buildSearchKeywordVariants(keyword)) {
            String slug = normalizeMatchText(variant).replaceAll("\\s+", "-");
            if (slug.length() >= 2) {
                candidates.add(slug);
            }
        }
        return new ArrayList<>(candidates);
    }

    private Map<String, Object> extractDetailMovie(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Collections.emptyMap();
        }

        Object movie = payload.get("movie");
        if (movie instanceof Map<?, ?> movieMap && !movieMap.isEmpty()) {
            return safeMap(movieMap);
        }

        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMapRaw) {
            Object dataMovie = dataMapRaw.get("movie");
            if (dataMovie instanceof Map<?, ?> movieMap && !movieMap.isEmpty()) {
                return safeMap(movieMap);
            }
        }

        return Collections.emptyMap();
    }

    private String buildSearchResponse(
            Map<String, Object> primaryPayload,
            LinkedHashMap<String, Map<String, Object>> mergedItems,
            String keyword,
            boolean ophimSource
    ) {
        List<Map<String, Object>> items = new ArrayList<>(mergedItems.values());
        Map<String, Object> response = new LinkedHashMap<>(primaryPayload == null ? Collections.emptyMap() : primaryPayload);
        response.putIfAbsent("status", "success");
        response.putIfAbsent("message", "");
        response.put("items", items);
        response.put("count", items.size());
        response.put("source", ophimSource ? "ophim" : "kk");

        Map<String, Object> data = response.get("data") instanceof Map<?, ?> rawData
                ? new LinkedHashMap<>(safeMap(rawData))
                : new LinkedHashMap<>();
        data.put("items", items);
        data.putIfAbsent("titlePage", keyword);

        Map<String, Object> params = data.get("params") instanceof Map<?, ?> rawParams
                ? new LinkedHashMap<>(safeMap(rawParams))
                : new LinkedHashMap<>();
        Map<String, Object> pagination = params.get("pagination") instanceof Map<?, ?> rawPagination
                ? new LinkedHashMap<>(safeMap(rawPagination))
                : new LinkedHashMap<>();
        int upstreamTotalItems = Math.max(
                parsePositiveInt(pagination.get("totalItems")),
                parsePositiveInt(pagination.get("total_items"))
        );
        int totalItems = Math.max(upstreamTotalItems, items.size());
        pagination.put("currentPage", 1);
        pagination.put("totalItems", totalItems);
        pagination.put("totalItemsPerPage", Math.max(items.size(), DEFAULT_LIST_LIMIT));
        pagination.put("totalPages", Math.max(1, (int) Math.ceil((double) totalItems / Math.max(DEFAULT_LIST_LIMIT, 1))));
        params.put("pagination", pagination);
        data.put("params", params);
        response.put("data", data);

        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            return "{\"status\":\"success\",\"items\":[]}";
        }
    }

    public String getMovieDetail(String slug) {
        String url = kkBaseUrl + "/phim/{slug}";
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
        return fetchCachedJson(url.replace("{slug}", encodedSlug), movieDetailJsonCache, MOVIE_DETAIL_TTL_MILLIS);
    }

    public String getOphimMovieDetail(String slug) {
        String url = ophimBaseUrl + "/phim/{slug}";
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
        return fetchCachedJson(url.replace("{slug}", encodedSlug), movieDetailJsonCache, MOVIE_DETAIL_TTL_MILLIS);
    }

    public String getMoviesByCountry(String countrySlug, int page) {
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(countrySlug, StandardCharsets.UTF_8);
        String url = kkBaseUrl + "/v1/api/quoc-gia/" + encodedSlug + "?page=" + Math.max(page, 1) + "&limit=" + DEFAULT_LIST_LIMIT;
        return fetchCachedJson(url, movieListCache, MOVIE_LIST_TTL_MILLIS);
    }

    public String getOphimMoviesByCountry(String countrySlug, int page) {
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(countrySlug, StandardCharsets.UTF_8);
        String url = ophimBaseUrl + "/v1/api/quoc-gia/" + encodedSlug + "?page=" + Math.max(page, 1) + "&limit=" + DEFAULT_LIST_LIMIT;
        return fetchCachedJson(url, movieListCache, MOVIE_LIST_TTL_MILLIS);
    }

    public String getMoviesByGenre(String genreSlug, int page) {
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(genreSlug, StandardCharsets.UTF_8);
        String url = kkBaseUrl + "/v1/api/the-loai/" + encodedSlug + "?page=" + Math.max(page, 1) + "&limit=" + DEFAULT_LIST_LIMIT;
        return fetchCachedJson(url, movieListCache, MOVIE_LIST_TTL_MILLIS);
    }

    public String getOphimMoviesByGenre(String genreSlug, int page) {
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(genreSlug, StandardCharsets.UTF_8);
        String url = ophimBaseUrl + "/v1/api/the-loai/" + encodedSlug + "?page=" + Math.max(page, 1) + "&limit=" + DEFAULT_LIST_LIMIT;
        return fetchCachedJson(url, movieListCache, MOVIE_LIST_TTL_MILLIS);
    }

    public Map<String, Object> getMoviesData(String type, int page) {
        try {
            String response = getMovies(type, page);
            Map<String, Object> payload = objectMapper.readValue(
                    response,
                    new TypeReference<Map<String, Object>>() {}
            );
            return payload != null ? payload : Collections.emptyMap();
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> getOphimMoviesData(String type, int page) {
        return parseJsonObject(getOphimMovies(type, page));
    }

    public Map<String, Object> searchMoviesData(String keyword, int page) {
        try {
            String response = searchMovies(keyword, page);
            Map<String, Object> payload = objectMapper.readValue(
                    response,
                    new TypeReference<Map<String, Object>>() {}
            );
            return payload != null ? payload : Collections.emptyMap();
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> searchMoviesDataQuick(String keyword, int page) {
        return parseJsonObject(fetchSearchPage(keyword, page, false));
    }

    public Map<String, Object> searchOphimMoviesData(String keyword, int page) {
        return parseJsonObject(searchOphimMovies(keyword, page));
    }

    public Map<String, Object> searchOphimMoviesDataQuick(String keyword, int page) {
        return parseJsonObject(fetchSearchPage(keyword, page, true));
    }

    public Map<String, Object> getMoviesByCountryData(String countrySlug, int page) {
        try {
            String response = getMoviesByCountry(countrySlug, page);
            Map<String, Object> payload = objectMapper.readValue(
                    response,
                    new TypeReference<Map<String, Object>>() {}
            );
            return payload != null ? payload : Collections.emptyMap();
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> getOphimMoviesByCountryData(String countrySlug, int page) {
        return parseJsonObject(getOphimMoviesByCountry(countrySlug, page));
    }

    public Map<String, Object> getMoviesByGenreData(String genreSlug, int page) {
        try {
            String response = getMoviesByGenre(genreSlug, page);
            Map<String, Object> payload = objectMapper.readValue(
                    response,
                    new TypeReference<Map<String, Object>>() {}
            );
            return payload != null ? payload : Collections.emptyMap();
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> getOphimMoviesByGenreData(String genreSlug, int page) {
        return parseJsonObject(getOphimMoviesByGenre(genreSlug, page));
    }

    public List<Map<String, String>> getCountryOptions() {
        CacheEntry<List<Map<String, String>>> cached = countryListCache.get("countries");
        if (isFresh(cached)) {
            return cached.value();
        }

        try {
            String response = fetchCachedJson(kkBaseUrl + "/v1/api/quoc-gia", movieListCache, COUNTRY_LIST_TTL_MILLIS);
            List<Map<String, String>> parsed = extractTaxonomyOptionsFromJson(response);
            if (parsed.isEmpty()) {
                response = fetchCachedJson(kkBaseUrl + "/quoc-gia", movieListCache, COUNTRY_LIST_TTL_MILLIS);
                parsed = extractTaxonomyOptionsFromJson(response);
            }
            if (!parsed.isEmpty()) {
                countryListCache.put("countries", new CacheEntry<>(parsed, System.currentTimeMillis() + COUNTRY_LIST_TTL_MILLIS));
                return parsed;
            }
        } catch (Exception ignored) {
        }

        return cached != null ? cached.value() : Collections.emptyList();
    }

    public List<Map<String, String>> getGenreOptions() {
        CacheEntry<List<Map<String, String>>> cached = genreListCache.get("genres");
        if (isFresh(cached)) {
            return cached.value();
        }

        try {
            String response = fetchCachedJson(kkBaseUrl + "/v1/api/the-loai", movieListCache, GENRE_LIST_TTL_MILLIS);
            List<Map<String, String>> parsed = extractTaxonomyOptionsFromJson(response);
            if (parsed.isEmpty()) {
                response = fetchCachedJson(kkBaseUrl + "/the-loai", movieListCache, GENRE_LIST_TTL_MILLIS);
                parsed = extractTaxonomyOptionsFromJson(response);
            }
            if (!parsed.isEmpty()) {
                genreListCache.put("genres", new CacheEntry<>(parsed, System.currentTimeMillis() + GENRE_LIST_TTL_MILLIS));
                return parsed;
            }
        } catch (Exception ignored) {
        }

        return cached != null ? cached.value() : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMovieDetailData(String slug) {
        CacheEntry<Map<String, Object>> cached = movieDetailCache.get(slug);
        if (isFresh(cached)) {
            return cached.value();
        }

        try {
            String response = getMovieDetail(slug);
            Map<String, Object> payload = objectMapper.readValue(
                    response,
                    new TypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> result = payload != null ? payload : Collections.emptyMap();
            if (!result.isEmpty() && hasMoviePayload(result)) {
                result.putIfAbsent("source", "kkphim");
                movieDetailCache.put(slug, new CacheEntry<>(result, System.currentTimeMillis() + MOVIE_DETAIL_TTL_MILLIS));
                return result;
            }
        } catch (Exception ex) {
        }

        try {
            Map<String, Object> result = parseJsonObject(getOphimMovieDetail(slug));
            if (!result.isEmpty() && hasMoviePayload(result)) {
                result.put("source", "ophim");
                movieDetailCache.put(slug, new CacheEntry<>(result, System.currentTimeMillis() + MOVIE_DETAIL_TTL_MILLIS));
                return result;
            }
        } catch (Exception ignored) {
        }

        return cached != null ? cached.value() : Collections.emptyMap();
    }

    public Map<String, Object> getMovieDetailData(String slug, String source) {
        String normalizedSource = normalizeDetailSource(source);
        if (normalizedSource.isBlank()) {
            return getMovieDetailData(slug);
        }

        String cacheKey = normalizedSource + "|" + String.valueOf(slug == null ? "" : slug).trim();
        CacheEntry<Map<String, Object>> cached = movieDetailCache.get(cacheKey);
        if (isFresh(cached)) {
            return cached.value();
        }

        try {
            Map<String, Object> result = parseJsonObject(
                    "ophim".equals(normalizedSource) ? getOphimMovieDetail(slug) : getMovieDetail(slug)
            );
            if (!result.isEmpty() && hasMoviePayload(result)) {
                result.put("source", "ophim".equals(normalizedSource) ? "ophim" : "kkphim");
                movieDetailCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + MOVIE_DETAIL_TTL_MILLIS));
                return result;
            }
        } catch (Exception ignored) {
        }

        return cached != null ? cached.value() : Collections.emptyMap();
    }

    public Map<String, String> getTmdbMovieLogo(String name, String originalName, String year) {
        return getTmdbMovieLogo(name, originalName, year, "", "", "");
    }

    public Map<String, String> getTmdbMovieLogo(
            String name,
            String originalName,
            String year,
            String tmdbId,
            String tmdbType,
            String imdbId
    ) {
        String normalizedName = normalizeQueryText(name);
        String normalizedOriginalName = normalizeQueryText(originalName);
        String normalizedYear = normalizeYear(year);
        String normalizedTmdbId = normalizeQueryText(tmdbId);
        String normalizedTmdbType = normalizeQueryText(tmdbType);
        String normalizedImdbId = normalizeQueryText(imdbId);
        String cacheKey = String.join(
                "|",
                TMDB_MATCH_CACHE_VERSION,
                normalizedName,
                normalizedOriginalName,
                normalizedYear,
                normalizedTmdbId,
                normalizedTmdbType,
                normalizedImdbId,
                String.join(",", TMDB_LOGO_LANGUAGE_PRIORITY)
        );
        CacheEntry<Map<String, String>> cached = tmdbLogoCache.get(cacheKey);
        if (isFresh(cached)) {
            return cached.value();
        }

        Map<String, String> result = Map.of("logoUrl", "");
        if (tmdbApiKey.isBlank()
                || (normalizedName.isBlank() && normalizedOriginalName.isBlank() && normalizedTmdbId.isBlank() && normalizedImdbId.isBlank())) {
            tmdbLogoCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + TMDB_LOGO_TTL_MILLIS));
            return result;
        }

        try {
            String logoUrl = "";
            List<TmdbMediaRef> tmdbMediaRefs = resolveExplicitTmdbMediaCandidates(normalizedTmdbId, normalizedTmdbType, normalizedImdbId);
            for (TmdbMediaRef tmdbMedia : tmdbMediaRefs) {
                logoUrl = fetchTmdbLogoUrl(tmdbMedia);
                if (!logoUrl.isBlank()) {
                    break;
                }
            }
            if (logoUrl.isBlank()) {
                List<String> titleCandidates = buildTmdbTitleCandidates(normalizedName, normalizedOriginalName);
                TmdbMediaRef tmdbMedia = findTmdbMedia(titleCandidates, normalizedYear);
                if (tmdbMedia != null) {
                    logoUrl = fetchTmdbLogoUrl(tmdbMedia);
                }
            }
            if (!logoUrl.isBlank()) {
                result = Map.of("logoUrl", logoUrl);
            }
        } catch (Exception ignored) {
        }

        tmdbLogoCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + TMDB_LOGO_TTL_MILLIS));
        return result;
    }

    public List<Map<String, Object>> getTmdbGalleryImages(String name, String originalName, String year, int limit) {
        return getTmdbGalleryImages(name, originalName, year, "", "", "", limit);
    }

    public List<Map<String, Object>> getTmdbGalleryImages(
            String name,
            String originalName,
            String year,
            String tmdbId,
            String tmdbType,
            String imdbId,
            int limit
    ) {
        int safeLimit = Math.max(0, Math.min(limit, 10));
        String normalizedName = normalizeQueryText(name);
        String normalizedOriginalName = normalizeQueryText(originalName);
        String normalizedYear = normalizeYear(year);
        String normalizedTmdbId = normalizeQueryText(tmdbId);
        String normalizedTmdbType = normalizeQueryText(tmdbType);
        String normalizedImdbId = normalizeQueryText(imdbId);
        String cacheKey = String.join(
                "|",
                TMDB_MATCH_CACHE_VERSION,
                TMDB_GALLERY_CACHE_VERSION,
                normalizedName,
                normalizedOriginalName,
                normalizedYear,
                normalizedTmdbId,
                normalizedTmdbType,
                normalizedImdbId,
                String.valueOf(safeLimit)
        );
        CacheEntry<List<Map<String, Object>>> cached = tmdbGalleryCache.get(cacheKey);
        if (isFresh(cached)) {
            return cached.value();
        }

        List<Map<String, Object>> result = Collections.emptyList();
        if (safeLimit <= 0
                || tmdbApiKey.isBlank()
                || (normalizedName.isBlank() && normalizedOriginalName.isBlank() && normalizedTmdbId.isBlank() && normalizedImdbId.isBlank())) {
            tmdbGalleryCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + TMDB_LOGO_TTL_MILLIS));
            return result;
        }

        try {
            TmdbMediaRef tmdbMedia = resolveExplicitTmdbMedia(normalizedTmdbId, normalizedTmdbType, normalizedImdbId);
            if (tmdbMedia == null) {
                List<String> titleCandidates = buildTmdbTitleCandidates(normalizedName, normalizedOriginalName);
                tmdbMedia = findTmdbMedia(titleCandidates, normalizedYear);
            }
            if (tmdbMedia != null) {
                result = fetchTmdbGalleryImages(tmdbMedia, safeLimit);
            }
        } catch (Exception ignored) {
        }

        tmdbGalleryCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + TMDB_LOGO_TTL_MILLIS));
        return result;
    }

    public Map<String, String> getTmdbActorImages(
            String name,
            String originalName,
            String year,
            String tmdbId,
            String tmdbType,
            String imdbId,
            List<String> actorNames
    ) {
        List<String> safeActorNames = actorNames == null ? Collections.emptyList() : actorNames;
        List<String> normalizedActorNames = new ArrayList<>();
        for (String actorName : safeActorNames) {
            String normalizedActorName = normalizeQueryText(actorName);
            if (!normalizedActorName.isBlank()) {
                normalizedActorNames.add(normalizedActorName);
            }
        }

        String normalizedName = normalizeQueryText(name);
        String normalizedOriginalName = normalizeQueryText(originalName);
        String normalizedYear = normalizeYear(year);
        String normalizedTmdbId = normalizeQueryText(tmdbId);
        String normalizedTmdbType = normalizeQueryText(tmdbType);
        String normalizedImdbId = normalizeQueryText(imdbId);
        String cacheKey = String.join(
                "|",
                TMDB_MATCH_CACHE_VERSION,
                TMDB_ACTOR_CACHE_VERSION,
                normalizedName,
                normalizedOriginalName,
                normalizedYear,
                normalizedTmdbId,
                normalizedTmdbType,
                normalizedImdbId,
                String.join(",", normalizedActorNames)
        );
        CacheEntry<Map<String, String>> cached = tmdbActorImageCache.get(cacheKey);
        if (isFresh(cached)) {
            return cached.value();
        }

        Map<String, String> result = Collections.emptyMap();
        if (tmdbApiKey.isBlank()
                || normalizedActorNames.isEmpty()
                || (normalizedName.isBlank() && normalizedOriginalName.isBlank() && normalizedTmdbId.isBlank() && normalizedImdbId.isBlank())) {
            tmdbActorImageCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + TMDB_LOGO_TTL_MILLIS));
            return result;
        }

        try {
            TmdbMediaRef tmdbMedia = resolveExplicitTmdbMedia(normalizedTmdbId, normalizedTmdbType, normalizedImdbId);
            if (tmdbMedia == null) {
                List<String> titleCandidates = buildTmdbTitleCandidates(normalizedName, normalizedOriginalName);
                tmdbMedia = findTmdbMedia(titleCandidates, normalizedYear);
            }
            if (tmdbMedia != null) {
                result = fetchTmdbActorImages(tmdbMedia, normalizedActorNames);
            }
        } catch (Exception ignored) {
        }

        tmdbActorImageCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + TMDB_LOGO_TTL_MILLIS));
        return result;
    }

    public HttpResponse<String> fetchText(String url) throws IOException, InterruptedException {
        String normalizedUrl = normalizeExternalUrl(url);
        if (normalizedUrl.isBlank()) {
            throw new IOException("Invalid upstream text URL");
        }

        CacheEntry<String> cached = textCache.get(normalizedUrl);
        if (isFresh(cached)) {
            return new CachedHttpResponse<>(cached.value(), 200, normalizedUrl);
        }

        HttpRequest request = baseRequest(normalizedUrl).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 400 && response.body() != null && !response.body().isBlank()) {
            textCache.put(normalizedUrl, new CacheEntry<>(response.body(), System.currentTimeMillis() + TEXT_RESPONSE_TTL_MILLIS));
            return response;
        }
        if (cached != null) {
            return new CachedHttpResponse<>(cached.value(), 200, normalizedUrl);
        }
        return response;
    }

    private List<String> buildTmdbTitleCandidates(String name, String originalName) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addTmdbTitleVariants(candidates, originalName);
        addTmdbTitleVariants(candidates, name);
        return new ArrayList<>(candidates);
    }

    private void addTmdbTitleVariants(LinkedHashSet<String> candidates, String rawTitle) {
        String title = normalizeQueryText(rawTitle);
        if (title.isBlank()) {
            return;
        }

        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        seeds.add(title);
        seeds.add(stripTmdbBracketText(title));
        seeds.add(normalizeMatchText(title));
        seeds.add(normalizeMatchText(stripTmdbBracketText(title)));

        for (String seed : seeds) {
            addTmdbTitleCandidate(candidates, seed);

            String withoutNoise = removeTmdbTitleNoise(seed);
            addTmdbTitleCandidate(candidates, withoutNoise);
        }

        for (String seed : seeds) {
            addTmdbSplitTitleCandidates(candidates, seed);
            String withoutNoise = removeTmdbTitleNoise(seed);
            addTmdbSplitTitleCandidates(candidates, withoutNoise);
        }
    }

    private void addTmdbSplitTitleCandidates(LinkedHashSet<String> candidates, String title) {
        String normalized = normalizeQueryText(title);
        if (normalized.isBlank()) {
            return;
        }

        for (String part : normalized.split("\\s+(?:-|\\u2013|\\u2014)\\s+|\\s*[:|/]\\s*")) {
            addTmdbTitleCandidate(candidates, removeTmdbTitleNoise(part));
            addTmdbTitleCandidate(candidates, part);
        }
    }

    private void addTmdbTitleCandidate(LinkedHashSet<String> candidates, String title) {
        String normalized = normalizeQueryText(title);
        if (normalized.length() >= 2) {
            candidates.add(normalized);
        }
    }

    private String stripTmdbBracketText(String title) {
        return normalizeQueryText(String.valueOf(title == null ? "" : title)
                .replaceAll("\\([^)]*\\)|\\[[^]]*\\]|\\{[^}]*\\}", " "));
    }

    private String removeTmdbTitleNoise(String title) {
        String cleaned = " " + normalizeQueryText(title) + " ";
        cleaned = cleaned.replaceAll("(?i)\\blive\\s*[- ]?\\s*action\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\bban\\s+nguoi\\s+dong\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\bnguoi\\s+dong\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\bseason\\s+\\d+\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\bs\\d+\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\bmua\\s+\\d+\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\bphan\\s+\\d+\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(vietsub|thuyet\\s+minh|long\\s+tieng)\\b", " ");
        return normalizeQueryText(cleaned);
    }

    private TmdbMediaRef findTmdbMedia(List<String> titleCandidates, String year) {
        if (!year.isBlank()) {
            TmdbMediaCandidate mediaCandidate = findBestTmdbMediaCandidate(titleCandidates, year);
            if (mediaCandidate != null) {
                return mediaCandidate.toRef();
            }
        }

        TmdbMediaCandidate mediaCandidate = findBestTmdbMediaCandidate(titleCandidates, "");
        return mediaCandidate == null ? null : mediaCandidate.toRef();
    }

    private TmdbMediaRef resolveExplicitTmdbMedia(String tmdbId, String tmdbType, String imdbId) {
        TmdbMediaRef tmdbMedia = resolveDirectTmdbMedia(tmdbId, tmdbType);
        return tmdbMedia != null ? tmdbMedia : resolveImdbTmdbMedia(imdbId);
    }

    private List<TmdbMediaRef> resolveExplicitTmdbMediaCandidates(String tmdbId, String tmdbType, String imdbId) {
        TmdbMediaRef tmdbMedia = resolveDirectTmdbMedia(tmdbId, tmdbType);
        if (tmdbMedia != null) {
            return List.of(tmdbMedia);
        }

        TmdbMediaRef imdbMedia = resolveImdbTmdbMedia(imdbId);
        if (imdbMedia != null) {
            return List.of(imdbMedia);
        }

        int mediaId = parsePositiveInt(tmdbId);
        if (mediaId <= 0) {
            return Collections.emptyList();
        }
        return List.of(new TmdbMediaRef("movie", mediaId), new TmdbMediaRef("tv", mediaId));
    }

    private TmdbMediaRef resolveDirectTmdbMedia(String tmdbId, String tmdbType) {
        int mediaId = parsePositiveInt(tmdbId);
        String mediaType = normalizeTmdbMediaType(tmdbType);
        if (mediaId <= 0 || mediaType.isBlank()) {
            return null;
        }
        return new TmdbMediaRef(mediaType, mediaId);
    }

    private TmdbMediaRef resolveImdbTmdbMedia(String imdbId) {
        String normalizedImdbId = normalizeQueryText(imdbId);
        if (!normalizedImdbId.matches("tt\\d+")) {
            return null;
        }

        String url = tmdbBaseUrl
                + "/find/" + encodePathSegment(normalizedImdbId)
                + "?api_key=" + encodeQueryParam(tmdbApiKey)
                + "&external_source=imdb_id";
        Map<String, Object> payload = parseJsonObject(fetchCachedJson(url, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
        TmdbMediaRef movieRef = firstTmdbMediaRef(safeMapList(payload.get("movie_results")), "movie");
        if (movieRef != null) {
            return movieRef;
        }
        return firstTmdbMediaRef(safeMapList(payload.get("tv_results")), "tv");
    }

    private TmdbMediaRef firstTmdbMediaRef(List<Map<String, Object>> results, String mediaType) {
        for (Map<String, Object> result : results) {
            int mediaId = parsePositiveInt(result.get("id"));
            if (mediaId > 0) {
                return new TmdbMediaRef(mediaType, mediaId);
            }
        }
        return null;
    }

    private String normalizeTmdbMediaType(String value) {
        String normalized = normalizeMatchText(value);
        if (normalized.equals("movie") || normalized.equals("single") || normalized.equals("phim le")) {
            return "movie";
        }
        if (normalized.equals("tv")
                || normalized.equals("series")
                || normalized.equals("tvshows")
                || normalized.equals("phim bo")) {
            return "tv";
        }
        return "";
    }

    private TmdbMediaCandidate findBestTmdbMediaCandidate(List<String> titleCandidates, String year) {
        TmdbMediaCandidate selected = null;
        for (String title : titleCandidates) {
            TmdbMediaCandidate mediaCandidate = searchTmdbMedia(title, year);
            if (mediaCandidate != null && (selected == null || mediaCandidate.score() > selected.score())) {
                selected = mediaCandidate;
            }
        }
        return selected;
    }

    private TmdbMediaCandidate searchTmdbMedia(String title, String year) {
        if (title == null || title.isBlank()) {
            return null;
        }

        TmdbMediaCandidate movieCandidate = searchTmdbMediaCandidate("movie", title, year);
        TmdbMediaCandidate tvCandidate = searchTmdbMediaCandidate("tv", title, year);
        if (movieCandidate == null) {
            return tvCandidate;
        }
        if (tvCandidate == null) {
            return movieCandidate;
        }
        return tvCandidate.score() > movieCandidate.score() ? tvCandidate : movieCandidate;
    }

    private TmdbMediaCandidate searchTmdbMediaCandidate(String mediaType, String title, String year) {
        String url = tmdbBaseUrl
                + "/search/" + mediaType
                + "?api_key=" + encodeQueryParam(tmdbApiKey)
                + "&query=" + encodeQueryParam(title)
                + "&include_adult=false&page=1&language=vi-VN";
        if (year != null && !year.isBlank()) {
            url += "movie".equals(mediaType)
                    ? "&year=" + encodeQueryParam(year)
                    : "&first_air_date_year=" + encodeQueryParam(year);
        }

        Map<String, Object> payload = parseJsonObject(fetchCachedJson(url, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
        return selectBestTmdbCandidate(safeMapList(payload.get("results")), title, year, mediaType);
    }

    private TmdbMediaCandidate selectBestTmdbCandidate(List<Map<String, Object>> results, String title, String year, String mediaType) {
        if (results.isEmpty()) {
            return null;
        }

        String normalizedTitle = normalizeMatchText(title);
        TmdbMediaCandidate selected = null;

        for (Map<String, Object> result : results) {
            int mediaId = parsePositiveInt(result.get("id"));
            if (mediaId <= 0) {
                continue;
            }

            double titleScore = tmdbTitleScore(
                    normalizedTitle,
                    String.valueOf(result.getOrDefault(primaryTmdbTitleKey(mediaType), "")),
                    String.valueOf(result.getOrDefault(originalTmdbTitleKey(mediaType), ""))
            );
            boolean hasExpectedYear = year != null && !year.isBlank();
            String resultYear = extractYear(String.valueOf(result.getOrDefault(tmdbDateKey(mediaType), "")));
            boolean yearMatches = hasExpectedYear && year.equals(resultYear);
            if (hasExpectedYear && !resultYear.isBlank() && !yearMatches) {
                continue;
            }
            if (!isAcceptableTmdbTitleScore(titleScore, hasExpectedYear, yearMatches)) {
                continue;
            }

            double score = titleScore + parseDouble(result.get("popularity")) + tmdbQuerySpecificityScore(normalizedTitle);
            if (hasExpectedYear) {
                score += yearMatches ? 350 : -250;
            }

            if (selected == null || score > selected.score()) {
                selected = new TmdbMediaCandidate(mediaType, mediaId, score);
            }
        }

        return selected;
    }

    private String fetchTmdbLogoUrl(TmdbMediaRef mediaRef) {
        String url = tmdbBaseUrl
                + "/" + mediaRef.mediaType() + "/" + mediaRef.id()
                + "/images?api_key=" + encodeQueryParam(tmdbApiKey)
                + "&language=vi-VN&include_image_language=" + encodeQueryParam(String.join(",", TMDB_LOGO_LANGUAGE_PRIORITY));
        Map<String, Object> payload = parseJsonObject(fetchCachedJson(url, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
        Map<String, Object> logo = selectBestTmdbLogo(safeMapList(payload.get("logos")));
        if (logo.isEmpty()) {
            String fallbackUrl = tmdbBaseUrl
                    + "/" + mediaRef.mediaType() + "/" + mediaRef.id()
                    + "/images?api_key=" + encodeQueryParam(tmdbApiKey);
            payload = parseJsonObject(fetchCachedJson(fallbackUrl, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
            logo = selectBestTmdbLogo(safeMapList(payload.get("logos")));
        }
        String logoPath = String.valueOf(logo.getOrDefault("file_path", "")).trim();
        if (isBlankTmdbImagePath(logoPath)) {
            return "";
        }
        if (logoPath.startsWith("http://") || logoPath.startsWith("https://")) {
            return normalizeExternalUrl(logoPath);
        }
        return TMDB_IMAGE_BASE_URL + (logoPath.startsWith("/") ? logoPath : "/" + logoPath);
    }

    private List<Map<String, Object>> fetchTmdbGalleryImages(TmdbMediaRef mediaRef, int limit) {
        String url = tmdbBaseUrl
                + "/" + mediaRef.mediaType() + "/" + mediaRef.id()
                + "/images?api_key=" + encodeQueryParam(tmdbApiKey);
        Map<String, Object> payload = parseJsonObject(fetchCachedJson(url, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
        List<Map<String, Object>> images = new ArrayList<>();
        LinkedHashSet<String> seenUrls = new LinkedHashSet<>();

        collectTmdbGalleryImages(images, seenUrls, safeMapList(payload.get("backdrops")), "backdrop", limit);
        collectTmdbGalleryImages(images, seenUrls, safeMapList(payload.get("posters")), "poster", limit);
        return images;
    }

    private Map<String, String> fetchTmdbActorImages(TmdbMediaRef mediaRef, List<String> actorNames) {
        Map<String, String> requestedNames = new java.util.LinkedHashMap<>();
        for (String actorName : actorNames) {
            String normalizedActorName = normalizeMatchText(actorName);
            if (!normalizedActorName.isBlank()) {
                requestedNames.putIfAbsent(normalizedActorName, actorName);
            }
        }
        if (requestedNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> actorImages = new java.util.HashMap<>();
        List<Map<String, Object>> castMembers = fetchTmdbCastMembers(mediaRef);
        Set<Integer> castPersonIds = collectTmdbPersonIds(castMembers);

        for (Map<String, Object> castMember : castMembers) {
            String requestedName = findRequestedActorName(requestedNames, castMember);
            if (requestedName.isBlank() || actorImages.containsKey(requestedName)) {
                continue;
            }

            String profileUrl = resolveTmdbPersonProfileUrl(castMember);
            if (!profileUrl.isBlank()) {
                actorImages.put(requestedName, profileUrl);
            }
        }

        for (String requestedName : requestedNames.values()) {
            if (actorImages.containsKey(requestedName)) {
                continue;
            }

            String profileUrl = fetchTmdbPersonImage(requestedName, castPersonIds);
            if (!profileUrl.isBlank()) {
                actorImages.put(requestedName, profileUrl);
            }
        }

        return actorImages;
    }

    private List<Map<String, Object>> fetchTmdbCastMembers(TmdbMediaRef mediaRef) {
        List<Map<String, Object>> castMembers = new ArrayList<>();
        String creditsUrl = tmdbBaseUrl
                + "/" + mediaRef.mediaType() + "/" + mediaRef.id()
                + "/credits?api_key=" + encodeQueryParam(tmdbApiKey)
                + "&language=vi-VN";
        Map<String, Object> creditsPayload = parseJsonObject(fetchCachedJson(creditsUrl, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
        castMembers.addAll(safeMapList(creditsPayload.get("cast")));

        if ("tv".equals(mediaRef.mediaType())) {
            String aggregateCreditsUrl = tmdbBaseUrl
                    + "/" + mediaRef.mediaType() + "/" + mediaRef.id()
                    + "/aggregate_credits?api_key=" + encodeQueryParam(tmdbApiKey)
                    + "&language=vi-VN";
            Map<String, Object> aggregateCreditsPayload = parseJsonObject(fetchCachedJson(aggregateCreditsUrl, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
            castMembers.addAll(safeMapList(aggregateCreditsPayload.get("cast")));
        }

        return castMembers;
    }

    private Set<Integer> collectTmdbPersonIds(List<Map<String, Object>> castMembers) {
        Set<Integer> personIds = new HashSet<>();
        for (Map<String, Object> castMember : castMembers) {
            int personId = parsePositiveInt(castMember.get("id"));
            if (personId > 0) {
                personIds.add(personId);
            }
        }
        return personIds;
    }

    private String fetchTmdbPersonImage(String actorName, Set<Integer> expectedPersonIds) {
        String normalizedActorName = normalizeQueryText(actorName);
        if (normalizedActorName.isBlank()) {
            return "";
        }

        String url = tmdbBaseUrl
                + "/search/person?api_key=" + encodeQueryParam(tmdbApiKey)
                + "&query=" + encodeQueryParam(normalizedActorName)
                + "&include_adult=false&page=1&language=vi-VN";
        Map<String, Object> payload = parseJsonObject(fetchCachedJson(url, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
        String normalizedExpectedName = normalizeMatchText(normalizedActorName);
        Map<String, String> expectedNames = Map.of(normalizedExpectedName, normalizedActorName);
        List<Map<String, Object>> people = safeMapList(payload.get("results"));

        if (expectedPersonIds != null && !expectedPersonIds.isEmpty()) {
            for (Map<String, Object> person : people) {
                int personId = parsePositiveInt(person.get("id"));
                if (personId <= 0 || !expectedPersonIds.contains(personId)) {
                    continue;
                }

                String profileUrl = resolveTmdbPersonProfileUrl(person);
                if (!profileUrl.isBlank()) {
                    return profileUrl;
                }
            }
        }

        for (Map<String, Object> person : people) {
            boolean matchesExpectedName = !findRequestedActorName(expectedNames, person).isBlank();
            if (!matchesExpectedName) {
                continue;
            }

            String profileUrl = resolveTmdbPersonProfileUrl(person);
            if (!profileUrl.isBlank()) {
                return profileUrl;
            }
        }

        return "";
    }

    private String resolveTmdbPersonProfileUrl(Map<String, Object> person) {
        String profilePath = String.valueOf(person.getOrDefault("profile_path", "")).trim();
        String profileUrl = buildTmdbImageUrl(profilePath);
        if (!profileUrl.isBlank()) {
            return profileUrl;
        }

        int personId = parsePositiveInt(person.get("id"));
        return fetchTmdbPersonProfileImageById(personId);
    }

    private String fetchTmdbPersonProfileImageById(int personId) {
        if (personId <= 0) {
            return "";
        }

        String url = tmdbBaseUrl
                + "/person/" + personId
                + "/images?api_key=" + encodeQueryParam(tmdbApiKey);
        Map<String, Object> payload = parseJsonObject(fetchCachedJson(url, tmdbJsonCache, TMDB_LOGO_TTL_MILLIS));
        Map<String, Object> profile = selectBestTmdbProfileImage(safeMapList(payload.get("profiles")));
        return buildTmdbImageUrl(String.valueOf(profile.getOrDefault("file_path", "")));
    }

    private Map<String, Object> selectBestTmdbProfileImage(List<Map<String, Object>> profiles) {
        if (profiles.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> sortedProfiles = new ArrayList<>(profiles);
        sortedProfiles.sort((first, second) -> Double.compare(tmdbImageScore(second), tmdbImageScore(first)));
        for (Map<String, Object> profile : sortedProfiles) {
            String path = String.valueOf(profile.getOrDefault("file_path", "")).trim();
            if (!isBlankTmdbImagePath(path)) {
                return profile;
            }
        }

        return Collections.emptyMap();
    }

    private String buildTmdbImageUrl(String path) {
        String normalizedPath = String.valueOf(path == null ? "" : path).trim();
        if (isBlankTmdbImagePath(normalizedPath)) {
            return "";
        }
        if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
            return normalizeExternalUrl(normalizedPath);
        }
        return TMDB_IMAGE_BASE_URL + (normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath);
    }

    private boolean isBlankTmdbImagePath(String path) {
        String normalizedPath = String.valueOf(path == null ? "" : path).trim();
        return normalizedPath.isBlank() || "null".equalsIgnoreCase(normalizedPath);
    }

    private String findRequestedActorName(Map<String, String> requestedNames, Map<String, Object> castMember) {
        for (String key : List.of("name", "original_name")) {
            String requestedName = findRequestedActorNameByText(requestedNames, String.valueOf(castMember.getOrDefault(key, "")));
            if (!requestedName.isBlank()) {
                return requestedName;
            }
        }
        return "";
    }

    private String findRequestedActorNameByText(Map<String, String> requestedNames, String value) {
        String normalizedName = normalizeMatchText(value);
        if (normalizedName.isBlank()) {
            return "";
        }

        String requestedName = requestedNames.get(normalizedName);
        if (requestedName != null) {
            return requestedName;
        }

        String compactName = compactMatchText(normalizedName);
        for (Map.Entry<String, String> requestedNameEntry : requestedNames.entrySet()) {
            String expectedName = requestedNameEntry.getKey();
            if (compactName.equals(compactMatchText(expectedName))) {
                return requestedNameEntry.getValue();
            }
            if (isSafeActorNameTokenMatch(expectedName, normalizedName)) {
                return requestedNameEntry.getValue();
            }
        }

        return "";
    }

    private String compactMatchText(String value) {
        return normalizeMatchText(value).replace(" ", "");
    }

    private boolean isSafeActorNameTokenMatch(String expectedName, String candidateName) {
        Set<String> expectedTokens = tmdbTitleTokens(expectedName);
        Set<String> candidateTokens = tmdbTitleTokens(candidateName);
        if (expectedTokens.size() < 2 || candidateTokens.size() < 2) {
            return false;
        }

        int matchedTokens = 0;
        for (String token : expectedTokens) {
            if (candidateTokens.contains(token)) {
                matchedTokens++;
            }
        }

        boolean expectedContained = matchedTokens == expectedTokens.size()
                && candidateTokens.size() <= expectedTokens.size() + 1;
        boolean candidateContained = matchedTokens == candidateTokens.size()
                && expectedTokens.size() <= candidateTokens.size() + 1;
        return expectedContained || candidateContained;
    }

    private void collectTmdbGalleryImages(
            List<Map<String, Object>> images,
            LinkedHashSet<String> seenUrls,
            List<Map<String, Object>> rawImages,
            String type,
            int limit
    ) {
        if (images.size() >= limit || rawImages.isEmpty()) {
            return;
        }

        List<Map<String, Object>> sortedImages = new ArrayList<>(rawImages);
        sortedImages.sort((first, second) -> Double.compare(tmdbImageScore(second), tmdbImageScore(first)));
        for (Map<String, Object> rawImage : sortedImages) {
            if (images.size() >= limit) {
                return;
            }

            String path = String.valueOf(rawImage.getOrDefault("file_path", "")).trim();
            if (isBlankTmdbImagePath(path)) {
                continue;
            }

            String url = TMDB_IMAGE_BASE_URL + (path.startsWith("/") ? path : "/" + path);
            if (!seenUrls.add(url)) {
                continue;
            }

            Map<String, Object> image = new java.util.HashMap<>();
            image.put("url", url);
            image.put("source", "tmdb");
            image.put("type", type);
            image.put("width", parsePositiveInt(rawImage.get("width")));
            image.put("height", parsePositiveInt(rawImage.get("height")));
            images.add(image);
        }
    }

    private double tmdbImageScore(Map<String, Object> image) {
        return parseDouble(image.get("vote_average")) * 100
                + parseDouble(image.get("vote_count")) * 2
                + parseDouble(image.get("width")) / 1000;
    }

    private double tmdbTitleScore(String normalizedQuery, String primaryTitle, String originalTitle) {
        return Math.max(
                tmdbSingleTitleScore(normalizedQuery, primaryTitle),
                tmdbSingleTitleScore(normalizedQuery, originalTitle)
        );
    }

    private double tmdbSingleTitleScore(String normalizedQuery, String resultTitle) {
        String normalizedResultTitle = normalizeMatchText(resultTitle);
        if (normalizedQuery.isBlank() || normalizedResultTitle.isBlank()) {
            return 0;
        }
        if (normalizedQuery.equals(normalizedResultTitle)) {
            return 1000;
        }
        if (containsNormalizedTitle(normalizedResultTitle, normalizedQuery)) {
            return 820;
        }
        return tmdbTokenOverlapScore(normalizedQuery, normalizedResultTitle);
    }

    private boolean containsNormalizedTitle(String text, String title) {
        return (" " + text + " ").contains(" " + title + " ");
    }

    private double tmdbTokenOverlapScore(String query, String resultTitle) {
        Set<String> queryTokens = tmdbTitleTokens(query);
        Set<String> titleTokens = tmdbTitleTokens(resultTitle);
        if (queryTokens.isEmpty() || titleTokens.isEmpty()) {
            return 0;
        }

        int matchedTokens = 0;
        for (String token : titleTokens) {
            if (queryTokens.contains(token)) {
                matchedTokens++;
            }
        }

        double titleCoverage = (double) matchedTokens / titleTokens.size();
        double queryCoverage = (double) matchedTokens / queryTokens.size();
        if (titleTokens.size() < 2 || queryTokens.size() < 2) {
            return 0;
        }
        if (titleCoverage >= 0.85 && queryCoverage >= 0.7) {
            return 650;
        }
        if (titleCoverage >= 0.75 && queryCoverage >= 0.75) {
            return 520;
        }
        return 0;
    }

    private Set<String> tmdbTitleTokens(String title) {
        Set<String> tokens = new HashSet<>();
        for (String token : normalizeMatchText(title).split(" ")) {
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double tmdbQuerySpecificityScore(String title) {
        return Math.min(tmdbTitleTokens(title).size(), 8) * TMDB_QUERY_TOKEN_SCORE;
    }

    private boolean isAcceptableTmdbTitleScore(double titleScore, boolean hasExpectedYear, boolean yearMatches) {
        if (titleScore >= TMDB_STRONG_TITLE_SCORE) {
            return true;
        }
        return hasExpectedYear && yearMatches && titleScore >= TMDB_WEAK_TITLE_SCORE;
    }

    private String primaryTmdbTitleKey(String mediaType) {
        return "tv".equals(mediaType) ? "name" : "title";
    }

    private String originalTmdbTitleKey(String mediaType) {
        return "tv".equals(mediaType) ? "original_name" : "original_title";
    }

    private String tmdbDateKey(String mediaType) {
        return "tv".equals(mediaType) ? "first_air_date" : "release_date";
    }

    private Map<String, Object> selectBestTmdbLogo(List<Map<String, Object>> logos) {
        if (logos.isEmpty()) {
            return Collections.emptyMap();
        }

        for (String language : TMDB_LOGO_LANGUAGE_PRIORITY) {
            Map<String, Object> logo = selectBestTmdbLogoForLanguage(logos, language);
            if (!logo.isEmpty()) {
                return logo;
            }
        }

        return selectBestTmdbLogoForLanguage(logos, "");
    }

    private Map<String, Object> selectBestTmdbLogoForLanguage(List<Map<String, Object>> logos, String language) {
        Map<String, Object> selected = Collections.emptyMap();
        double selectedScore = -1;

        for (Map<String, Object> logo : logos) {
            if (!language.isBlank() && !matchesLogoLanguage(logo, language)) {
                continue;
            }

            double score = parseDouble(logo.get("vote_average")) * 100
                    + parseDouble(logo.get("vote_count")) * 2
                    + parseDouble(logo.get("width")) / 1000;
            if (score > selectedScore) {
                selected = logo;
                selectedScore = score;
            }
        }

        return selected;
    }

    private boolean matchesLogoLanguage(Map<String, Object> logo, String language) {
        Object rawLanguage = logo.get("iso_639_1");
        String logoLanguage = rawLanguage == null ? "null" : String.valueOf(rawLanguage).trim().toLowerCase();
        return logoLanguage.equals(language);
    }

    private String normalizeQueryText(String value) {
        return String.valueOf(value == null ? "" : value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeDetailSource(String source) {
        String normalized = normalizeQueryText(source).toLowerCase();
        if ("ophim".equals(normalized)) {
            return "ophim";
        }
        if ("kk".equals(normalized) || "kkphim".equals(normalized) || "phimapi".equals(normalized)) {
            return "kk";
        }
        return "";
    }

    private String normalizeYear(String value) {
        String year = extractYear(String.valueOf(value == null ? "" : value));
        return year.isBlank() ? "" : year;
    }

    private String extractYear(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b(18\\d{2}|19\\d{2}|20\\d{2}|21\\d{2})\\b")
                .matcher(String.valueOf(value == null ? "" : value));
        return matcher.find() ? matcher.group(1) : "";
    }

    private String normalizeMatchText(String value) {
        String normalized = Normalizer.normalize(String.valueOf(value == null ? "" : value), Normalizer.Form.NFD);
        return normalized
                .replaceAll("[\\u0300-\\u036f]", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private int parsePositiveInt(Object value) {
        try {
            int number = Integer.parseInt(String.valueOf(value == null ? "" : value).trim());
            return Math.max(number, 0);
        } catch (Exception ex) {
            return 0;
        }
    }

    private double parseDouble(Object value) {
        try {
            return Double.parseDouble(String.valueOf(value == null ? "" : value).trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private String encodePathSegment(String value) {
        return org.springframework.web.util.UriUtils.encodePathSegment(
                value == null ? "" : value,
                StandardCharsets.UTF_8
        );
    }

    private String encodeQueryParam(String value) {
        return org.springframework.web.util.UriUtils.encodeQueryParam(
                value == null ? "" : value,
                StandardCharsets.UTF_8
        );
    }

    private Map<String, Object> parseJsonObject(String response) {
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    response,
                    new TypeReference<Map<String, Object>>() {}
            );
            return payload != null ? payload : Collections.emptyMap();
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private boolean hasMoviePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        if (payload.get("movie") instanceof Map<?, ?> movie && !movie.isEmpty()) {
            return true;
        }
        if (payload.get("data") instanceof Map<?, ?> data) {
            Object movie = data.get("movie");
            return movie instanceof Map<?, ?> movieMap && !movieMap.isEmpty();
        }
        return false;
    }

    private String normalizeBaseUrl(String value, String fallback) {
        String normalized = normalizeExternalUrl(value);
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String fetchCachedJson(String url, Map<String, CacheEntry<String>> cache, long ttlMillis) {
        CacheEntry<String> cached = cache.get(url);
        if (isFresh(cached)) {
            return cached.value();
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest request = baseRequest(url)
                        .header("Accept", "application/json, text/plain, */*")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                String body = response.body();
                if (response.statusCode() < 400 && body != null && !body.isBlank() && looksLikeJson(body)) {
                    cache.put(url, new CacheEntry<>(body, System.currentTimeMillis() + ttlMillis));
                    return body;
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    if (body != null && !body.isBlank() && looksLikeJson(body)) {
                        cache.put(url, new CacheEntry<>(body, System.currentTimeMillis() + Math.min(ttlMillis, TEXT_RESPONSE_TTL_MILLIS)));
                        return body;
                    }
                    break;
                }
            } catch (Exception ignored) {
            }

            if (attempt < 3) {
                try {
                    Thread.sleep(120L * attempt);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return cached != null ? cached.value() : "{\"status\":\"error\",\"message\":\"Upstream unavailable\"}";
    }

    private boolean looksLikeJson(String body) {
        String trimmed = body == null ? "" : body.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    public HttpResponse<byte[]> fetchBytes(String url) throws IOException, InterruptedException {
        String normalizedUrl = normalizeExternalUrl(url);
        if (normalizedUrl.isBlank()) {
            throw new IOException("Invalid upstream media URL");
        }

        HttpRequest request = baseRequest(normalizedUrl).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    public String resolveUrl(String baseUrl, String target) {
        if (target == null || target.isBlank()) {
            return "";
        }

        try {
            URI resolved = new URI(normalizeExternalUrl(baseUrl)).resolve(target.trim());
            return normalizeExternalUrl(resolved.toString());
        } catch (URISyntaxException ex) {
            return normalizeExternalUrl(target.trim());
        }
    }

    public boolean isResolvableUrl(String url) {
        try {
            String normalizedUrl = normalizeExternalUrl(url);
            if (normalizedUrl.isBlank()) {
                return false;
            }

            URI uri = URI.create(normalizedUrl);
            return uri.getScheme() != null && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    private HttpRequest.Builder baseRequest(String url) {
        String normalizedUrl = normalizeExternalUrl(url);
        if (normalizedUrl.isBlank()) {
            throw new IllegalArgumentException("Invalid upstream URL");
        }

        URI uri = URI.create(normalizedUrl);
        String origin = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");

        return HttpRequest.newBuilder(uri)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "*/*")
                .header("Origin", origin)
                .header("Referer", origin + "/");
    }

    public String normalizeExternalUrl(String url) {
        if (url == null) {
            return "";
        }

        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("//")) {
            trimmed = "https:" + trimmed;
        }

        String candidate = trimmed.replace(" ", "%20");
        try {
            URI uri = URI.create(candidate);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getHost().isBlank()) {
                return "";
            }
            return uri.toASCIIString();
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean isFresh(CacheEntry<?> entry) {
        return entry != null && entry.expiresAt() > System.currentTimeMillis();
    }


    private List<Map<String, String>> extractTaxonomyOptionsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<Map<String, Object>> directList = objectMapper.readValue(
                    json,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            if (!directList.isEmpty()) {
                return mapTaxonomyOptions(directList);
            }
        } catch (Exception ignored) {
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return mapTaxonomyOptions(extractTaxonomyDataList(payload));
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, String>> mapTaxonomyOptions(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, String>> options = new ArrayList<>();
        for (Map<String, Object> map : dataList) {
            String slug = String.valueOf(map.getOrDefault("slug", map.getOrDefault("key", ""))).trim();
            String name = String.valueOf(map.getOrDefault("name", map.getOrDefault("title", ""))).trim();
            if (slug.isBlank() || name.isBlank()) {
                continue;
            }
            options.add(Map.of("slug", slug, "name", name));
        }
        return options;
    }

    private List<Map<String, Object>> extractTaxonomyDataList(Map<String, Object> payload) {
        Object directData = payload.get("data");
        if (directData instanceof List<?>) {
            return safeMapList(directData);
        }

        if (directData instanceof Map<?, ?> dataMapRaw) {
            Map<String, Object> dataMap = safeMap(dataMapRaw);
            Object items = dataMap.get("items");
            if (items instanceof List<?>) {
                return safeMapList(items);
            }
        }

        Object items = payload.get("items");
        if (items instanceof List<?>) {
            return safeMapList(items);
        }

        return Collections.emptyList();
    }

    private Map<String, Object> safeMap(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new java.util.HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private List<Map<String, Object>> safeMapList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> mapItem) {
                result.add(safeMap(mapItem));
            }
        }
        return result;
    }

    private record TmdbMediaRef(String mediaType, int id) {
    }

    private record TmdbMediaCandidate(String mediaType, int id, double score) {
        private TmdbMediaRef toRef() {
            return new TmdbMediaRef(mediaType, id);
        }
    }

    private record CacheEntry<T>(T value, long expiresAt) {
    }

    private static final class CachedHttpResponse<T> implements HttpResponse<T> {
        private final T body;
        private final int statusCode;
        private final HttpRequest request;
        private final URI uri;

        private CachedHttpResponse(T body, int statusCode, String url) {
            this.body = body;
            this.statusCode = statusCode;
            this.uri = URI.create(url);
            this.request = HttpRequest.newBuilder(this.uri).GET().build();
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public java.util.Optional<HttpResponse<T>> previousResponse() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
            return java.util.Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
