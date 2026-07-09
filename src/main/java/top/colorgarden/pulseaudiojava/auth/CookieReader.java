package top.colorgarden.pulseaudiojava.auth;

import top.colorgarden.pulseaudiojava.protocol.ProtocolConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads the PulseAudio authentication cookie (256 bytes).
 *
 * Search order by platform:
 *
 *   All platforms:
 *     1. $PULSE_COOKIE environment variable
 *
 *   Windows:
 *     2. %APPDATA%\pulse\cookie
 *     3. %USERPROFILE%\.config\pulse\cookie
 *     4. %USERPROFILE%\.pulse-cookie
 *
 *   Linux/macOS:
 *     2. ~/.config/pulse/cookie
 *     3. ~/.pulse-cookie
 */
public class CookieReader {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /**
     * Read the default cookie following PulseAudio's platform-aware search logic.
     * @return 256-byte cookie array
     * @throws IOException if no cookie file found or read error
     */
    public static byte[] readDefaultCookie() throws IOException {
        // 1. $PULSE_COOKIE env var (all platforms)
        String envCookie = System.getenv("PULSE_COOKIE");
        if (envCookie != null) envCookie = envCookie.trim();
        if (envCookie != null && !envCookie.isEmpty()) {
            Path path = Paths.get(envCookie);
            if (Files.exists(path)) {
                return readCookie(path);
            }
        }

        if (IS_WINDOWS) {
            return readWindowsCookie();
        } else {
            return readUnixCookie();
        }
    }

    private static byte[] readWindowsCookie() throws IOException {
        String home = System.getProperty("user.home");

        // %APPDATA%\pulse\cookie
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            Path path = Paths.get(appData, "pulse", "cookie");
            if (Files.exists(path)) return readCookie(path);
        }

        // %USERPROFILE%\.config\pulse\cookie
        Path configCookie = Paths.get(home, ".config", "pulse", "cookie");
        if (Files.exists(configCookie)) return readCookie(configCookie);

        // %USERPROFILE%\.pulse-cookie
        Path fallback = Paths.get(home, ".pulse-cookie");
        if (Files.exists(fallback)) return readCookie(fallback);

        throw new IOException(
                "PulseAudio cookie not found on Windows. Searched:\n" +
                "  $PULSE_COOKIE\n" +
                "  %APPDATA%\\pulse\\cookie\n" +
                "  %USERPROFILE%\\.config\\pulse\\cookie\n" +
                "  %USERPROFILE%\\.pulse-cookie\n" +
                "Set PULSE_COOKIE env var to the cookie file path.");
    }

    private static byte[] readUnixCookie() throws IOException {
        String home = System.getProperty("user.home");

        // ~/.config/pulse/cookie
        Path configCookie = Paths.get(home, ".config", "pulse", "cookie");
        if (Files.exists(configCookie)) return readCookie(configCookie);

        // ~/.pulse-cookie (fallback)
        Path fallbackCookie = Paths.get(home, ".pulse-cookie");
        if (Files.exists(fallbackCookie)) return readCookie(fallbackCookie);

        throw new IOException(
                "PulseAudio cookie not found on Linux/macOS. Searched:\n" +
                "  $PULSE_COOKIE\n" +
                "  ~/.config/pulse/cookie\n" +
                "  ~/.pulse-cookie\n" +
                "Set PULSE_COOKIE env var to the cookie file path.");
    }

    /**
     * Read cookie from a specific path.
     * @param path the cookie file path
     * @return exactly 256 bytes
     * @throws IOException if file is wrong size or read error
     */
    public static byte[] readCookie(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length != ProtocolConstants.COOKIE_LENGTH) {
            throw new IOException(
                    "Cookie file " + path + " has wrong size: " + data.length +
                    " (expected " + ProtocolConstants.COOKIE_LENGTH + ")");
        }
        return data;
    }
}
