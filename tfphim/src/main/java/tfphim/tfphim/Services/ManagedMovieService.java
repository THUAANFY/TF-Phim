package tfphim.tfphim.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ManagedMovieService {
    private static final String JAPAN_MAZE_KEY = "japanMaze";
    private static final int MAX_SECTION_MOVIES = 40;
    private static final int MAX_TEXT_LENGTH = 900;
    private static final int MAX_DESCRIPTION_LENGTH = 420;
    private static final List<String> TEXT_FIELDS = List.of(
            "slug",
            "source",
            "name",
            "origin_name",
            "original_name",
            "poster_url",
            "thumb_url",
            "card_image_url",
            "quality",
            "lang",
            "language",
            "episode_current",
            "current_episode",
            "total_episodes",
            "episode_total",
            "status",
            "year",
            "type",
            "movie_type",
            "category_type",
            "time",
            "runtime",
            "duration",
            "content_rating",
            "age_rating",
            "description",
            "content",
            "category",
            "country",
            "trailer_url",
            "movie_rating",
            "movie_imdb_rating",
            "movie_tmdb_rating",
            "imdb_rating",
            "tmdb_rating"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storagePath;

    public ManagedMovieService(
            @Value("${tfphim.admin.movies-file:data/quan-ly-phim.json}") String storageFile
    ) {
        Path configuredPath = Path.of(storageFile == null || storageFile.isBlank()
                ? "data/quan-ly-phim.json"
                : storageFile.trim());
        this.storagePath = configuredPath.isAbsolute()
                ? configuredPath.normalize()
                : Path.of("").toAbsolutePath().resolve(configuredPath).normalize();
    }

    public synchronized List<Map<String, Object>> getJapanMazeMovies(boolean enabledOnly) {
        Map<String, Object> root = readRoot();
        List<Map<String, Object>> movies = sanitizeMovieList(readMovieList(root.get(JAPAN_MAZE_KEY)), false);
        if (enabledOnly) {
            movies = movies.stream()
                    .filter(movie -> readBoolean(movie.get("enabled"), true))
                    .toList();
        }
        return movies;
    }

    public synchronized List<Map<String, Object>> saveJapanMazeMovies(List<Map<String, Object>> movies) throws IOException {
        Map<String, Object> root = readRoot();
        List<Map<String, Object>> sanitizedMovies = sanitizeMovieList(movies, true);
        root.put("version", 1);
        root.put("updated_at", Instant.now().toString());
        root.put(JAPAN_MAZE_KEY, sanitizedMovies);
        writeRoot(root);
        return sanitizedMovies;
    }

    public String getStoragePathForDisplay() {
        return storagePath.toString();
    }

    private Map<String, Object> readRoot() {
        if (!Files.exists(storagePath)) {
            return new LinkedHashMap<>();
        }

        try {
            Object raw = objectMapper.readValue(
                    Files.readString(storagePath, StandardCharsets.UTF_8),
                    new TypeReference<Object>() {}
            );
            if (raw instanceof Map<?, ?> rawMap) {
                return toStringObjectMap(rawMap);
            }
            if (raw instanceof List<?> rawList) {
                Map<String, Object> root = new LinkedHashMap<>();
                root.put(JAPAN_MAZE_KEY, readMovieList(rawList));
                return root;
            }
        } catch (Exception ignored) {
        }

        return new LinkedHashMap<>();
    }

    private void writeRoot(Map<String, Object> root) throws IOException {
        Path parent = storagePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        Path tempFile = parent == null
                ? Files.createTempFile("quan-ly-phim-", ".tmp")
                : Files.createTempFile(parent, "quan-ly-phim-", ".tmp");
        Files.writeString(tempFile, json, StandardCharsets.UTF_8);

        try {
            Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<Map<String, Object>> sanitizeMovieList(List<Map<String, Object>> movies, boolean refreshTimestamps) {
        if (movies == null || movies.isEmpty()) {
            return List.of();
        }

        String now = Instant.now().toString();
        Set<String> seenKeys = new HashSet<>();
        List<Map<String, Object>> sanitizedMovies = new ArrayList<>();
        List<Map<String, Object>> orderedMovies = new ArrayList<>(movies);
        orderedMovies.sort(Comparator.comparingInt(movie -> readInt(movie.get("order"), Integer.MAX_VALUE)));

        for (Map<String, Object> movie : orderedMovies) {
            if (sanitizedMovies.size() >= MAX_SECTION_MOVIES) {
                break;
            }

            String slug = textValue(movie.get("slug"), 160);
            if (slug.isBlank()) {
                continue;
            }

            String source = normalizeSource(textValue(movie.get("source"), 24));
            String dedupeKey = source + "|" + slug.toLowerCase(Locale.ROOT);
            if (!seenKeys.add(dedupeKey)) {
                continue;
            }

            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (String field : TEXT_FIELDS) {
                int maxLength = ("description".equals(field) || "content".equals(field))
                        ? MAX_DESCRIPTION_LENGTH
                        : MAX_TEXT_LENGTH;
                String value = textValue(movie.get(field), maxLength);
                if (!value.isBlank()) {
                    sanitized.put(field, value);
                }
            }

            sanitized.put("slug", slug);
            sanitized.put("source", source);
            sanitized.putIfAbsent("name", slug);
            sanitized.put("enabled", readBoolean(movie.get("enabled"), true));
            sanitized.put("order", sanitizedMovies.size() + 1);

            String addedAt = textValue(movie.get("added_at"), 40);
            sanitized.put("added_at", addedAt.isBlank() ? now : addedAt);
            sanitized.put("updated_at", refreshTimestamps ? now : textValue(movie.get("updated_at"), 40));
            sanitizedMovies.add(sanitized);
        }

        return sanitizedMovies;
    }

    private List<Map<String, Object>> readMovieList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList)) {
            return List.of();
        }

        List<Map<String, Object>> movies = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> itemMap) {
                movies.add(toStringObjectMap(itemMap));
            }
        }
        return movies;
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                mapped.put(String.valueOf(key), value);
            }
        });
        return mapped;
    }

    private String normalizeSource(String source) {
        String normalized = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return "ophim".equals(normalized) ? "ophim" : "kk";
    }

    private boolean readBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String text = textValue(value, 16).toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return fallback;
    }

    private int readInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(textValue(value, 20));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String textValue(Object value, int maxLength) {
        if (value == null) {
            return "";
        }

        String text;
        if (value instanceof List<?> listValue) {
            text = listValue.stream()
                    .map(this::compactListItemText)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .reduce((first, second) -> first + " - " + second)
                    .orElse("");
        } else if (value instanceof Map<?, ?> mapValue) {
            text = compactListItemText(mapValue);
        } else {
            text = String.valueOf(value);
        }

        String normalized = text
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if ("null".equalsIgnoreCase(normalized) || "[object Object]".equals(normalized)) {
            return "";
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength).trim() : normalized;
    }

    private String compactListItemText(Object value) {
        if (value instanceof Map<?, ?> itemMap) {
            Object name = itemMap.get("name");
            if (name == null) {
                name = itemMap.get("title");
            }
            if (name == null) {
                name = itemMap.get("slug");
            }
            return textValue(name, 120);
        }
        return textValue(value, 120);
    }
}
