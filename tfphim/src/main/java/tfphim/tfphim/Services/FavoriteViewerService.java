package tfphim.tfphim.Services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class FavoriteViewerService {
    public static final String VIEWER_COOKIE_NAME = "tf_favorite_viewer";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    public String resolveViewerId(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> existingViewerId = readViewerId(request);
        if (existingViewerId.isPresent()) {
            return existingViewerId.get();
        }

        String viewerId = UUID.randomUUID().toString().replace("-", "");
        ResponseCookie cookie = ResponseCookie.from(VIEWER_COOKIE_NAME, viewerId)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return viewerId;
    }

    private Optional<String> readViewerId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> VIEWER_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
