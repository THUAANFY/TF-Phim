package tfphim.tfphim.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import tfphim.tfphim.Services.MovieApiService;

@Controller
public class HomeController {
    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    private static final int EPISODES_PER_PAGE = 50;
    private static final int MOVIES_PER_PAGE = 36;
    private static final int API_MOVIES_PER_PAGE = 10;
    private static final Map<String, Map<String, String>> CATEGORY_METADATA = Map.of(
            "phim-bo", Map.of(
                    "title", "Phim Bộ",
                    "description", "Danh sách phim bộ được cập nhật liên tục trên TF-Phim.",
                    "icon", "fa-solid fa-tv"
            ),
            "phim-le", Map.of(
                    "title", "Phim Lẻ",
                    "description", "Tổng hợp phim lẻ nổi bật, xem nhanh theo từng trang.",
                    "icon", "fa-solid fa-clapperboard"
            ),
            "phim-moi", Map.of(
                    "title", "Phim Đang Chiếu",
                    "description", "Những phim đang được cập nhật mới nhất và đang phát hành.",
                    "icon", "fa-solid fa-film"
            )
    );
    private final MovieApiService movieApiService;
    public HomeController(MovieApiService movieApiService) {
        this.movieApiService = movieApiService;
    }

    @ModelAttribute
    public void populateNavbarData(Model model) {
        model.addAttribute("countryOptions", movieApiService.getCountryOptions());
        model.addAttribute("genreOptions", movieApiService.getGenreOptions());
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "Trang chủ");
        return "index";
    }

    @GetMapping("/danh-muc/{type}")
    public String moviesByCategory(
            @PathVariable String type,
            @RequestParam(defaultValue = "1") int page,
            Model model
    ) {
        Map<String, String> categoryMeta = CATEGORY_METADATA.getOrDefault(type, Collections.emptyMap());
        if (categoryMeta.isEmpty() && !"phim-moi".equals(type) && !"phim-bo".equals(type) && !"phim-le".equals(type)) {
            model.addAttribute("pageTitle", "Danh mục không tồn tại");
            model.addAttribute("errorMessage", "Không tìm thấy danh mục phim bạn yêu cầu.");
            return "movie-category";
        }

        int safePage = Math.max(page, 1);
        Map<String, Object> metadataPayload = movieApiService.getMoviesData(type, 1);
        Map<String, Object> paginate = safeMap(metadataPayload.get("paginate"));
        int totalItems = extractNonNegativeInt(paginate.get("total_items"), 0);
        int totalPages = Math.max(calculateTotalPages(totalItems, MOVIES_PER_PAGE), 1);
        int currentPage = Math.min(safePage, totalPages);
        List<Map<String, Object>> movies = fetchMoviesForLocalPage(type, currentPage, metadataPayload);

        Map<String, Object> cat = safeMap(metadataPayload.get("cat"));
        String categoryTitle = String.valueOf(cat.getOrDefault("title", cat.getOrDefault("name", categoryMeta.getOrDefault("title", type))));
        String categoryDescription = categoryMeta.getOrDefault("description", "Danh sách phim được lấy theo định dạng từ API.");

        model.addAttribute("pageTitle", categoryTitle);
        model.addAttribute("categoryType", type);
        model.addAttribute("categoryTitle", categoryTitle);
        model.addAttribute("categoryDescription", categoryDescription);
        model.addAttribute("categoryIcon", categoryMeta.getOrDefault("icon", "fa-solid fa-film"));
        model.addAttribute("movies", movies);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("hasPrevious", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);
        model.addAttribute("paginationItems", buildPaginationItems(currentPage, totalPages));
        model.addAttribute("paginationBasePath", "/danh-muc/" + type);
        return "movie-category";
    }

    @GetMapping("/tim-kiem")
    public String searchMovies(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            Model model
    ) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        model.addAttribute("searchKeyword", normalizedKeyword);
        model.addAttribute("pageTitle", normalizedKeyword.isBlank() ? "Tìm kiếm" : "Kết quả tìm kiếm");

        if (normalizedKeyword.isBlank()) {
            model.addAttribute("movies", Collections.emptyList());
            model.addAttribute("totalItems", 0);
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("hasPrevious", false);
            model.addAttribute("hasNext", false);
            model.addAttribute("errorMessage", "Vui lòng nhập từ khóa để tìm kiếm phim.");
            return "search-results";
        }

        int safePage = Math.max(page, 1);
        Map<String, Object> firstPayload = movieApiService.searchMoviesData(normalizedKeyword, 1);
        Map<String, Object> paginate = safeMap(firstPayload.get("paginate"));
        int totalItems = extractNonNegativeInt(paginate.get("total_items"), 0);
        int totalPages = Math.max(calculateTotalPages(totalItems, MOVIES_PER_PAGE), 1);
        int currentPage = Math.min(safePage, totalPages);
        List<Map<String, Object>> movies = fetchSearchMoviesForLocalPage(normalizedKeyword, currentPage, firstPayload);

        model.addAttribute("movies", movies);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrevious", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);
        model.addAttribute("errorMessage", null);
        return "search-results";
    }

    @GetMapping("/yeu-thich")
    public String favoriteMovies(Model model) {
        model.addAttribute("pageTitle", "Phim yêu thích");
        model.addAttribute("favoritePage", true);
        return "favorites";
    }

    @GetMapping("/quoc-gia/{slug}")
    public String moviesByCountry(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") int page,
            Model model
    ) {
        int safePage = Math.max(page, 1);
        Map<String, Object> metadataPayload = movieApiService.getMoviesByCountryData(slug, 1);
        Map<String, Object> paginate = safeMap(metadataPayload.get("paginate"));
        Map<String, Object> cat = safeMap(metadataPayload.get("cat"));
        int totalItems = extractNonNegativeInt(paginate.get("total_items"), 0);
        int totalPages = Math.max(calculateTotalPages(totalItems, MOVIES_PER_PAGE), 1);
        int currentPage = Math.min(safePage, totalPages);
        List<Map<String, Object>> movies = fetchCountryMoviesForLocalPage(slug, currentPage, metadataPayload);
        String countryTitle = String.valueOf(cat.getOrDefault("title", cat.getOrDefault("name", slug)));

        model.addAttribute("pageTitle", countryTitle);
        model.addAttribute("countrySlug", slug);
        model.addAttribute("categoryType", null);
        model.addAttribute("categoryTitle", countryTitle);
        model.addAttribute("categoryDescription", "Danh sách phim theo quốc gia được cập nhật liên tục.");
        model.addAttribute("categoryIcon", "fa-solid fa-earth-asia");
        model.addAttribute("movies", movies);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("hasPrevious", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);
        model.addAttribute("paginationBasePath", "/quoc-gia/" + slug);
        return "movie-category";
    }

    @GetMapping("/the-loai/{slug}")
    public String moviesByGenre(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") int page,
            Model model
    ) {
        int safePage = Math.max(page, 1);
        Map<String, Object> metadataPayload = movieApiService.getMoviesByGenreData(slug, 1);
        Map<String, Object> paginate = safeMap(metadataPayload.get("paginate"));
        Map<String, Object> cat = safeMap(metadataPayload.get("cat"));
        int totalItems = extractNonNegativeInt(paginate.get("total_items"), 0);
        int totalPages = Math.max(calculateTotalPages(totalItems, MOVIES_PER_PAGE), 1);
        int currentPage = Math.min(safePage, totalPages);
        List<Map<String, Object>> movies = fetchGenreMoviesForLocalPage(slug, currentPage, metadataPayload);
        String genreTitle = String.valueOf(cat.getOrDefault("title", cat.getOrDefault("name", slug)));

        model.addAttribute("pageTitle", genreTitle);
        model.addAttribute("genreSlug", slug);
        model.addAttribute("categoryType", null);
        model.addAttribute("categoryTitle", genreTitle);
        model.addAttribute("categoryDescription", "Danh sách phim theo thể loại được cập nhật liên tục.");
        model.addAttribute("categoryIcon", "fa-solid fa-layer-group");
        model.addAttribute("movies", movies);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("hasPrevious", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);
        model.addAttribute("paginationBasePath", "/the-loai/" + slug);
        return "movie-category";
    }

    @GetMapping("/phim/{slug}")
    public String movieDetail(@PathVariable String slug, Model model) {
        try {
            Map<String, Object> payload = movieApiService.getMovieDetailData(slug);
            Map<String, Object> movie = extractMovieDetail(payload);
            List<Map<String, Object>> episodes = extractEpisodeServers(movie);

            if (movie.isEmpty()) {
                model.addAttribute("pageTitle", "Không tìm thấy phim");
                model.addAttribute("errorMessage", "Không tìm thấy thông tin phim.");
                return "movie-detail";
            }

            model.addAttribute("pageTitle", movie.getOrDefault("name", "Chi tiết phim"));
            model.addAttribute("movie", movie);
            model.addAttribute("categories", extractCategories(movie));
            model.addAttribute("languageBadges", extractLanguageBadges(movie));
            model.addAttribute("releaseYear", findCategoryValue(movie, "Năm"));
            model.addAttribute("episodes", episodes);
            model.addAttribute("pagedEpisodes", paginateEpisodesForView(
                    episodes,
                    null,
                    null,
                    String.valueOf(movie.getOrDefault("language", ""))
            ));
            applyEpisodeProgressAttributes(model, movie, episodes);
            Map<String, Object> firstEpisode = findFirstEpisode(episodes);
            model.addAttribute("firstEpisode", firstEpisode);
            model.addAttribute("firstEpisodeStreamUrl", buildStreamUrl(String.valueOf(movie.getOrDefault("slug", slug)), firstEpisode));
            return "movie-detail";
        } catch (Exception ex) {
            model.addAttribute("pageTitle", "Không tìm thấy phim");
            model.addAttribute("errorMessage", "Không thể tải chi tiết phim vào lúc này.");
            return "movie-detail";
        }
    }

    @GetMapping("/xem/{slug}")
    public String watchMovie(
            @PathVariable String slug,
            @RequestParam(required = false) String server,
            @RequestParam(required = false) String tap,
            Model model
    ) {
        try {
            Map<String, Object> payload = movieApiService.getMovieDetailData(slug);
            Map<String, Object> movie = extractMovieDetail(payload);
            List<Map<String, Object>> episodes = extractEpisodeServers(movie);
            Map<String, Object> selectedEpisode = findSelectedEpisode(episodes, server, tap);

            if (movie.isEmpty()) {
                model.addAttribute("pageTitle", "Không xem được phim");
                model.addAttribute("errorMessage", "Không tìm thấy thông tin phim.");
                return "movie-watch";
            }

            if (selectedEpisode.isEmpty()) {
                model.addAttribute("pageTitle", movie.getOrDefault("name", "Xem phim"));
                model.addAttribute("movie", movie);
                model.addAttribute("errorMessage", "Phim này hiện chưa có nguồn phát khả dụng.");
                return "movie-watch";
            }

            model.addAttribute("pageTitle", movie.getOrDefault("name", "Xem phim"));
            model.addAttribute("movie", movie);
            model.addAttribute("episodes", episodes);
            model.addAttribute("pagedEpisodes", paginateEpisodesForView(
                    episodes,
                    String.valueOf(selectedEpisode.getOrDefault("server_name", "")),
                    String.valueOf(selectedEpisode.getOrDefault("slug", "")),
                    String.valueOf(movie.getOrDefault("language", ""))
            ));
            model.addAttribute("selectedEpisode", selectedEpisode);
            model.addAttribute("languageBadges", extractLanguageBadges(movie));
            model.addAttribute("categories", extractCategories(movie));
            model.addAttribute("releaseYear", findCategoryValue(movie, "NÄƒm"));
            model.addAttribute("selectedServer", String.valueOf(selectedEpisode.getOrDefault("server_name", "")));
            model.addAttribute("selectedServerLabel", buildServerDisplayLabel(
                    String.valueOf(selectedEpisode.getOrDefault("server_name", "")),
                    String.valueOf(movie.getOrDefault("language", ""))
            ));
            model.addAttribute("selectedTap", String.valueOf(selectedEpisode.getOrDefault("slug", "")));
            model.addAttribute("selectedStreamUrl", buildStreamUrl(String.valueOf(movie.getOrDefault("slug", slug)), selectedEpisode));
            applyEpisodeProgressAttributes(model, movie, episodes);
            return "movie-watch";
        } catch (Exception ex) {
            model.addAttribute("pageTitle", "Không xem được phim");
            model.addAttribute("errorMessage", "Không thể tải trang xem phim vào lúc này.");
            return "movie-watch";
        }
    }

    @GetMapping("/xem/{slug}/{tap}")
    public String watchMovieByEpisode(
            @PathVariable String slug,
            @PathVariable String tap,
            @RequestParam(required = false) String server,
            Model model
    ) {
        return watchMovie(slug, server, tap, model);
    }

    @GetMapping("/stream/{slug}/{tap}")
    public RedirectView streamMovie(
            @PathVariable String slug,
            @PathVariable String tap,
            @RequestParam(required = false) String server
    ) {
        Map<String, Object> payload = movieApiService.getMovieDetailData(slug);
        Map<String, Object> movie = extractMovieDetail(payload);
        List<Map<String, Object>> episodes = extractEpisodeServers(movie);
        Map<String, Object> selectedEpisode = findSelectedEpisode(episodes, server, tap);
        String m3u8 = String.valueOf(selectedEpisode.getOrDefault("m3u8", ""));

        if (!m3u8.isBlank()) {
            return new RedirectView(m3u8);
        }

        String embed = String.valueOf(selectedEpisode.getOrDefault("embed", ""));
        if (!embed.isBlank()) {
            return new RedirectView(embed);
        }

        return new RedirectView("/xem/" + slug);
    }

    @GetMapping(value = "/proxy/hls/{slug}/{tap}.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> proxyPlaylist(
            @PathVariable String slug,
            @PathVariable String tap,
            @RequestParam(required = false) String server
    ) {
        try {
            String playlistUrl = findEpisodeStreamSource(slug, server, tap);
            if (playlistUrl.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            var response = movieApiService.fetchText(playlistUrl);
            if (response.statusCode() >= 400) {
                return ResponseEntity.status(response.statusCode()).build();
            }

            String rewritten = rewritePlaylist(playlistUrl, response.body());
            return ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.maxAge(20, TimeUnit.SECONDS).cachePublic())
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .body(rewritten);
        } catch (Exception ex) {
            log.error("Cannot proxy playlist for slug={} tap={}", slug, tap, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping(value = "/proxy/hls/raw", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> proxyRawPlaylist(@RequestParam String url) {
        try {
            if (!movieApiService.isResolvableUrl(url)) {
                return ResponseEntity.badRequest().build();
            }

            var response = movieApiService.fetchText(url);
            if (response.statusCode() >= 400) {
                return ResponseEntity.status(response.statusCode()).build();
            }

            String rewritten = rewritePlaylist(url, response.body());
            return ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.maxAge(20, TimeUnit.SECONDS).cachePublic())
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .body(rewritten);
        } catch (Exception ex) {
            log.error("Cannot proxy raw playlist url={}", url, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/proxy/media")
    public ResponseEntity<byte[]> proxyMedia(@RequestParam String url) {
        try {
            var response = movieApiService.fetchBytes(url);
            if (response.statusCode() >= 400) {
                return ResponseEntity.status(response.statusCode()).build();
            }

            HttpHeaders headers = new HttpHeaders();
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            headers.setContentType(MediaType.parseMediaType(contentType));
            response.headers().firstValue("Accept-Ranges").ifPresent(value -> headers.set("Accept-Ranges", value));
            response.headers().firstValue("Content-Length").ifPresent(value -> headers.set("Content-Length", value));
            response.headers().firstValue("Content-Disposition").ifPresent(value -> headers.set("Content-Disposition", value));
            headers.setCacheControl(org.springframework.http.CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic().getHeaderValue());
            return new ResponseEntity<>(response.body(), headers, HttpStatus.OK);
        } catch (Exception ex) {
            log.error("Cannot proxy media url={}", url, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : Collections.emptyList();
    }

    private List<Map<String, Object>> extractCategories(Map<String, Object> movie) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> categoryGroups = safeMap(movie.get("category"));

        for (Object groupValue : categoryGroups.values()) {
            Map<String, Object> group = safeMap(groupValue);
            Map<String, Object> groupInfo = safeMap(group.get("group"));
            String groupName = String.valueOf(groupInfo.getOrDefault("name", ""));

            for (Map<String, Object> item : safeList(group.get("list"))) {
                result.add(Map.of(
                        "group", groupName,
                        "name", String.valueOf(item.getOrDefault("name", ""))
                ));
            }
        }
        return result;
    }

    private String findCategoryValue(Map<String, Object> movie, String targetGroup) {
        for (Map<String, Object> category : extractCategories(movie)) {
            if (targetGroup.equals(category.get("group"))) {
                return String.valueOf(category.get("name"));
            }
        }
        return "N/A";
    }

    private List<String> extractLanguageBadges(Map<String, Object> movie) {
        return extractLanguageBadges(String.valueOf(movie.getOrDefault("language", "")));
    }

    private List<String> extractLanguageBadges(String language) {
        if (language.isBlank()) {
            return Collections.emptyList();
        }

        String normalized = normalizeLanguageText(language);
        List<String> badges = new ArrayList<>();

        if (normalized.contains("vietsub")) {
            badges.add("P.Đề");
        }
        if (normalized.contains("thuyet minh")) {
            badges.add("T.Minh");
        }
        if (normalized.contains("long tieng")) {
            badges.add("L.Tiếng");
        }

        return badges;
    }

    private List<Map<String, String>> buildLanguageBadgeItems(String language) {
        List<Map<String, String>> items = new ArrayList<>();

        for (String badge : extractLanguageBadges(language)) {
            if ("P.Đề".equals(badge)) {
                items.add(Map.of("label", "P.Đề", "className", "lang-sub"));
                continue;
            }
            if ("T.Minh".equals(badge)) {
                items.add(Map.of("label", "T.Minh", "className", "lang-dub"));
                continue;
            }
            items.add(Map.of("label", "L.Tiếng", "className", "lang-voice"));
        }

        return items;
    }

    private String normalizeLanguageText(String language) {
        String normalized = Normalizer.normalize(language, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase();

        return normalized.replaceAll("\\s+", " ").trim();
    }

    private List<Map<String, Object>> extractMovieItems(Map<String, Object> payload) {
        Object items = payload.get("items");
        if (items instanceof List<?>) {
            return safeList(items);
        }

        Map<String, Object> data = safeMap(payload.get("data"));
        Object dataItems = data.get("items");
        if (dataItems instanceof List<?>) {
            return safeList(dataItems);
        }

        Object rawData = payload.get("data");
        if (rawData instanceof List<?>) {
            return safeList(rawData);
        }

        return Collections.emptyList();
    }

    private Map<String, Object> extractMovieDetail(Map<String, Object> payload) {
        Map<String, Object> movie = safeMap(payload.get("movie"));
        if (!movie.isEmpty()) {
            return movie;
        }

        Map<String, Object> data = safeMap(payload.get("data"));
        movie = safeMap(data.get("movie"));
        if (!movie.isEmpty()) {
            return movie;
        }

        if (!data.isEmpty()) {
            return data;
        }

        return Collections.emptyMap();
    }

    private List<Map<String, Object>> extractEpisodeServers(Map<String, Object> movie) {
        List<Map<String, Object>> episodes = safeList(movie.get("episodes"));
        if (!episodes.isEmpty()) {
            return episodes;
        }

        Map<String, Object> data = safeMap(movie.get("data"));
        return safeList(data.get("episodes"));
    }

    private void applyEpisodeProgressAttributes(Model model, Map<String, Object> movie, List<Map<String, Object>> episodes) {
        Map<String, Object> progress = buildEpisodeProgress(movie, episodes);
        model.addAttribute("showEpisodeProgress", progress.get("show"));
        model.addAttribute("episodeProgressText", progress.get("text"));
        model.addAttribute("episodeProgressCompleted", progress.get("completed"));
    }

    private Map<String, Object> buildEpisodeProgress(Map<String, Object> movie, List<Map<String, Object>> episodes) {
        int totalEpisodes = extractNonNegativeInt(movie.get("total_episodes"), 0);
        int airedEpisodes = resolveAiredEpisodes(movie, episodes);

        if (!isSeriesMovie(movie, totalEpisodes, airedEpisodes) || totalEpisodes <= 0 || airedEpisodes <= 0) {
            return Map.of(
                    "show", false,
                    "text", "",
                    "completed", false
            );
        }

        int safeAiredEpisodes = Math.min(airedEpisodes, totalEpisodes);
        boolean completed = isCompletedSeries(movie, safeAiredEpisodes, totalEpisodes);
        return Map.of(
                "show", true,
                "text", "Đã chiếu " + safeAiredEpisodes + " / " + totalEpisodes + " tập",
                "completed", completed
        );
    }

    private boolean isSeriesMovie(Map<String, Object> movie, int totalEpisodes, int airedEpisodes) {
        if (totalEpisodes > 1 || airedEpisodes > 1) {
            return true;
        }

        String currentEpisode = String.valueOf(movie.getOrDefault("current_episode", "")).toLowerCase();
        return currentEpisode.contains("tập");
    }

    private int resolveAiredEpisodes(Map<String, Object> movie, List<Map<String, Object>> episodes) {
        int fromCurrentEpisode = extractEpisodeCountFromLabel(String.valueOf(movie.getOrDefault("current_episode", "")));
        if (fromCurrentEpisode > 0) {
            return fromCurrentEpisode;
        }

        int maxEpisodeCount = 0;
        for (Map<String, Object> server : episodes) {
            maxEpisodeCount = Math.max(maxEpisodeCount, safeList(server.get("items")).size());
        }
        return maxEpisodeCount;
    }

    private boolean isCompletedSeries(Map<String, Object> movie, int airedEpisodes, int totalEpisodes) {
        if (airedEpisodes >= totalEpisodes) {
            return true;
        }

        String currentEpisode = String.valueOf(movie.getOrDefault("current_episode", "")).toLowerCase();
        return currentEpisode.contains("hoàn tất")
                || currentEpisode.contains("hoan tat")
                || currentEpisode.contains("full")
                || currentEpisode.contains("completed");
    }

    private int extractEpisodeCountFromLabel(String label) {
        if (label == null || label.isBlank()) {
            return 0;
        }

        String normalized = label.replaceAll("[^0-9/]+", " ").trim();
        if (normalized.isBlank()) {
            return 0;
        }

        String[] slashParts = normalized.split("/");
        if (slashParts.length > 0) {
            int firstSlashNumber = extractFirstInt(slashParts[0]);
            if (firstSlashNumber > 0) {
                return firstSlashNumber;
            }
        }

        return extractFirstInt(normalized);
    }

    private int extractFirstInt(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String[] parts = text.trim().split("\\s+");
        for (String part : parts) {
            try {
                return Math.max(Integer.parseInt(part), 0);
            } catch (NumberFormatException ignored) {
            }
        }

        return 0;
    }

    private int extractPositiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 1);
        }

        if (value instanceof String text) {
            try {
                return Math.max(Integer.parseInt(text.trim()), 1);
            } catch (NumberFormatException ignored) {
            }
        }

        return Math.max(fallback, 1);
    }

    private int extractNonNegativeInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }

        if (value instanceof String text) {
            try {
                return Math.max(Integer.parseInt(text.trim()), 0);
            } catch (NumberFormatException ignored) {
            }
        }

        return Math.max(fallback, 0);
    }

    private int calculateTotalPages(int totalItems, int pageSize) {
        if (totalItems <= 0 || pageSize <= 0) {
            return 0;
        }

        return (int) Math.ceil((double) totalItems / pageSize);
    }

    private List<Map<String, Object>> fetchMoviesForLocalPage(String type, int localPage, Map<String, Object> firstPayload) {
        return fetchLocalPageItems(localPage, firstPayload, apiPage -> movieApiService.getMoviesData(type, apiPage));
    }

    private List<Map<String, Object>> fetchSearchMoviesForLocalPage(String keyword, int localPage, Map<String, Object> firstPayload) {
        return fetchLocalPageItems(localPage, firstPayload, apiPage -> movieApiService.searchMoviesData(keyword, apiPage));
    }

    private List<Map<String, Object>> fetchCountryMoviesForLocalPage(String countrySlug, int localPage, Map<String, Object> firstPayload) {
        return fetchLocalPageItems(localPage, firstPayload, apiPage -> movieApiService.getMoviesByCountryData(countrySlug, apiPage));
    }

    private List<Map<String, Object>> fetchGenreMoviesForLocalPage(String genreSlug, int localPage, Map<String, Object> firstPayload) {
        return fetchLocalPageItems(localPage, firstPayload, apiPage -> movieApiService.getMoviesByGenreData(genreSlug, apiPage));
    }

    private List<Map<String, Object>> fetchLocalPageItems(
            int localPage,
            Map<String, Object> firstPayload,
            java.util.function.IntFunction<Map<String, Object>> pageFetcher
    ) {
        if (localPage < 1) {
            return Collections.emptyList();
        }

        int startIndex = (localPage - 1) * MOVIES_PER_PAGE;
        int endIndex = startIndex + MOVIES_PER_PAGE - 1;
        int startApiPage = (startIndex / API_MOVIES_PER_PAGE) + 1;
        int endApiPage = (endIndex / API_MOVIES_PER_PAGE) + 1;
        int startOffset = startIndex % API_MOVIES_PER_PAGE;
        List<Map<String, Object>> combinedItems = new ArrayList<>();

        for (int apiPage = startApiPage; apiPage <= endApiPage; apiPage++) {
            Map<String, Object> payload;
            if (apiPage == 1 && firstPayload != null && !firstPayload.isEmpty()) {
                payload = firstPayload;
            } else {
                payload = pageFetcher.apply(apiPage);
            }
            combinedItems.addAll(extractMovieItems(payload));
        }

        if (combinedItems.isEmpty() || startOffset >= combinedItems.size()) {
            return Collections.emptyList();
        }

        int toIndex = Math.min(startOffset + MOVIES_PER_PAGE, combinedItems.size());
        return new ArrayList<>(combinedItems.subList(startOffset, toIndex));
    }

    private List<Map<String, Object>> buildPaginationItems(int currentPage, int totalPages) {
        if (totalPages <= 1) {
            return Collections.emptyList();
        }

        LinkedHashSet<Integer> visiblePages = new LinkedHashSet<>();
        visiblePages.add(1);

        for (int page = currentPage - 2; page <= currentPage + 2; page++) {
            if (page >= 1 && page <= totalPages) {
                visiblePages.add(page);
            }
        }

        visiblePages.add(totalPages);

        List<Integer> sortedPages = new ArrayList<>(visiblePages);
        Collections.sort(sortedPages);

        List<Map<String, Object>> items = new ArrayList<>();
        Integer previous = null;
        for (Integer page : sortedPages) {
            if (previous != null && page - previous > 1) {
                items.add(Map.of("ellipsis", true));
            }

            items.add(Map.of(
                    "ellipsis", false,
                    "page", page,
                    "active", page == currentPage
            ));
            previous = page;
        }

        return items;
    }

    private Map<String, Object> findFirstEpisode(List<Map<String, Object>> episodes) {
        for (Map<String, Object> server : episodes) {
            String serverName = String.valueOf(server.getOrDefault("server_name", ""));
            List<Map<String, Object>> items = safeList(server.get("items"));
            if (!items.isEmpty()) {
                Map<String, Object> first = new HashMap<>(items.get(0));
                first.put("server_name", serverName);
                first.put("server_label", buildServerDisplayLabel(serverName, ""));
                return first;
            }
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> findSelectedEpisode(List<Map<String, Object>> episodes, String targetServer, String targetTap) {
        if (episodes.isEmpty()) {
            return Collections.emptyMap();
        }

        for (Map<String, Object> server : episodes) {
            String serverName = String.valueOf(server.getOrDefault("server_name", ""));
            if (targetServer != null && !targetServer.isBlank() && !serverName.equals(targetServer)) {
                continue;
            }

            List<Map<String, Object>> items = safeList(server.get("items"));
            for (Map<String, Object> item : items) {
                String tapSlug = String.valueOf(item.getOrDefault("slug", ""));
                if (targetTap == null || targetTap.isBlank() || tapSlug.equals(targetTap)) {
                    Map<String, Object> selected = new HashMap<>(item);
                    selected.put("server_name", serverName);
                    selected.put("server_label", buildServerDisplayLabel(serverName, ""));
                    return selected;
                }
            }
        }

        return findFirstEpisode(episodes);
    }

    private List<Map<String, Object>> paginateEpisodesForView(
            List<Map<String, Object>> episodes,
            String selectedServer,
            String selectedTap,
            String fallbackLanguage
    ) {
        List<Map<String, Object>> pagedServers = new ArrayList<>();

        for (Map<String, Object> server : episodes) {
            String serverName = String.valueOf(server.getOrDefault("server_name", ""));
            String serverLabel = buildServerDisplayLabel(serverName, fallbackLanguage);
            List<Map<String, Object>> items = safeList(server.get("items"));
            if (items.isEmpty()) {
                continue;
            }

            List<Map<String, Object>> pages = new ArrayList<>();
            int activePage = 1;

            for (int start = 0; start < items.size(); start += EPISODES_PER_PAGE) {
                int end = Math.min(start + EPISODES_PER_PAGE, items.size());
                List<Map<String, Object>> pageItems = new ArrayList<>(items.subList(start, end));
                int pageNumber = (start / EPISODES_PER_PAGE) + 1;

                if (serverName.equals(selectedServer) && containsEpisodeSlug(pageItems, selectedTap)) {
                    activePage = pageNumber;
                }

                String startName = String.valueOf(pageItems.get(0).getOrDefault("name", start + 1));
                String endName = String.valueOf(pageItems.get(pageItems.size() - 1).getOrDefault("name", end));
                pages.add(Map.of(
                        "pageNumber", pageNumber,
                        "label", startName + " - " + endName,
                        "items", pageItems
                ));
            }

            pagedServers.add(Map.of(
                    "server_name", serverName,
                    "server_label", serverLabel,
                    "pages", pages,
                    "activePage", activePage,
                    "totalEpisodes", items.size()
            ));
        }

        return pagedServers;
    }

    private boolean containsEpisodeSlug(List<Map<String, Object>> items, String targetTap) {
        if (targetTap == null || targetTap.isBlank()) {
            return false;
        }

        for (Map<String, Object> item : items) {
            String tapSlug = String.valueOf(item.getOrDefault("slug", ""));
            if (targetTap.equals(tapSlug)) {
                return true;
            }
        }

        return false;
    }

    private String buildServerDisplayLabel(String serverName, String fallbackLanguage) {
        String normalizedServer = normalizeLanguageText(serverName);
        if (normalizedServer.contains("long tieng")) {
            return "Lồng tiếng";
        }
        if (normalizedServer.contains("thuyet minh")) {
            return "Thuyết minh";
        }
        if (normalizedServer.contains("vietsub")) {
            return "Vietsub";
        }

        return serverName == null || serverName.isBlank() ? "Vietsub" : serverName;
    }

    private String buildStreamUrl(String slug, Map<String, Object> episode) {
        if (slug == null || slug.isBlank() || episode == null || episode.isEmpty()) {
            return "";
        }

        String m3u8 = String.valueOf(episode.getOrDefault("m3u8", ""));
        String normalizedM3u8 = movieApiService.normalizeExternalUrl(m3u8);
        if (normalizedM3u8.isBlank() || !movieApiService.isResolvableUrl(normalizedM3u8)) {
            return "";
        }

        String tapSlug = String.valueOf(episode.getOrDefault("slug", ""));
        if (tapSlug.isBlank()) {
            return "";
        }

        String serverName = String.valueOf(episode.getOrDefault("server_name", ""));
        String streamUrl = "/proxy/hls/" + slug + "/" + tapSlug + ".m3u8";
        if (serverName.isBlank()) {
            return streamUrl;
        }

        return streamUrl + "?server=" + org.springframework.web.util.UriUtils.encodeQueryParam(serverName, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String findEpisodeStreamSource(String slug, String server, String tap) {
        Map<String, Object> payload = movieApiService.getMovieDetailData(slug);
        Map<String, Object> movie = extractMovieDetail(payload);
        List<Map<String, Object>> episodes = extractEpisodeServers(movie);
        Map<String, Object> selectedEpisode = findSelectedEpisode(episodes, server, tap);
        return movieApiService.normalizeExternalUrl(String.valueOf(selectedEpisode.getOrDefault("m3u8", "")));
    }

    private String rewritePlaylist(String playlistUrl, String content) {
        StringBuilder rewritten = new StringBuilder();
        String[] lines = content.split("\\R");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                if (trimmed.startsWith("#EXT-X-KEY") && trimmed.contains("URI=\"")) {
                    rewritten.append(rewriteKeyLine(playlistUrl, line));
                } else {
                    rewritten.append(line);
                }
            } else {
                String resolvedUrl = movieApiService.resolveUrl(playlistUrl, trimmed);
                if (isPlaylistUrl(resolvedUrl)) {
                    rewritten.append("/proxy/hls/raw?url=").append(org.springframework.web.util.UriUtils.encode(resolvedUrl, java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    rewritten.append("/proxy/media?url=").append(org.springframework.web.util.UriUtils.encode(resolvedUrl, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            rewritten.append('\n');
        }

        return rewritten.toString();
    }

    private String rewriteKeyLine(String playlistUrl, String line) {
        int start = line.indexOf("URI=\"");
        if (start < 0) {
            return line;
        }

        int valueStart = start + 5;
        int valueEnd = line.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return line;
        }

        String keyUrl = line.substring(valueStart, valueEnd);
        String resolvedUrl = movieApiService.resolveUrl(playlistUrl, keyUrl);
        String proxiedUrl = "/proxy/media?url=" + org.springframework.web.util.UriUtils.encode(resolvedUrl, java.nio.charset.StandardCharsets.UTF_8);
        return line.substring(0, valueStart) + proxiedUrl + line.substring(valueEnd);
    }

    private boolean isPlaylistUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        return url.toLowerCase().contains(".m3u8");
    }
}
