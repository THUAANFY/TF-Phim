package tfphim.tfphim.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tfphim.tfphim.DTO.FavoriteMovieRequest;
import tfphim.tfphim.DTO.FavoriteMovieResponse;
import tfphim.tfphim.Services.FavoriteMovieService;
import tfphim.tfphim.Services.FavoriteViewerService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final FavoriteMovieService favoriteMovieService;
    private final FavoriteViewerService favoriteViewerService;

    public FavoriteController(FavoriteMovieService favoriteMovieService, FavoriteViewerService favoriteViewerService) {
        this.favoriteMovieService = favoriteMovieService;
        this.favoriteViewerService = favoriteViewerService;
    }

    @GetMapping
    public ResponseEntity<?> getFavorites(HttpServletRequest request, HttpServletResponse response) {
        try {
            String viewerId = favoriteViewerService.resolveViewerId(request, response);
            List<FavoriteMovieResponse> favorites = favoriteMovieService.getFavorites(viewerId);
            return ResponseEntity.ok(Map.of(
                    "items", favorites,
                    "favoriteSlugs", favoriteMovieService.getFavoriteSlugs(viewerId)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/status/{slug}")
    public ResponseEntity<?> getFavoriteStatus(@PathVariable String slug, HttpServletRequest request, HttpServletResponse response) {
        try {
            String viewerId = favoriteViewerService.resolveViewerId(request, response);
            return ResponseEntity.ok(Map.of(
                    "movieSlug", slug,
                    "favorite", favoriteMovieService.isFavorite(viewerId, slug)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> saveFavorite(
            @RequestBody FavoriteMovieRequest favoriteMovieRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            String viewerId = favoriteViewerService.resolveViewerId(request, response);
            FavoriteMovieResponse favoriteMovie = favoriteMovieService.saveFavorite(viewerId, favoriteMovieRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(favoriteMovie);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<?> removeFavorite(@PathVariable String slug, HttpServletRequest request, HttpServletResponse response) {
        try {
            String viewerId = favoriteViewerService.resolveViewerId(request, response);
            boolean removed = favoriteMovieService.removeFavorite(viewerId, slug);
            return ResponseEntity.ok(Map.of(
                    "movieSlug", slug,
                    "favorite", false,
                    "removed", removed
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
