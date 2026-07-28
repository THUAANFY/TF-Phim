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
import org.springframework.web.util.HtmlUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.text.NumberFormat;

import tfphim.tfphim.Services.MovieApiService;

@Controller
public class HomeController {
    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    private static final int EPISODES_PER_PAGE = 100;
    private static final int MAX_LISTING_MOVIES_PER_PAGE = 24;
    private static final String KK_IMAGE_BASE_URL = "https://img.phimapi.com/";
    private static final String KK_MOVIE_IMAGE_BASE_URL = KK_IMAGE_BASE_URL + "uploads/movies/";
    private static final String OPHIM_IMAGE_BASE_URL = "https://img.ophim.live/";
    private static final String OPHIM_MOVIE_IMAGE_BASE_URL = OPHIM_IMAGE_BASE_URL + "uploads/movies/";
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
        Map<String, Object> pagePayload = ensureMetadataPayload(
                movieApiService.getMoviesData(type, safePage),
                () -> movieApiService.getMoviesData(type, safePage)
        );
        int totalItems = resolveTotalItems(pagePayload);
        int totalPages = Math.max(resolveTotalPages(pagePayload, totalItems), 1);
        int currentPage = Math.min(safePage, totalPages);
        if (currentPage != safePage) {
            pagePayload = ensureMetadataPayload(
                    movieApiService.getMoviesData(type, currentPage),
                    () -> movieApiService.getMoviesData(type, currentPage)
            );
        }
        List<Map<String, Object>> movies = mergeSupplementalMovies(
                extractMovieItems(pagePayload, type),
                extractMovieItems(movieApiService.getOphimMoviesData(type, currentPage), type, "ophim")
        );
        movies = limitListingMovies(movies);

