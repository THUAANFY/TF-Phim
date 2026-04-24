package tfphim.tfphim.Models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;


@Entity
@Table(
        name = "favorite_movies",
        uniqueConstraints = @UniqueConstraint(name = "uk_favorite_movies_viewer_slug", columnNames = {"viewer_id", "movie_slug"})
)
public class FavoriteMovie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "viewer_id", nullable = false, length = 64)
    private String viewerId;

    @Column(name = "movie_slug", nullable = false, length = 255)
    private String movieSlug;

    @Column(name = "movie_name", nullable = false, length = 500)
    private String movieName;

    @Column(name = "original_name", length = 500)
    private String originalName;

    @Column(name = "poster_url", length = 1000)
    private String posterUrl;

    @Column(name = "thumb_url", length = 1000)
    private String thumbUrl;

    @Column(name = "language", length = 100)
    private String language;

    @Column(name = "quality", length = 50)
    private String quality;

    @Column(name = "release_year", length = 20)
    private String releaseYear;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getViewerId() {
        return viewerId;
    }

    public void setViewerId(String viewerId) {
        this.viewerId = viewerId;
    }

    public String getMovieSlug() {
        return movieSlug;
    }

    public void setMovieSlug(String movieSlug) {
        this.movieSlug = movieSlug;
    }

    public String getMovieName() {
        return movieName;
    }

    public void setMovieName(String movieName) {
        this.movieName = movieName;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public String getQuality() {
        return quality;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(String releaseYear) {
        this.releaseYear = releaseYear;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
