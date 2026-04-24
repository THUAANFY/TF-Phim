package tfphim.tfphim.DAO;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tfphim.tfphim.Models.FavoriteMovie;

public interface FavoriteMovieRepository extends JpaRepository<FavoriteMovie, Long>{
    List<FavoriteMovie> findByViewerIdOrderByCreatedAtDesc(String viewerId);

    Optional<FavoriteMovie> findByViewerIdAndMovieSlug(String viewerId, String movieSlug);

    boolean existsByViewerIdAndMovieSlug(String viewerId, String movieSlug);

    long deleteByViewerIdAndMovieSlug(String viewerId, String movieSlug);
}
