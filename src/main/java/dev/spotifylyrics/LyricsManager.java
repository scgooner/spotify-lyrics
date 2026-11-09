package dev.spotifylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches time-synced lyrics from LRCLIB (free, no API key). */
public final class LyricsManager {
    public record Line(double time, String text) {}
    public enum Status { NONE, LOADING, READY, MISSING }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Map<String, List<Line>> CACHE = new ConcurrentHashMap<>();

    private static volatile List<Line> lines = List.of();
    private static volatile Status status = Status.NONE;
    private static volatile String current = "";

    private LyricsManager() {}

    public static List<Line> lines() { return lines; }
    public static Status status() { return status; }

    public static void request(String artist, String title, double duration) {
        String key = artist + "|" + title;
        current = key;
        List<Line> cached = CACHE.get(key);
        if (cached != null) {
            lines = cached;
            status = cached.isEmpty() ? Status.MISSING : Status.READY;
            return;
        }
        lines = List.of();
        status = Status.LOADING;
        CompletableFuture.runAsync(() -> {
            List<Line> result = List.of();
            try {
                result = fetch(artist, title, duration);
            } catch (Exception e) {
                SpotifyLyricsClient.LOG.warn("Lyrics fetch failed", e);
            }
            CACHE.put(key, result);
            if (key.equals(current)) {
                lines = result;
                status = result.isEmpty() ? Status.MISSING : Status.READY;
            }
        });
    }

    private static List<Line> fetch(String artist, String title, double duration) throws Exception {
        String url = "https://lrclib.net/api/get?artist_name=" + enc(artist) + "&track_name=" + enc(title)
                + (duration > 0 ? "&duration=" + Math.round(duration) : "");
        JsonElement e = getJson(url);
        String lrc = (e != null && e.isJsonObject()) ? synced(e.getAsJsonObject()) : null;
        if (lrc == null) {
            JsonElement s = getJson("https://lrclib.net/api/search?q=" + enc(artist + " " + title));
            if (s != null && s.isJsonArray()) {
                JsonArray arr = s.getAsJsonArray();
                for (JsonElement x : arr) {
                    String c = synced(x.getAsJsonObject());
                    if (c != null) { lrc = c; break; }
                }
            }
        }
        return lrc == null ? List.of() : parse(lrc);
    }

    private static String synced(JsonObject o) {
        JsonElement el = o.get("syncedLyrics");
        if (el == null || el.isJsonNull()) return null;
        String s = el.getAsString();
        return s.isBlank() ? null : s;
    }

    private static JsonElement getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "SpotifyLyricsMod/1.0 (Minecraft Fabric)")
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) return null;
        return JsonParser.parseString(res.body());
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = TS.matcher(raw);
            List<Double> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                double t = Integer.parseInt(m.group(1)) * 60.0 + Integer.parseInt(m.group(2));
                if (m.group(3) != null) t += Double.parseDouble("0." + m.group(3));
                times.add(t);
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (double t : times) out.add(new Line(t, text));
        }
        out.sort((a, b) -> Double.compare(a.time(), b.time()));
        return out;
    }
}
