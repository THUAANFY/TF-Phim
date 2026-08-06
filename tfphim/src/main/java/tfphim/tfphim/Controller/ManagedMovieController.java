package tfphim.tfphim.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tfphim.tfphim.Services.HeroBannerService;
import tfphim.tfphim.Services.ManagedMovieService;

@Controller
public class ManagedMovieController {
    private static final Logger log = LoggerFactory.getLogger(ManagedMovieController.class);
    private final ManagedMovieService managedMovieService;
    private final HeroBannerService heroBannerService;

    public ManagedMovieController(ManagedMovieService managedMovieService, HeroBannerService heroBannerService) {
        this.managedMovieService = managedMovieService;
        this.heroBannerService = heroBannerService;
    }

    @GetMapping({"/admin", "/dashboard"})
    public String adminDashboard(Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("adminPage", true);
        model.addAttribute("adminCards", buildAdminCards());
        return "admin-dashboard";
    }

    @GetMapping("/quan-ly-phim")
    public String legacyManageMovies() {
        return "redirect:/me-cung-phim-nhat";
    }

    @GetMapping("/me-cung-phim-nhat")
    public String manageMovies(Model model) {
        model.addAttribute("pageTitle", "M\u00ea Cung Phim Nh\u1eadt");
        model.addAttribute("adminPage", true);
        return "quan-ly-phim";
    }

    @GetMapping("/quan-ly-hero-banner")
    public String manageHeroBanner(Model model) {
        model.addAttribute("pageTitle", "Hero Banner");
        model.addAttribute("adminPage", true);
        return "quan-ly-hero-banner";
    }

    @GetMapping(value = "/api/quan-ly-phim/japan-maze", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getJapanMazeMovies(
            @RequestParam(defaultValue = "false") boolean enabledOnly
    ) {
        List<Map<String, Object>> movies = managedMovieService.getJapanMazeMovies(enabledOnly);
        return ResponseEntity.ok(buildResponse(movies));
    }

    @PutMapping(value = "/api/quan-ly-phim/japan-maze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveJapanMazeMovies(
            @RequestBody(required = false) List<Map<String, Object>> movies
    ) {
        try {
            List<Map<String, Object>> savedMovies = managedMovieService.saveJapanMazeMovies(
                    movies == null ? List.of() : movies
            );
            return ResponseEntity.ok(buildResponse(savedMovies));
        } catch (IOException ex) {
            log.error("Cannot save managed Japan maze movies", ex);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "error");
            response.put("message", "Khong the luu danh sach phim.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping(value = "/api/quan-ly-phim/hero-banner", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getHeroBanner(
            @RequestParam(defaultValue = "false") boolean enabledOnly
    ) {
        Map<String, Object> heroBanner = heroBannerService.getHeroBanner(enabledOnly);
        return ResponseEntity.ok(buildHeroResponse(heroBanner));
    }

    @PutMapping(value = "/api/quan-ly-phim/hero-banner", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveHeroBanner(
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        try {
            Map<String, Object> heroBanner = heroBannerService.saveHeroBanner(
                    payload == null ? Map.of("enabled", false, "items", List.of()) : payload
            );
            return ResponseEntity.ok(buildHeroResponse(heroBanner));
        } catch (IOException ex) {
            log.error("Cannot save hero banner", ex);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "error");
            response.put("message", "Khong the luu hero banner.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping(value = "/api/quan-ly-phim/hero-banner", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearHeroBanner() {
        try {
            Map<String, Object> heroBanner = heroBannerService.clearHeroBanner();
            return ResponseEntity.ok(buildHeroResponse(heroBanner));
        } catch (IOException ex) {
            log.error("Cannot clear hero banner", ex);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "error");
            response.put("message", "Khong the xoa hero banner.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private Map<String, Object> buildResponse(List<Map<String, Object>> movies) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("items", movies);
        response.put("count", movies.size());
        response.put("storageFile", managedMovieService.getStoragePathForDisplay());
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildAdminCards() {
        List<Map<String, Object>> cards = new ArrayList<>();

        List<Map<String, Object>> japanMazeMovies = managedMovieService.getJapanMazeMovies(false);
        long enabledJapanMazeMovies = japanMazeMovies.stream()
                .filter(movie -> !Boolean.FALSE.equals(movie.get("enabled")))
                .count();
        cards.add(buildAdminCard(
                "M\u00ea Cung Phim Nh\u1eadt",
                "Trang qu\u1ea3n l\u00fd",
                "/me-cung-phim-nhat",
                "clapperboard",
                japanMazeMovies.size() + " phim",
                enabledJapanMazeMovies + " \u0111ang b\u1eadt",
                "japan"
        ));

        Map<String, Object> heroBanner = heroBannerService.getHeroBanner(false);
        List<Map<String, Object>> heroItems = heroBanner.get("items") instanceof List<?> rawItems
                ? rawItems.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList()
                : List.of();
        cards.add(buildAdminCard(
                "Hero Banner",
                "Banner trang ch\u1ee7",
                "/quan-ly-hero-banner",
                "image",
                heroItems.size() + "/6 phim",
                Boolean.TRUE.equals(heroBanner.get("enabled")) ? "\u0110ang b\u1eadt" : "\u0110ang t\u1eaft",
                "hero"
        ));

        return cards;
    }

    private Map<String, Object> buildAdminCard(
            String title,
            String subtitle,
            String href,
            String icon,
            String statValue,
            String statLabel,
            String accent
    ) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", title);
        card.put("subtitle", subtitle);
        card.put("href", href);
        card.put("icon", icon);
        card.put("statValue", statValue);
        card.put("statLabel", statLabel);
        card.put("accent", accent);
        return card;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildHeroResponse(Map<String, Object> heroBanner) {
        List<Map<String, Object>> items = heroBanner.get("items") instanceof List<?> rawItems
                ? rawItems.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList()
                : List.of();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("enabled", Boolean.TRUE.equals(heroBanner.get("enabled")) && !items.isEmpty());
        response.put("items", items);
        response.put("item", items.isEmpty() ? null : items.get(0));
        response.put("count", items.size());
        response.put("storageFile", heroBannerService.getStoragePathForDisplay());
        return response;
    }
}
