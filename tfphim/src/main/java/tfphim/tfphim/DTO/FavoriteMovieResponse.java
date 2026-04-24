package tfphim.tfphim.DTO;

import java.time.LocalDateTime;

public record FavoriteMovieResponse(
    Long id,
    String movieSlug,
    String movieName,
    String originalName,
    String posterUrl,
    String thumbUrl,
    String language,
    String quality,
    String year,
    LocalDateTime createdAt,
    boolean favorite
) {
}
