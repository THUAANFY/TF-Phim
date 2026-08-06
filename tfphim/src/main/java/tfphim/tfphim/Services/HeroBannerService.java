package tfphim.tfphim.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HeroBannerService {
    private static final int MAX_HERO_ITEMS = 6;
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

    public HeroBannerService(
            @Value("${tfphim.admin.hero-banner-file:data/hero-banner.json}") String storageFile
    ) {
        Path configuredPath = Path.of(storageFile == null || storageFile.isBlank()
                ? "data/hero-banner.json"
                : storageFile.trim());
        this.storagePath = configuredPath.isAbsolute()
                ? configuredPath.normalize()
                : Path.of("").toAbsolutePath().resolve(configuredPath).normalize();
    }

    public synchronized Map<String, Object> getHeroBanner(boolean enabledOnly) {
        Map<String, Object> root = sanitizeRoot(readRoot(), false);
        if (enabledOnly && !readBoolean(root.get("enabled"), false)) {
            return emptyRoot(false);
        }
        return root;
    }

    public synchronized Map<String, Object> saveHeroBanner(Map<String, Object> payload) throws IOException {
        List<Map<String, Object>> items = sanitizeHeroItems(readHeroItems(payload), true);
        boolean enabled = readBoolean(payload == null ? null : payload.get("enabled"), true) && !items.isEmpty();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("enabled", enabled);
        root.put("updated_at", Instant.now().toString());
        root.put("items", items);
        writeRoot(root);
        return root;
    }

    public synchronized Map<String, Object> clearHeroBanner() throws IOException {
        Map<String, Object> root = emptyRoot(false);
        root.put("updated_at", Instant.now().toString());
        writeRoot(root);
        return root;
    }

    public String getStoragePathForDisplay() {
        return storagePath.toString();
    }

    private Map<String, Object> sanitizeRoot(Map<String, Object> rawRoot, boolean refreshTimestamps) {
        List<Map<String, Object>> items = sanitizeHeroItems(readHeroItems(rawRoot), refreshTimestamps);
        boolean enabled = readBoolean(rawRoot.get("enabled"), !items.isEmpty()) && !items.isEmpty();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", readInt(rawRoot.get("version"), 1));
        root.put("enabled", enabled);
        root.put("updated_at", textValue(rawRoot.get("updated_at"), 40));
        root.put("items", items);
        return root;
    }

    private Map<String, Object> emptyRoot(boolean enabled) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("enabled", enabled);
        root.put("updated_at", "");
        root.put("items", List.of());
        return root;
    }

    private Map<String, Object> readRoot() {
        if (!Files.exists(storagePath)) {
            return emptyRoot(false);
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
                Map<String, Object> root = emptyRoot(!rawList.isEmpty());
                root.put("items", readMapList(rawList));
                return root;
            }
        } catch (Exception ignored) {
        }

        return emptyRoot(false);
    }

    private void writeRoot(Map<String, Object> root) throws IOException {
        Path parent = storagePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        Path tempFile = parent == null
                ? Files.createTempFile("hero-banner-", ".tmp")
                : Files.createTempFile(parent, "hero-banner-", ".tmp");
        Files.writeString(tempFile, json, StandardCharsets.UTF_8);

        try {
            Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<Map<String, Object>> sanitizeHeroItems(List<Map<String, Object>> items, boolean refreshTimestamps) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        String now = Instant.now().toString();
        List<Map<String, Object>> sanitizedItems = new ArrayList<>();

        for (Map<String, Object> item : items) {
            if (sanitizedItems.size() >= MAX_HERO_ITEMS) {
                break;
            }

            Map<String, Object> flatItem = flattenHeroItem(item);
            String slug = textValue(flatItem.get("slug"), 160);
            if (slug.isBlank()) {
                continue;
            }

            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (String field : TEXT_FIELDS) {
                int maxLength = ("description".equals(field) || "content".equals(field))
                        ? MAX_DESCRIPTION_LENGTH
                        : MAX_TEXT_LENGTH;
                String value = textValue(flatItem.get(field), maxLength);
                if (!value.isBlank()) {
                    sanitized.put(field, value);
                }
            }

            sanitized.put("slug", slug);
            sanitized.put("source", normalizeSource(textValue(flatItem.get("source"), 24)));
            sanitized.putIfAbsent("name", slug);
            sanitized.put("enabled", readBoolean(flatItem.get("enabled"), true));
            sanitized.put("order", sanitizedItems.size() + 1);

            String tmdbThumbUrl = normalizeAssetUrl(firstValue(flatItem, "tmdb_thumb_url", "tmdb_backdrop_url", "backdrop_url"));
            String tmdbLogoUrl = normalizeAssetUrl(firstValue(flatItem, "tmdb_logo_url", "logo_url", "clear_logo", "clearlogo"));
            if (!tmdbThumbUrl.isBlank()) {
                sanitized.put("tmdb_thumb_url", tmdbThumbUrl);
            }
            if (!tmdbLogoUrl.isBlank()) {
                sanitized.put("tmdb_logo_url", tmdbLogoUrl);
            }

            String addedAt = textValue(flatItem.get("added_at"), 40);
            sanitized.put("added_at", addedAt.isBlank() ? now : addedAt);
            sanitized.put("updated_at", refreshTimestamps ? now : textValue(flatItem.get("updated_at"), 40));
            sanitizedItems.add(sanitized);
        }

        return sanitizedItems;
    }

    private Map<String, Object> flattenHeroItem(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> flatItem = new LinkedHashMap<>();
        Object movieValue = item.get("movie");
        if (movieValue instanceof Map<?, ?> movieMap) {
            flatItem.putAll(toStringObjectMap(movieMap));
        }
        flatItem.putAll(item);
        flatItem.remove("movie");
        return flatItem;
    }

    private List<Map<String, Object>> readHeroItems(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }

        Object rawItems = payload.get("items");
        if (rawItems instanceof List<?> rawList) {
            return readMapList(rawList);
        }

        Object rawItem = payload.get("item");
        if (rawItem instanceof Map<?, ?> rawMap) {
            return List.of(toStringObjectMap(rawMap));
        }

        Object rawMovie = payload.get("movie");
        if (rawMovie instanceof Map<?, ?> rawMap) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("movie", toStringObjectMap(rawMap));
            copyIfPresent(payload, item, "tmdb_thumb_url");
            copyIfPresent(payload, item, "tmdb_backdrop_url");
            copyIfPresent(payload, item, "tmdb_logo_url");
            copyIfPresent(payload, item, "logo_url");
            copyIfPresent(payload, item, "enabled");
            return List.of(item);
        }

        if (payload.containsKey("slug")) {
            return List.of(payload);
        }

        return List.of();
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private List<Map<String, Object>> readMapList(List<?> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> itemMap) {
                items.add(toStringObjectMap(itemMap));
            }
        }
        return items;
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

    private Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            String text = textValue(value, MAX_TEXT_LENGTH);
            if (!text.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String normalizeSource(String source) {
        String normalized = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return "ophim".equals(normalized) ? "ophim" : "kk";
    }

    private String normalizeAssetUrl(Object value) {
        String text = textValue(value, MAX_TEXT_LENGTH);
        if (text.isBlank()) {
            return "";
        }

        if (text.startsWith("//")) {
            text = "https:" + text;
        }

        if (text.startsWith("/") && looksLikeImagePath(text)) {
            return text;
        }

        try {
            URI uri = URI.create(text.replace(" ", "%20"));
            String scheme = uri.getScheme();
            if (uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return uri.toASCIIString();
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private boolean looksLikeImagePath(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg")
                || normalized.endsWith(".png")
                || normalized.endsWith(".webp")
                || normalized.endsWith(".avif");
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