        String categoryTitle = resolveListingTitle(pagePayload, categoryMeta.getOrDefault("title", type));
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
        model.addAttribute("totalItemsText", formatCount(totalItems));
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
            model.addAttribute("totalItemsText", "0");
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("hasPrevious", false);
            model.addAttribute("hasNext", false);
            model.addAttribute("errorMessage", "Vui lòng nhập từ khóa để tìm kiếm phim.");
            return "search-results";
        }

        int safePage = Math.max(page, 1);
        Map<String, Object> pagePayload = ensureMetadataPayload(
                movieApiService.searchMoviesData(normalizedKeyword, safePage),
                () -> movieApiService.searchMoviesData(normalizedKeyword, safePage)
        );
        int totalItems = resolveTotalItems(pagePayload);
        int totalPages = Math.max(resolveTotalPages(pagePayload, totalItems), 1);
        int currentPage = Math.min(safePage, totalPages);
        if (currentPage != safePage) {
            pagePayload = ensureMetadataPayload(
                    movieApiService.searchMoviesData(normalizedKeyword, currentPage),
                    () -> movieApiService.searchMoviesData(normalizedKeyword, currentPage)
            );
        }
        List<Map<String, Object>> movies = mergeSupplementalMovies(
                extractMovieItems(pagePayload),
                currentPage == 1
                        ? extractMovieItems(movieApiService.searchOphimMoviesData(normalizedKeyword, currentPage), "", "ophim")
                        : Collections.emptyList()
        );
        movies = sortSearchResults(movies);
        totalItems += Math.max(0, movies.size() - extractMovieItems(pagePayload).size());

        model.addAttribute("movies", movies);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("totalItemsText", formatCount(totalItems));
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
        Map<String, Object> pagePayload = ensureMetadataPayload(
                movieApiService.getMoviesByCountryData(slug, safePage),
                () -> movieApiService.getMoviesByCountryData(slug, safePage)
        );
        int totalItems = resolveTotalItems(pagePayload);
        int totalPages = Math.max(resolveTotalPages(pagePayload, totalItems), 1);
        int currentPage = Math.min(safePage, totalPages);
        if (currentPage != safePage) {
            pagePayload = ensureMetadataPayload(
                    movieApiService.getMoviesByCountryData(slug, currentPage),
                    () -> movieApiService.getMoviesByCountryData(slug, currentPage)
            );
        }
        List<Map<String, Object>> movies = mergeSupplementalMovies(
                extractMovieItems(pagePayload),
                extractMovieItems(movieApiService.getOphimMoviesByCountryData(slug, currentPage), "", "ophim")
        );
        movies = limitListingMovies(movies);
        String countryTitle = resolveListingTitle(pagePayload, slug);

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
        model.addAttribute("totalItemsText", formatCount(totalItems));
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
        Map<String, Object> pagePayload = ensureMetadataPayload(
                movieApiService.getMoviesByGenreData(slug, safePage),
                () -> movieApiService.getMoviesByGenreData(slug, safePage)
        );
        int totalItems = resolveTotalItems(pagePayload);
        int totalPages = Math.max(resolveTotalPages(pagePayload, totalItems), 1);
        int currentPage = Math.min(safePage, totalPages);
        if (currentPage != safePage) {
            pagePayload = ensureMetadataPayload(
                    movieApiService.getMoviesByGenreData(slug, currentPage),
                    () -> movieApiService.getMoviesByGenreData(slug, currentPage)
            );
        }
        List<Map<String, Object>> movies = mergeSupplementalMovies(
                extractMovieItems(pagePayload),
                extractMovieItems(movieApiService.getOphimMoviesByGenreData(slug, currentPage), "", "ophim")
        );
        movies = limitListingMovies(movies);
        String genreTitle = resolveListingTitle(pagePayload, slug);

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
        model.addAttribute("totalItemsText", formatCount(totalItems));
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
            List<Map<String, Object>> episodes = extractEpisodeServers(movie, payload);
            int totalEpisodes = extractNonNegativeInt(movie.get("total_episodes"), 0);
            int airedEpisodes = resolveAiredEpisodes(movie, episodes);
            boolean showServerCards = !episodes.isEmpty()
                    && episodes.stream().allMatch(server -> safeList(server.get("items")).size() <= 1);

            if (movie.isEmpty()) {
                model.addAttribute("pageTitle", "Không tìm thấy phim");
                model.addAttribute("errorMessage", "Không tìm thấy thông tin phim.");
                return "movie-detail";
            }

            model.addAttribute("pageTitle", movie.getOrDefault("name", "Chi tiết phim"));
            model.addAttribute("movie", movie);
            model.addAttribute("movieDescription", resolveMovieDescription(movie));
            model.addAttribute("categories", extractCategories(movie));
            model.addAttribute("actors", enrichActorsWithTmdbImages(movie, extractActors(movie)));
            model.addAttribute("galleryImages", buildGalleryImages(movie));
            model.addAttribute("languageBadges", extractLanguageBadges(movie));
            model.addAttribute("releaseYear", findCategoryValue(movie, "Năm"));
            model.addAttribute("movieDuration", resolveMovieField(movie, "time", "runtime", "duration"));
            model.addAttribute("movieCountry", resolveMovieField(movie, "country", "countries"));
            model.addAttribute("movieDirector", resolveMovieField(movie, "director", "directors"));
            model.addAttribute("isTrailerMovie", isTrailerMovie(movie));
            model.addAttribute("episodes", episodes);
            model.addAttribute("showServerCards", showServerCards);
            model.addAttribute("detailServerCards", buildDetailServerCards(
                    episodes,
                    String.valueOf(movie.getOrDefault("slug", slug)),
                    String.valueOf(movie.getOrDefault("name", ""))
            ));
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
            log.error("Cannot load movie detail for slug={}", slug, ex);
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
            List<Map<String, Object>> episodes = extractEpisodeServers(movie, payload);
            Map<String, Object> selectedEpisode = findSelectedEpisode(episodes, server, tap);
            boolean showServerCards = !episodes.isEmpty()
                    && episodes.stream().allMatch(serverItem -> safeList(serverItem.get("items")).size() <= 1);

            if (movie.isEmpty()) {
                model.addAttribute("pageTitle", "Không xem được phim");
                model.addAttribute("errorMessage", "Không tìm thấy thông tin phim.");
                return "movie-watch";
            }

            if (selectedEpisode.isEmpty()) {
                model.addAttribute("pageTitle", movie.getOrDefault("name", "Xem phim"));
                model.addAttribute("movie", movie);
                model.addAttribute("movieDescription", resolveMovieDescription(movie));
                model.addAttribute("errorMessage", "Phim này hiện chưa có nguồn phát khả dụng.");
                return "movie-watch";
            }

            model.addAttribute("pageTitle", movie.getOrDefault("name", "Xem phim"));
            model.addAttribute("movie", movie);
            model.addAttribute("movieDescription", resolveMovieDescription(movie));
            model.addAttribute("episodes", episodes);
            model.addAttribute("showServerCards", showServerCards);
            model.addAttribute("detailServerCards", buildDetailServerCards(
                    episodes,
                    String.valueOf(movie.getOrDefault("slug", slug)),
                    String.valueOf(movie.getOrDefault("name", ""))
            ));
            model.addAttribute("pagedEpisodes", paginateEpisodesForView(
                    episodes,
                    String.valueOf(selectedEpisode.getOrDefault("server_name", "")),
                    String.valueOf(selectedEpisode.getOrDefault("slug", "")),
                    String.valueOf(movie.getOrDefault("language", ""))
            ));
            model.addAttribute("selectedEpisode", selectedEpisode);
            model.addAttribute("languageBadges", extractLanguageBadges(movie));
            model.addAttribute("categories", extractCategories(movie));
            model.addAttribute("actors", enrichActorsWithTmdbImages(movie, extractActors(movie)));
            model.addAttribute("releaseYear", findCategoryValue(movie, "Năm"));
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
        List<Map<String, Object>> episodes = extractEpisodeServers(movie, payload);
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
        Object rawCategories = movie.get("category");
        List<Map<String, Object>> flatCategories = safeList(rawCategories);
        if (flatCategories.isEmpty()) {
            flatCategories = safeList(movie.get("categories"));
        }

        for (Map<String, Object> item : flatCategories) {
            String name = String.valueOf(item.getOrDefault("name", "")).trim();
            if (!name.isBlank()) {
                result.add(Map.of(
                        "group", "Thể loại",
                        "name", name
                ));
            }
        }

        if (!result.isEmpty()) {
            return result;
        }

        Map<String, Object> categoryGroups = safeMap(rawCategories);

        for (Object groupValue : categoryGroups.values()) {
            Map<String, Object> group = safeMap(groupValue);
            Map<String, Object> groupInfo = safeMap(group.get("group"));
            String groupName = String.valueOf(groupInfo.getOrDefault("name", ""));

            for (Map<String, Object> item : safeList(group.get("list"))) {
                String name = String.valueOf(item.getOrDefault("name", "")).trim();
                if (!name.isBlank()) {
                    result.add(Map.of(
                            "group", groupName,
                            "name", name
                    ));
                }
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

    private List<Map<String, Object>> extractActors(Map<String, Object> movie) {
        Object rawActors = movie.getOrDefault("actor", movie.get("actors"));
        List<Map<String, Object>> actors = new ArrayList<>();
        LinkedHashSet<String> seenNames = new LinkedHashSet<>();

        if (rawActors instanceof CharSequence sequence) {
            for (String name : sequence.toString().split(",")) {
                addActor(actors, seenNames, name, "");
            }
            return actors;
        }

        if (rawActors instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?>) {
                    Map<String, Object> actorMap = safeMap(item);
                    String name = normalizeMovieTextValue(actorMap.get("name"));
                    if (name.isBlank()) {
                        name = normalizeMovieTextValue(actorMap.get("title"));
                    }
                    if (name.isBlank()) {
                        name = normalizeMovieTextValue(actorMap.get("value"));
                    }
                    if (name.isBlank()) {
                        name = normalizeMovieTextValue(actorMap.get("actor"));
                    }
                    if (name.isBlank()) {
                        name = normalizeMovieTextValue(actorMap.get("actor_name"));
                    }
                    String image = normalizeMovieImageUrl(String.valueOf(
                            actorMap.getOrDefault("avatar", actorMap.getOrDefault("image", actorMap.getOrDefault("profile_path", "")))
                    ));
                    addActor(actors, seenNames, name, image);
                } else {
                    addActor(actors, seenNames, normalizeMovieTextValue(item), "");
                }
            }
            return actors;
        }

        if (rawActors != null && rawActors.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(rawActors);
            for (int i = 0; i < length; i++) {
                addActor(actors, seenNames, normalizeMovieTextValue(java.lang.reflect.Array.get(rawActors, i)), "");
            }
            return actors;
        }

        addActor(actors, seenNames, normalizeMovieTextValue(rawActors), "");
        return actors;
    }

    private List<Map<String, Object>> enrichActorsWithTmdbImages(Map<String, Object> movie, List<Map<String, Object>> actors) {
        if (actors.isEmpty()) {
            return actors;
        }

        List<String> actorNames = new ArrayList<>();
        for (Map<String, Object> actor : actors) {
            String actorName = normalizeMovieTextValue(actor.get("name"));
            if (!actorName.isBlank()) {
                actorNames.add(actorName);
            }
        }
        if (actorNames.isEmpty()) {
            return actors;
        }

        String name = resolveFirstMovieText(movie, "name");
        String originalName = resolveFirstMovieText(movie, "original_name", "origin_name");
        String year = resolveFirstMovieText(movie, "year", "release_year");
        if (year.isBlank()) {
            year = findCategoryValue(movie, "NÄƒm");
        }
        String tmdbId = resolveExternalMovieText(movie, "tmdb", "id", "tmdb_id");
        String tmdbType = resolveExternalMovieText(movie, "tmdb", "type", "tmdb_type");
        if (tmdbType.isBlank()) {
            tmdbType = resolveFirstMovieText(movie, "type", "movie_type", "category_type");
        }
        String imdbId = resolveExternalMovieText(movie, "imdb", "id", "imdb_id");

        Map<String, String> tmdbActorImages = movieApiService.getTmdbActorImages(
                name,
                originalName,
                year,
                tmdbId,
                tmdbType,
                imdbId,
                actorNames
        );
        if (tmdbActorImages.isEmpty()) {
            return actors;
        }

        List<Map<String, Object>> enrichedActors = new ArrayList<>();
        for (Map<String, Object> actor : actors) {
            String actorName = normalizeMovieTextValue(actor.get("name"));
            String tmdbImageUrl = tmdbActorImages.getOrDefault(actorName, "");
            if (tmdbImageUrl.isBlank()) {
                enrichedActors.add(actor);
                continue;
            }

            Map<String, Object> enrichedActor = new java.util.HashMap<>(actor);
            enrichedActor.put("image_url", tmdbImageUrl);
            enrichedActors.add(enrichedActor);
        }
        return enrichedActors;
    }

    private List<Map<String, Object>> buildGalleryImages(Map<String, Object> movie) {
        List<Map<String, Object>> images = new ArrayList<>();
        LinkedHashSet<String> seenUrls = new LinkedHashSet<>();

        addGalleryImage(images, seenUrls, movie.get("thumb_url"), "movie-api", "backdrop");
        addGalleryImage(images, seenUrls, movie.get("poster_url"), "movie-api", "poster");
        collectGalleryImages(images, seenUrls, movie.get("images"), "movie-api");
        collectGalleryImages(images, seenUrls, movie.get("image"), "movie-api");
        collectGalleryImages(images, seenUrls, movie.get("photos"), "movie-api");
        collectGalleryImages(images, seenUrls, movie.get("backdrops"), "movie-api");
        collectGalleryImages(images, seenUrls, movie.get("posters"), "movie-api");
        collectGalleryImages(images, seenUrls, movie.get("gallery"), "movie-api");
        collectTmdbGalleryImages(images, seenUrls, movie);

        return images;
    }

    private void collectTmdbGalleryImages(List<Map<String, Object>> images, LinkedHashSet<String> seenUrls, Map<String, Object> movie) {
        String name = resolveFirstMovieText(movie, "name");
        String originalName = resolveFirstMovieText(movie, "original_name", "origin_name");
        String year = resolveFirstMovieText(movie, "year", "release_year");
        if (year.isBlank()) {
            year = findCategoryValue(movie, "Năm");
        }

        String tmdbId = resolveExternalMovieText(movie, "tmdb", "id", "tmdb_id");
        String tmdbType = resolveExternalMovieText(movie, "tmdb", "type", "tmdb_type");
        if (tmdbType.isBlank()) {
            tmdbType = resolveFirstMovieText(movie, "type", "movie_type", "category_type");
        }
        String imdbId = resolveExternalMovieText(movie, "imdb", "id", "imdb_id");

        for (Map<String, Object> tmdbImage : movieApiService.getTmdbGalleryImages(
                name,
                originalName,
                year,
                tmdbId,
                tmdbType,
                imdbId,
                10
        )) {
            addGalleryImage(
                    images,
                    seenUrls,
                    tmdbImage.get("url"),
                    "tmdb",
                    String.valueOf(tmdbImage.getOrDefault("type", "image")),
                    tmdbImage.getOrDefault("width", 0),
                    tmdbImage.getOrDefault("height", 0)
            );
        }
    }

    private String resolveExternalMovieText(Map<String, Object> movie, String groupKey, String nestedKey, String directKey) {
        String directValue = resolveFirstMovieText(movie, directKey);
        if (!directValue.isBlank()) {
            return directValue;
        }

        Map<String, Object> group = safeMap(movie.get(groupKey));
        return normalizeMovieTextValue(group.get(nestedKey));
    }

    private void collectGalleryImages(List<Map<String, Object>> images, LinkedHashSet<String> seenUrls, Object value, String source) {
        if (value == null) {
            return;
        }
        if (value instanceof CharSequence sequence) {
            for (String part : sequence.toString().split(",")) {
                addGalleryImage(images, seenUrls, part, source, "image");
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> item = safeMap(map);
            String type = String.valueOf(item.getOrDefault("type", item.getOrDefault("kind", "image")));
            Object url = item.getOrDefault(
                    "url",
                    item.getOrDefault("src", item.getOrDefault("file_path", item.getOrDefault("path", "")))
            );
            addGalleryImage(images, seenUrls, url, source, type, item.getOrDefault("width", 0), item.getOrDefault("height", 0));
            for (Object nested : item.values()) {
                if (nested instanceof Iterable<?> || nested instanceof Map<?, ?>) {
                    collectGalleryImages(images, seenUrls, nested, source);
                }
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectGalleryImages(images, seenUrls, item, source);
            }
        }
    }

    private void addGalleryImage(List<Map<String, Object>> images, LinkedHashSet<String> seenUrls, Object rawUrl, String source, String type) {
        addGalleryImage(images, seenUrls, rawUrl, source, type, 0, 0);
    }

    private void addGalleryImage(
            List<Map<String, Object>> images,
            LinkedHashSet<String> seenUrls,
            Object rawUrl,
            String source,
            String type,
            Object width,
            Object height
    ) {
        String url = normalizeMovieImageUrl(String.valueOf(rawUrl == null ? "" : rawUrl), source);
        if (url.isBlank() || !seenUrls.add(url)) {
            return;
        }

        Map<String, Object> item = new HashMap<>();
        item.put("url", url);
        item.put("source", source == null || source.isBlank() ? "api" : source);
        item.put("type", type == null || type.isBlank() ? "image" : type);
        item.put("width", extractNonNegativeInt(width, 0));
        item.put("height", extractNonNegativeInt(height, 0));
        images.add(item);
    }

    private int resolveMovieYear(Map<String, Object> movie) {
        int year = extractNonNegativeInt(movie.get("year"), 0);
        if (year > 0) {
            return year;
        }
        year = extractNonNegativeInt(movie.get("release_year"), 0);
        if (year > 0) {
            return year;
        }
        return extractNonNegativeInt(findCategoryValue(movie, "Năm"), 0);
    }

    private void addActor(List<Map<String, Object>> actors, LinkedHashSet<String> seenNames, String rawName, String imageUrl) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank() || !seenNames.add(name.toLowerCase(Locale.ROOT))) {
            return;
        }

        actors.add(Map.of(
                "name", name,
                "image_url", imageUrl == null ? "" : imageUrl
        ));
    }

    private String resolveMovieField(Map<String, Object> movie, String primaryKey, String... fallbackKeys) {
        String value = normalizeMovieTextValue(movie.get(primaryKey));
        if (!value.isBlank()) {
            return value;
        }

        for (String key : fallbackKeys) {
            value = normalizeMovieTextValue(movie.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }

        return "Đang cập nhật";
    }

    private String resolveMovieDescription(Map<String, Object> movie) {
        return cleanMovieDescription(resolveFirstMovieText(movie, "content", "description", "excerpt"));
    }

    private String cleanMovieDescription(String value) {
        String unescaped = HtmlUtils.htmlUnescape(String.valueOf(value == null ? "" : value));
        return unescaped
                .replaceAll("(?is)<br\\s*/?>", " ")
                .replaceAll("(?is)</p\\s*>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeMovieTextValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof CharSequence sequence) {
            return sequence.toString().trim();
        }

        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("name", "title", "value")) {
                Object nested = map.get(key);
                String text = normalizeMovieTextValue(nested);
                if (!text.isBlank()) {
                    return text;
                }
            }

            List<String> parts = new ArrayList<>();
            for (Object entryValue : map.values()) {
                String text = normalizeMovieTextValue(entryValue);
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
            return String.join(", ", parts).trim();
        }

        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            for (Object item : iterable) {
                String text = normalizeMovieTextValue(item);
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
            return String.join(", ", parts).trim();
        }

        if (value.getClass().isArray()) {
            List<String> parts = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                String text = normalizeMovieTextValue(java.lang.reflect.Array.get(value, i));
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
            return String.join(", ", parts).trim();
        }

        return String.valueOf(value).trim();
    }

    private boolean isTrailerMovie(Map<String, Object> movie) {
        String currentEpisode = normalizeMovieTextValue(movie.get("current_episode"));
        if (currentEpisode.isBlank()) {
            currentEpisode = normalizeMovieTextValue(movie.get("episode_current"));
        }

        return normalizeLanguageText(currentEpisode).toLowerCase().contains("trailer");
    }

    private List<String> extractLanguageBadges(Map<String, Object> movie) {
        return extractLanguageBadges(resolveLanguageBadgeSource(movie));
    }

    private List<String> extractLanguageBadges(String language) {
        if (language.isBlank()) {
            return Collections.emptyList();
        }

        String normalized = normalizeLanguageText(language);
        List<String> badges = new ArrayList<>();

        if (normalized.contains("vietsub")) {
            badges.add("P.\u0110\u1ec1");
        }
        if (normalized.contains("thuyet minh")) {
            badges.add("T.Minh");
        }
        if (normalized.contains("long tieng")) {
            badges.add("L.Ti\u1ebfng");
        }

        return badges;
    }

    private List<Map<String, String>> buildLanguageBadgeItems(String language) {
        List<Map<String, String>> items = new ArrayList<>();

        for (String badge : extractLanguageBadges(language)) {
            if ("P.\u0110\u1ec1".equals(badge)) {
                items.add(Map.of("label", "P.\u0110\u1ec1", "className", "lang-sub"));
                continue;
            }
            if ("T.Minh".equals(badge)) {
                items.add(Map.of("label", "T.Minh", "className", "lang-dub"));
                continue;
            }
            items.add(Map.of("label", "L.Ti\u1ebfng", "className", "lang-voice"));
        }

        return items;
    }

    private List<Map<String, String>> buildLanguageBadgeItems(Map<String, Object> movie) {
        return buildLanguageBadgeItems(resolveLanguageBadgeSource(movie));
    }

    private String resolveLanguageBadgeSource(Map<String, Object> movie) {
        return String.join(" ",
                resolveFirstMovieText(movie, "language", "lang"),
                resolveFirstMovieText(movie, "episode_current", "current_episode"),
                resolveFirstMovieText(movie, "status", "episode_status")
        ).trim();
    }

    private String normalizeLanguageText(String language) {
        String normalized = Normalizer.normalize(language, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase();

        return normalized.replaceAll("\\s+", " ").trim();
    }

    private List<Map<String, Object>> extractMovieItems(Map<String, Object> payload) {
        return extractMovieItems(payload, "");
    }

    private String buildRuntimeText(Object runtime) {
        int minutes = extractNonNegativeInt(runtime, 0);
        return minutes > 0 ? minutes + " phút" : "";
    }

    private List<Map<String, Object>> extractMovieItems(Map<String, Object> payload, String listingType) {
        Object items = payload.get("items");
        if (items instanceof List<?>) {
            return normalizeMovieListItems(safeList(items), listingType);
        }

        Map<String, Object> data = safeMap(payload.get("data"));
        Object dataItems = data.get("items");
        if (dataItems instanceof List<?>) {
            return normalizeMovieListItems(safeList(dataItems), listingType);
        }

        Object rawData = payload.get("data");
        if (rawData instanceof List<?>) {
            return normalizeMovieListItems(safeList(rawData), listingType);
        }

        return Collections.emptyList();
    }

    private List<Map<String, Object>> extractMovieItems(Map<String, Object> payload, String listingType, String source) {
        Object items = payload.get("items");
        if (items instanceof List<?>) {
            return normalizeMovieListItems(safeList(items), listingType, source);
        }

        Map<String, Object> data = safeMap(payload.get("data"));
        Object dataItems = data.get("items");
        if (dataItems instanceof List<?>) {
            return normalizeMovieListItems(safeList(dataItems), listingType, source);
        }

        Object rawData = payload.get("data");
        if (rawData instanceof List<?>) {
            return normalizeMovieListItems(safeList(rawData), listingType, source);
        }

        return Collections.emptyList();
    }

    private List<Map<String, Object>> mergeSupplementalMovies(
            List<Map<String, Object>> primaryMovies,
            List<Map<String, Object>> supplementalMovies
    ) {
        List<Map<String, Object>> mergedMovies = new ArrayList<>();
        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();

        appendUniqueMovies(mergedMovies, seenKeys, primaryMovies);
        appendUniqueMovies(mergedMovies, seenKeys, supplementalMovies);
        return mergedMovies;
    }

    private List<Map<String, Object>> limitListingMovies(List<Map<String, Object>> movies) {
        if (movies == null || movies.size() <= MAX_LISTING_MOVIES_PER_PAGE) {
            return movies == null ? Collections.emptyList() : movies;
        }

        return new ArrayList<>(movies.subList(0, MAX_LISTING_MOVIES_PER_PAGE));
    }

    private void appendUniqueMovies(
            List<Map<String, Object>> target,
            LinkedHashSet<String> seenKeys,
            List<Map<String, Object>> movies
    ) {
        for (Map<String, Object> movie : movies) {
            String slug = String.valueOf(movie.getOrDefault("slug", "")).trim();
            String key = slug.isBlank()
                    ? normalizeLanguageText(String.valueOf(movie.getOrDefault("name", "")))
                    : slug;
            if (key.isBlank() || seenKeys.add(key)) {
                target.add(movie);
            }
        }
    }

    private Map<String, Object> extractMovieDetail(Map<String, Object> payload) {
        String source = String.valueOf(payload.getOrDefault("source", ""));
        Map<String, Object> movie = safeMap(payload.get("movie"));
        if (!movie.isEmpty()) {
            return normalizeMovieDetail(movie, source);
        }

        Map<String, Object> data = safeMap(payload.get("data"));
        movie = safeMap(data.get("movie"));
        if (!movie.isEmpty()) {
            return normalizeMovieDetail(movie, source);
        }

        if (!data.isEmpty()) {
            return normalizeMovieDetail(data, source);
        }

        return Collections.emptyMap();
    }

    private List<Map<String, Object>> extractEpisodeServers(Map<String, Object> movie, Map<String, Object> payload) {
        List<Map<String, Object>> episodes = safeList(movie.get("episodes"));
        if (!episodes.isEmpty()) {
            return episodes;
        }

        episodes = adaptKkPhimEpisodes(safeList(payload.get("episodes")));
        if (!episodes.isEmpty()) {
            return episodes;
        }

        Map<String, Object> data = safeMap(movie.get("data"));
        return safeList(data.get("episodes"));
    }

    private Map<String, Object> normalizeMovieDetail(Map<String, Object> movie) {
        return normalizeMovieDetail(movie, "");
    }

    private Map<String, Object> normalizeMovieDetail(Map<String, Object> movie, String source) {
        Map<String, Object> normalized = new HashMap<>(movie);
        if (!normalized.containsKey("language") && normalized.containsKey("lang")) {
            normalized.put("language", normalized.get("lang"));
        }
        if (!normalized.containsKey("current_episode") && normalized.containsKey("episode_current")) {
            normalized.put("current_episode", normalized.get("episode_current"));
        }
        if (!normalized.containsKey("total_episodes") && normalized.containsKey("episode_total")) {
            normalized.put("total_episodes", normalized.get("episode_total"));
        }
        if (!normalized.containsKey("original_name") && normalized.containsKey("origin_name")) {
            normalized.put("original_name", normalized.get("origin_name"));
        }
        if (source != null && !source.isBlank()) {
            normalized.putIfAbsent("source", source);
        }
        normalizeMovieImages(normalized, source);
        normalized.put("card_image_url", resolveCardImageUrl(normalized, source));
        normalizeMovieRatings(normalized);
        normalized.put("language_badges", buildLanguageBadgeItems(normalized));
        return normalized;
    }

    private List<Map<String, Object>> normalizeMovieListItems(List<Map<String, Object>> items) {
        return normalizeMovieListItems(items, "");
    }

    private List<Map<String, Object>> normalizeMovieListItems(List<Map<String, Object>> items, String listingType) {
        return normalizeMovieListItems(items, listingType, "");
    }

    private List<Map<String, Object>> normalizeMovieListItems(List<Map<String, Object>> items, String listingType, String source) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> mapped = new HashMap<>(item);
            if (!mapped.containsKey("language") && mapped.containsKey("lang")) {
                mapped.put("language", mapped.get("lang"));
            }
            if (source != null && !source.isBlank()) {
                mapped.putIfAbsent("source", source);
            }
            normalizeMovieImages(mapped, source);
            mapped.put("card_image_url", resolveCardImageUrl(mapped, source));
            normalizeMovieRatings(mapped);
            mapped.put("card_episode_label", resolveCardEpisodeLabel(mapped, listingType));
            mapped.put("language_badges", buildLanguageBadgeItems(mapped));
            normalized.add(mapped);
        }
        return normalized;
    }

    private String resolveCardEpisodeLabel(Map<String, Object> movie, String listingType) {
        String episode = normalizeMovieTextValue(movie.get("episode_current"));
        if (episode.isBlank()) {
            episode = normalizeMovieTextValue(movie.get("current_episode"));
        }
        if (episode.isBlank()) {
            return "";
        }
        if (!isSeriesCardMovie(movie, listingType)) {
            return episode;
        }

        String normalized = normalizeLanguageText(episode);
        if (normalized.contains("hoan tat") || normalized.contains("full") || normalized.contains("completed")) {
            return "HO\u00c0N T\u1ea4T";
        }

        return episode;
    }

    private boolean isSeriesCardMovie(Map<String, Object> movie, String listingType) {
        String normalizedListingType = normalizeLanguageText(listingType == null ? "" : listingType);
        if ("phim-le".equals(normalizedListingType)) {
            return false;
        }
        if ("phim-bo".equals(normalizedListingType)) {
            return true;
        }

        String type = normalizeLanguageText(resolveFirstMovieText(movie, "type", "movie_type", "category_type"));
        if ("single".equals(type) || "phim-le".equals(type)) {
            return false;
        }
        if ("series".equals(type) || "tvshows".equals(type) || "phim-bo".equals(type)) {
            return true;
        }

        int totalEpisodes = extractNonNegativeInt(
                movie.getOrDefault("total_episodes", movie.get("episode_total")),
                0
        );
        return totalEpisodes > 1;
    }

    private String resolveFirstMovieText(Map<String, Object> movie, String... keys) {
        for (String key : keys) {
            String value = normalizeMovieTextValue(movie.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void normalizeMovieRatings(Map<String, Object> movie) {
        String imdbRating = normalizeRatingValue(resolveMovieImdbRating(movie));
        String tmdbRating = normalizeRatingValue(resolveMovieTmdbRating(movie));

        movie.put("movie_imdb_rating", imdbRating);
        movie.put("movie_tmdb_rating", tmdbRating);
        movie.put("movie_rating", !imdbRating.isBlank() ? imdbRating : tmdbRating);
    }

    private String resolveMovieImdbRating(Map<String, Object> movie) {
        String rating = resolveFirstMovieText(
                movie,
                "movie_imdb_rating",
                "imdb_vote_average",
                "imdb_rating",
                "rating_imdb",
                "imdb_score"
        );
        if (!rating.isBlank()) {
            return rating;
        }

        Map<String, Object> imdb = safeMap(movie.get("imdb"));
        rating = resolveFirstMovieText(imdb, "vote_average", "rating", "score");
        if (!rating.isBlank()) {
            return rating;
        }

        return resolveFirstMovieText(movie, "movie_rating", "rating");
    }

    private String resolveMovieTmdbRating(Map<String, Object> movie) {
        String rating = resolveFirstMovieText(
                movie,
                "movie_tmdb_rating",
                "tmdb_vote_average",
                "tmdb_rating",
                "rating_tmdb",
                "tmdb_score"
        );
        if (!rating.isBlank()) {
            return rating;
        }

        Map<String, Object> tmdb = safeMap(movie.get("tmdb"));
        return resolveFirstMovieText(tmdb, "vote_average", "rating", "score");
    }

    private String normalizeRatingValue(String value) {
        String rating = normalizeMovieTextValue(value);
        if (rating.isBlank() || "null".equalsIgnoreCase(rating) || "n/a".equalsIgnoreCase(rating)) {
            return "";
        }

        try {
            double numericRating = Double.parseDouble(rating.replace(",", "."));
            return numericRating > 0 ? rating : "";
        } catch (Exception ignored) {
            return rating;
        }
    }

    private String normalizeMovieImageUrl(String url) {
        return normalizeMovieImageUrl(url, "");
    }

    private void normalizeMovieImages(Map<String, Object> movie, String source) {
        String posterUrl = normalizeMovieImageUrl(String.valueOf(movie.getOrDefault("poster_url", "")), source);
        String thumbUrl = normalizeMovieImageUrl(String.valueOf(movie.getOrDefault("thumb_url", "")), source);

        if (isOphimSource(source)) {
            movie.put("poster_url", thumbUrl);
            movie.put("thumb_url", posterUrl);
            return;
        }

        movie.put("poster_url", posterUrl);
        movie.put("thumb_url", thumbUrl);
    }

    private String resolveCardImageUrl(Map<String, Object> movie, String source) {
        String posterUrl = String.valueOf(movie.getOrDefault("poster_url", "")).trim();
        String thumbUrl = String.valueOf(movie.getOrDefault("thumb_url", "")).trim();

        if (isOphimSource(source)) {
            return !thumbUrl.isBlank() ? thumbUrl : posterUrl;
        }

        return !posterUrl.isBlank() ? posterUrl : thumbUrl;
    }

    private String normalizeMovieImageUrl(String url, String source) {
        if (url == null || url.isBlank()) {
            return "";
        }

        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }

        String normalizedPath = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        if (normalizedPath.startsWith("uploads/movies/")) {
            return (isOphimSource(source) ? OPHIM_IMAGE_BASE_URL : KK_IMAGE_BASE_URL) + normalizedPath;
        }
        if (normalizedPath.startsWith("upload/")) {
            return KK_IMAGE_BASE_URL + normalizedPath;
        }
        if (looksLikeImageFile(normalizedPath)) {
            return (isOphimSource(source) ? OPHIM_MOVIE_IMAGE_BASE_URL : KK_MOVIE_IMAGE_BASE_URL) + normalizedPath;
        }

        return trimmed;
    }

    private boolean isOphimSource(String source) {
        return source != null && "ophim".equalsIgnoreCase(source.trim());
    }

    private boolean looksLikeImageFile(String path) {
        String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".jpg")
                || lowerPath.endsWith(".jpeg")
                || lowerPath.endsWith(".png")
                || lowerPath.endsWith(".webp")
                || lowerPath.endsWith(".avif");
    }

    private List<Map<String, Object>> adaptKkPhimEpisodes(List<Map<String, Object>> rawServers) {
        if (rawServers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> adapted = new ArrayList<>();
        for (Map<String, Object> server : rawServers) {
            String serverName = String.valueOf(server.getOrDefault("server_name", ""));
            List<Map<String, Object>> serverData = safeList(server.get("server_data"));
            if (serverData.isEmpty()) {
                continue;
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> item : serverData) {
                Map<String, Object> normalizedItem = new HashMap<>(item);
                if (!normalizedItem.containsKey("m3u8") && normalizedItem.containsKey("link_m3u8")) {
                    normalizedItem.put("m3u8", normalizedItem.get("link_m3u8"));
                }
                if (!normalizedItem.containsKey("embed") && normalizedItem.containsKey("link_embed")) {
                    normalizedItem.put("embed", normalizedItem.get("link_embed"));
                }
                items.add(normalizedItem);
            }

            adapted.add(Map.of(
                    "server_name", serverName,
                    "items", items
            ));
        }
        return adapted;
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

    private List<Map<String, Object>> sortSearchResults(List<Map<String, Object>> movies) {
        if (movies.isEmpty()) {
            return movies;
        }

        List<Map<String, Object>> sortedMovies = new ArrayList<>(movies);
        sortedMovies.sort((first, second) -> {
            int trailerCompare = Integer.compare(resolveSearchTrailerScore(second), resolveSearchTrailerScore(first));
            if (trailerCompare != 0) {
                return trailerCompare;
            }

            int episodeCompare = Integer.compare(resolveSearchEpisodeScore(second), resolveSearchEpisodeScore(first));
            if (episodeCompare != 0) {
                return episodeCompare;
            }

            return Integer.compare(resolveSearchYearScore(second), resolveSearchYearScore(first));
        });
        return sortedMovies;
    }

    private int resolveSearchTrailerScore(Map<String, Object> movie) {
        String episode = normalizeLanguageText(resolveFirstMovieText(movie, "current_episode", "episode_current"));
        String status = normalizeLanguageText(resolveFirstMovieText(movie, "status", "episode_status"));
        String type = normalizeLanguageText(resolveFirstMovieText(movie, "type", "movie_type", "category_type"));
        String quality = normalizeLanguageText(resolveFirstMovieText(movie, "quality"));
        String combinedText = String.join(" ", episode, status, type, quality);
        String trailerUrl = resolveFirstMovieText(movie, "trailer_url");

        if (combinedText.contains("trailer") || combinedText.contains("sap chieu")) {
            return 1;
        }

        return !trailerUrl.isBlank() && resolveSearchEpisodeScore(movie) == 0 ? 1 : 0;
    }

    private int resolveSearchEpisodeScore(Map<String, Object> movie) {
        int fromTotal = extractNonNegativeInt(
                movie.getOrDefault("total_episodes", movie.get("episode_total")),
                0
        );
        if (fromTotal > 0) {
            return fromTotal;
        }

        int fromCurrent = extractEpisodeCountFromLabel(String.valueOf(
                movie.getOrDefault("current_episode", movie.getOrDefault("episode_current", ""))
        ));
        if (fromCurrent > 0) {
            return fromCurrent;
        }

        return isSeriesCardMovie(movie, "") ? 1 : 0;
    }

    private int resolveSearchYearScore(Map<String, Object> movie) {
        int year = extractYear(movie.get("year"));
        if (year > 0) {
            return year;
        }

        year = extractYear(movie.get("release_year"));
        if (year > 0) {
            return year;
        }

        Map<String, Object> modified = safeMap(movie.get("modified"));
        year = extractYear(modified.get("time"));
        if (year > 0) {
            return year;
        }

        return extractYear(movie.get("created"));
    }

    private int extractYear(Object value) {
        if (value instanceof Number number) {
            int year = number.intValue();
            return year >= 1800 && year <= 3000 ? year : 0;
        }

        if (value instanceof String text) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(18\\d{2}|19\\d{2}|20\\d{2}|21\\d{2})\\b")
                    .matcher(text);
            if (matcher.find()) {
                return extractNonNegativeInt(matcher.group(1), 0);
            }
        }

        return 0;
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

        return fallback;
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

        return fallback;
    }

    private int resolveTotalItems(Map<String, Object> payload) {
        Map<String, Object> paginate = safeMap(payload.get("paginate"));
        int totalItems = extractNonNegativeInt(paginate.get("total_items"), -1);
        if (totalItems >= 0) {
            return totalItems;
        }

        Map<String, Object> paginationRoot = safeMap(payload.get("pagination"));
        totalItems = extractNonNegativeInt(
                paginationRoot.getOrDefault("totalItems", paginationRoot.get("total_items")),
                -1
        );
        if (totalItems >= 0) {
            return totalItems;
        }

        Map<String, Object> data = safeMap(payload.get("data"));
        Map<String, Object> params = safeMap(data.get("params"));
        Map<String, Object> pagination = safeMap(params.get("pagination"));
        totalItems = extractNonNegativeInt(pagination.get("totalItems"), -1);
        if (totalItems >= 0) {
            return totalItems;
        }

        return extractMovieItems(payload).size();
    }

    private Map<String, Object> ensureMetadataPayload(
            Map<String, Object> payload,
            java.util.function.Supplier<Map<String, Object>> refresher
    ) {
        if (payload != null && !payload.isEmpty()) {
            int totalItems = resolveTotalItems(payload);
            if (totalItems > 0 || !extractMovieItems(payload).isEmpty()) {
                return payload;
            }
        }

        try {
            Map<String, Object> refreshed = refresher.get();
            return refreshed != null ? refreshed : Collections.emptyMap();
        } catch (Exception ignored) {
            return payload != null ? payload : Collections.emptyMap();
        }
    }

    private int resolveTotalPages(Map<String, Object> payload, int totalItems) {
        Map<String, Object> paginationRoot = safeMap(payload.get("pagination"));
        int totalPages = extractPositiveInt(
                paginationRoot.getOrDefault("totalPages", paginationRoot.get("total_pages")),
                -1
        );
        if (totalPages > 0) {
            return totalPages;
        }

        Map<String, Object> data = safeMap(payload.get("data"));
        Map<String, Object> params = safeMap(data.get("params"));
        Map<String, Object> pagination = safeMap(params.get("pagination"));
        totalPages = extractPositiveInt(
                pagination.getOrDefault("totalPages", pagination.get("total_pages")),
                -1
        );
        if (totalPages > 0) {
            return totalPages;
        }

        int perPage = extractPositiveInt(
                pagination.getOrDefault("totalItemsPerPage", pagination.get("itemsPerPage")),
                extractMovieItems(payload).size()
        );
        if (totalItems > 0 && perPage > 0) {
            return (int) Math.ceil((double) totalItems / perPage);
        }

        return extractMovieItems(payload).isEmpty() ? 1 : 1;
    }

    private String formatCount(int value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.max(value, 0));
    }

    private String resolveListingTitle(Map<String, Object> payload, String fallback) {
        Map<String, Object> cat = safeMap(payload.get("cat"));
        String fromCat = String.valueOf(cat.getOrDefault("title", cat.getOrDefault("name", ""))).trim();
        if (!fromCat.isBlank()) {
            return fromCat;
        }

        Map<String, Object> data = safeMap(payload.get("data"));
        String titlePage = String.valueOf(data.getOrDefault("titlePage", "")).trim();
        if (!titlePage.isBlank()) {
            return titlePage;
        }

        List<Map<String, Object>> breadcrumb = safeList(data.get("breadCrumb"));
        if (!breadcrumb.isEmpty()) {
            Map<String, Object> last = breadcrumb.get(breadcrumb.size() - 1);
            String breadcrumbName = String.valueOf(last.getOrDefault("name", "")).trim();
            if (!breadcrumbName.isBlank()) {
                return breadcrumbName;
            }
        }

        return fallback;
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

    private List<Map<String, Object>> buildDetailServerCards(List<Map<String, Object>> episodes, String slug, String movieName) {
        if (episodes.isEmpty() || slug == null || slug.isBlank()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        int index = 1;

        for (Map<String, Object> server : episodes) {
            String serverName = String.valueOf(server.getOrDefault("server_name", ""));
            List<Map<String, Object>> items = safeList(server.get("items"));
            if (items.isEmpty()) {
                continue;
            }

            Map<String, Object> first = new HashMap<>(items.get(0));
            first.put("server_name", serverName);
            String serverLabel = resolveServerCardLabel(serverName);
            first.put("server_label", serverLabel);
            first.put("card_title", movieName == null || movieName.isBlank() ? serverLabel : movieName);
            first.put("card_subtitle", serverLabel + " #" + index);
            first.put("card_tone", resolveServerCardTone(serverName, serverLabel));
            first.put("card_icon", resolveServerCardIcon(serverName, serverLabel));
            first.put("watch_url", buildWatchUrl(slug, first));
            cards.add(first);
            index++;
        }

        return cards;
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

                String startName = normalizeEpisodeRangeName(pageItems.get(0).getOrDefault("name", start + 1));
                String endName = normalizeEpisodeRangeName(pageItems.get(pageItems.size() - 1).getOrDefault("name", end));
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

    private String normalizeEpisodeRangeName(Object value) {
        String name = String.valueOf(value == null ? "" : value).trim();
        if (name.isBlank()) {
            return "";
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.startsWith("tập")) {
            return name.substring(3).trim();
        }
        if (lowerName.startsWith("tap")) {
            return name.substring(3).trim();
        }

        return name;
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

    private String resolveServerCardTone(String serverName, String serverLabel) {
        String normalized = normalizeLanguageText(serverName + " " + serverLabel);
        if (normalized.contains("thuyet minh")) {
            return "dub";
        }
        if (normalized.contains("long tieng")) {
            return "voice";
        }
        return "sub";
    }

    private String resolveServerCardLabel(String serverName) {
        String normalized = normalizeLanguageText(serverName);
        if (normalized.contains("thuyet minh")) {
            return "Thuyết Minh";
        }
        if (normalized.contains("long tieng")) {
            return "Lồng tiếng";
        }
        return "Phụ đề";
    }

    private String resolveServerCardIcon(String serverName, String serverLabel) {
        String normalized = normalizeLanguageText(serverName + " " + serverLabel);
        if (normalized.contains("thuyet minh")) {
            return "fa-solid fa-wave-square";
        }
        if (normalized.contains("long tieng")) {
            return "fa-solid fa-bullhorn";
        }
        return "fa-solid fa-closed-captioning";
    }

    private String buildWatchUrl(String slug, Map<String, Object> episode) {
        if (slug == null || slug.isBlank() || episode == null || episode.isEmpty()) {
            return "";
        }

        String tapSlug = String.valueOf(episode.getOrDefault("slug", ""));
        if (tapSlug.isBlank()) {
            return "";
        }

        String serverName = String.valueOf(episode.getOrDefault("server_name", ""));
        String watchUrl = "/xem/" + slug + "/" + tapSlug;
        if (serverName.isBlank()) {
            return watchUrl;
        }

        return watchUrl + "?server=" + org.springframework.web.util.UriUtils.encodeQueryParam(serverName, java.nio.charset.StandardCharsets.UTF_8);
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
        List<Map<String, Object>> episodes = extractEpisodeServers(movie, payload);
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
