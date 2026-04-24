package tfphim.tfphim.Services;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import tfphim.tfphim.DAO.FavoriteMovieRepository;
import tfphim.tfphim.DTO.FavoriteMovieRequest;
import tfphim.tfphim.DTO.FavoriteMovieResponse;
import tfphim.tfphim.Models.FavoriteMovie;

@Service
public class FavoriteMovieService {
    private final FavoriteMovieRepository favoriteMovieRepository;

    public FavoriteMovieService(FavoriteMovieRepository favoriteMovieRepository) {
        this.favoriteMovieRepository = favoriteMovieRepository;
    }

    public List<FavoriteMovieResponse> getFavorites(String viewerId) {
        return favoriteMovieRepository.findByViewerIdOrderByCreatedAtDesc(viewerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Set<String> getFavoriteSlugs(String viewerId) {
        return favoriteMovieRepository.findByViewerIdOrderByCreatedAtDesc(viewerId).stream()
                .map(FavoriteMovie::getMovieSlug)
                .collect(Collectors.toSet());
    }

    public boolean isFavorite(String viewerId, String movieSlug) {
        return favoriteMovieRepository.existsByViewerIdAndMovieSlug(viewerId, normalizeRequired(movieSlug, "movieSlug"));
    }

    @Transactional
    public FavoriteMovieResponse saveFavorite(String viewerId, FavoriteMovieRequest request) {
        String movieSlug = normalizeRequired(request.movieSlug(), "movieSlug");
        String movieName = normalizeRequired(request.movieName(), "movieName");

        FavoriteMovie favoriteMovie = favoriteMovieRepository.findByViewerIdAndMovieSlug(viewerId, movieSlug)
                .orElseGet(FavoriteMovie::new);

        favoriteMovie.setViewerId(viewerId);
        favoriteMovie.setMovieSlug(movieSlug);
        favoriteMovie.setMovieName(movieName);
        favoriteMovie.setOriginalName(normalizeOptional(request.originalName()));
        favoriteMovie.setPosterUrl(normalizeOptional(request.posterUrl()));
        favoriteMovie.setThumbUrl(normalizeOptional(request.thumbUrl()));
        favoriteMovie.setLanguage(normalizeOptional(request.language()));
        favoriteMovie.setQuality(normalizeOptional(request.quality()));
        favoriteMovie.setReleaseYear(normalizeOptional(request.year()));

        return toResponse(favoriteMovieRepository.save(favoriteMovie));
    }

    @Transactional
    public boolean removeFavorite(String viewerId, String movieSlug) {
        return favoriteMovieRepository.deleteByViewerIdAndMovieSlug(viewerId, normalizeRequired(movieSlug, "movieSlug")) > 0;
    }

    private FavoriteMovieResponse toResponse(FavoriteMovie favoriteMovie) {
        return new FavoriteMovieResponse(
                favoriteMovie.getId(),
                favoriteMovie.getMovieSlug(),
                favoriteMovie.getMovieName(),
                favoriteMovie.getOriginalName(),
                favoriteMovie.getPosterUrl(),
                favoriteMovie.getThumbUrl(),
                favoriteMovie.getLanguage(),
                favoriteMovie.getQuality(),
                favoriteMovie.getReleaseYear(),
                favoriteMovie.getCreatedAt(),
                true
        );
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
