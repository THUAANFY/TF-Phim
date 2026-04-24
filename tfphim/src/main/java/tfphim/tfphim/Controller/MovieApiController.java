package tfphim.tfphim.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tfphim.tfphim.Services.MovieApiService;

@RestController
@RequestMapping("/api/movies")
public class MovieApiController {
    private final MovieApiService movieApiService;

    public MovieApiController(MovieApiService movieApiService) {
        this.movieApiService = movieApiService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getMovies(
            @RequestParam String type,
            @RequestParam(defaultValue = "1") int page
    ) {
        try {
            return ResponseEntity.ok(movieApiService.getMovies(type, page));
        } catch (org.springframework.web.client.RestClientException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":\"error\",\"message\":\"Khong the tai du lieu phim.\"}");
        }
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchMovies(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page
    ) {
        try {
            return ResponseEntity.ok(movieApiService.searchMovies(keyword, page));
        } catch (org.springframework.web.client.RestClientException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":\"error\",\"message\":\"Khong the tim kiem phim.\"}");
        }
    }

    @GetMapping(value = "/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getMovieDetail(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(movieApiService.getMovieDetail(slug));
        } catch (org.springframework.web.client.RestClientException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":\"error\",\"message\":\"Khong the tai chi tiet phim.\"}");
        }
    }
}
