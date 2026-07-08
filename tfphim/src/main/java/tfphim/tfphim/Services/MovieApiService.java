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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MovieApiService {
    private static final int DEFAULT_LIST_LIMIT = 24;
    private static final long MOVIE_LIST_TTL_MILLIS = 60 * 1000;
    private static final long MOVIE_DETAIL_TTL_MILLIS = 2 * 60 * 1000;
    private static final long SEARCH_TTL_MILLIS = 30 * 1000;
    private static final long TEXT_RESPONSE_TTL_MILLIS = 20 * 1000;
    private static final long COUNTRY_LIST_TTL_MILLIS = 10 * 60 * 1000;
    private static final long GENRE_LIST_TTL_MILLIS = 10 * 60 * 1000;
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
    private static final String DEFAULT_KK_BASE_URL = "https://phimapi.com";
    private static final String DEFAULT_OPHIM_BASE_URL = "https://ophim1.com";
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

    public MovieApiService(
            @org.springframework.beans.factory.annotation.Value("${kkphim.api.base-url:https://phimapi.com}") String kkBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${ophim.api.base-url:https://ophim1.com}") String ophimBaseUrl
    ) {
        this.kkBaseUrl = normalizeBaseUrl(kkBaseUrl, DEFAULT_KK_BASE_URL);
        this.ophimBaseUrl = normalizeBaseUrl(ophimBaseUrl, DEFAULT_OPHIM_BASE_URL);
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
        String url = kkBaseUrl + "/v1/api/tim-kiem?keyword={keyword}&page={page}&limit=" + DEFAULT_LIST_LIMIT;
        String encodedKeyword = org.springframework.web.util.UriUtils.encodeQueryParam(keyword, StandardCharsets.UTF_8);
        return fetchCachedJson(
                url.replace("{keyword}", encodedKeyword).replace("{page}", String.valueOf(Math.max(page, 1))),
                searchCache,
                SEARCH_TTL_MILLIS
        );
    }

    public String searchOphimMovies(String keyword, int page) {
        String url = ophimBaseUrl + "/v1/api/tim-kiem?keyword={keyword}&page={page}&limit=" + DEFAULT_LIST_LIMIT;
        String encodedKeyword = org.springframework.web.util.UriUtils.encodeQueryParam(keyword, StandardCharsets.UTF_8);
        return fetchCachedJson(
                url.replace("{keyword}", encodedKeyword).replace("{page}", String.valueOf(Math.max(page, 1))),
                searchCache,
                SEARCH_TTL_MILLIS
        );
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

    public Map<String, Object> searchOphimMoviesData(String keyword, int page) {
        return parseJsonObject(searchOphimMovies(keyword, page));
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

    private String encodePathSegment(String value) {
        return org.springframework.web.util.UriUtils.encodePathSegment(
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
