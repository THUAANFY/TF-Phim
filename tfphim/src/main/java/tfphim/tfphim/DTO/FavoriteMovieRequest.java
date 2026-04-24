package tfphim.tfphim.DTO;

public record FavoriteMovieRequest(
    String movieSlug,
    String movieName,
    String originalName,
    String posterUrl,
    String thumbUrl,
    String language,
    String quality,
    String year
) {
}
