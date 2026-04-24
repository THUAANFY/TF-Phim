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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MovieApiService {
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
    private final Map<String, CacheEntry<String>> siteHtmlCache = new ConcurrentHashMap<>();
    private static final String BASE_URL = "https://phim.nguonc.com/api";
    private static final String SITE_URL = "https://phim.nguonc.com";
    private static final Pattern COUNTRY_LINK_PATTERN = Pattern.compile(
            "href=\"https://phim\\.nguonc\\.com/quoc-gia/([^\"]+)\"[^>]*>([^<]+)</a>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GENRE_LINK_PATTERN = Pattern.compile(
            "href=\"https://phim\\.nguonc\\.com/the-loai/([^\"]+)\"[^>]*>([^<]+)</a>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Map<String, String> MOVIE_PATHS = Map.of(
            "phim-moi", "/films/phim-moi-cap-nhat",
            "phim-bo", "/films/danh-sach/phim-bo",
            "phim-le", "/films/danh-sach/phim-le",
            "hoat-hinh", "/films/the-loai/hoat-hinh"
    );

    public String getMovies(String type, int page) {
        String path = MOVIE_PATHS.getOrDefault(type, "/films/" + type);
        String url = BASE_URL + path + "?page=" + page;
        return fetchCachedJson(url, movieListCache, MOVIE_LIST_TTL_MILLIS);
    }

    public String searchMovies(String keyword, int page) {
        String url = BASE_URL + "/films/search?keyword={keyword}&page={page}";
        String encodedKeyword = org.springframework.web.util.UriUtils.encodeQueryParam(keyword, StandardCharsets.UTF_8);
        return fetchCachedJson(
                url.replace("{keyword}", encodedKeyword).replace("{page}", String.valueOf(Math.max(page, 1))),
                searchCache,
                SEARCH_TTL_MILLIS
        );
    }

    public String getMovieDetail(String slug) {
        String url = BASE_URL + "/film/{slug}";
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
        return fetchCachedJson(url.replace("{slug}", encodedSlug), movieDetailJsonCache, MOVIE_DETAIL_TTL_MILLIS);
    }

    public String getMoviesByCountry(String countrySlug, int page) {
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(countrySlug, StandardCharsets.UTF_8);
        String url = BASE_URL + "/films/quoc-gia/" + encodedSlug + "?page=" + Math.max(page, 1);
        return fetchCachedJson(url, movieListCache, MOVIE_LIST_TTL_MILLIS);
    }

    public String getMoviesByGenre(String genreSlug, int page) {
        String encodedSlug = org.springframework.web.util.UriUtils.encodePathSegment(genreSlug, StandardCharsets.UTF_8);
        String url = BASE_URL + "/films/the-loai/" + encodedSlug + "?page=" + Math.max(page, 1);
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

    public List<Map<String, String>> getCountryOptions() {
        CacheEntry<List<Map<String, String>>> cached = countryListCache.get("countries");
        if (isFresh(cached)) {
            return cached.value();
        }

        String html = getSiteHtml();
        if (!html.isBlank()) {
            List<Map<String, String>> parsed = extractCountryOptions(html);
            if (!parsed.isEmpty()) {
                countryListCache.put("countries", new CacheEntry<>(parsed, System.currentTimeMillis() + COUNTRY_LIST_TTL_MILLIS));
                return parsed;
            }
        }

        return cached != null ? cached.value() : Collections.emptyList();
    }

    public List<Map<String, String>> getGenreOptions() {
        CacheEntry<List<Map<String, String>>> cached = genreListCache.get("genres");
        if (isFresh(cached)) {
            return cached.value();
        }

        String html = getSiteHtml();
        if (!html.isBlank()) {
            List<Map<String, String>> parsed = extractOptionLinks(html, GENRE_LINK_PATTERN);
            if (!parsed.isEmpty()) {
                genreListCache.put("genres", new CacheEntry<>(parsed, System.currentTimeMillis() + GENRE_LIST_TTL_MILLIS));
                return parsed;
            }
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
            if (!result.isEmpty()) {
                movieDetailCache.put(slug, new CacheEntry<>(result, System.currentTimeMillis() + MOVIE_DETAIL_TTL_MILLIS));
            }
            return result;
        } catch (Exception ex) {
            return cached != null ? cached.value() : Collections.emptyMap();
        }
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

    private String fetchCachedJson(String url, Map<String, CacheEntry<String>> cache, long ttlMillis) {
        CacheEntry<String> cached = cache.get(url);
        if (isFresh(cached)) {
            return cached.value();
        }

        try {
            HttpRequest request = baseRequest(url)
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 400 && response.body() != null && !response.body().isBlank()) {
                cache.put(url, new CacheEntry<>(response.body(), System.currentTimeMillis() + ttlMillis));
                return response.body();
            }
        } catch (Exception ignored) {
        }

        return cached != null ? cached.value() : "{\"status\":\"error\",\"message\":\"Upstream unavailable\"}";
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

    private String getSiteHtml() {
        CacheEntry<String> cached = siteHtmlCache.get(SITE_URL);
        if (isFresh(cached)) {
            return cached.value();
        }

        try {
            HttpRequest request = baseRequest(SITE_URL).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 400 && response.body() != null && !response.body().isBlank()) {
                long expiresAt = System.currentTimeMillis() + Math.max(COUNTRY_LIST_TTL_MILLIS, GENRE_LIST_TTL_MILLIS);
                siteHtmlCache.put(SITE_URL, new CacheEntry<>(response.body(), expiresAt));
                return response.body();
            }
        } catch (Exception ignored) {
        }

        return cached != null ? cached.value() : "";
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


    private List<Map<String, String>> extractCountryOptions(String html) {
        return extractOptionLinks(html, COUNTRY_LINK_PATTERN);
    }

    private List<Map<String, String>> extractOptionLinks(String html, Pattern pattern) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }

        Matcher matcher = pattern.matcher(html);
        Map<String, Map<String, String>> options = new LinkedHashMap<>();
        while (matcher.find()) {
            String slug = matcher.group(1).trim();
            String name = matcher.group(2).trim();
            if (slug.isBlank() || name.isBlank()) {
                continue;
            }

            options.putIfAbsent(slug, Map.of(
                    "slug", slug,
                    "name", org.springframework.web.util.HtmlUtils.htmlUnescape(name)
            ));
        }

        return new ArrayList<>(options.values());
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
