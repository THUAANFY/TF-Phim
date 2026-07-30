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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tfphim.tfphim.Services.ManagedMovieService;

@Controller
public class ManagedMovieController {
    private static final Logger log = LoggerFactory.getLogger(ManagedMovieController.class);
    private final ManagedMovieService managedMovieService;

    public ManagedMovieController(ManagedMovieService managedMovieService) {
        this.managedMovieService = managedMovieService;
    }

    @GetMapping("/quan-ly-phim")
    public String manageMovies(Model model) {
        model.addAttribute("pageTitle", "Qu\u1ea3n l\u00fd phim");
        model.addAttribute("adminPage", true);
        return "quan-ly-phim";
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

    private Map<String, Object> buildResponse(List<Map<String, Object>> movies) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("items", movies);
        response.put("count", movies.size());
        response.put("storageFile", managedMovieService.getStoragePathForDisplay());
        return response;
    }
}
