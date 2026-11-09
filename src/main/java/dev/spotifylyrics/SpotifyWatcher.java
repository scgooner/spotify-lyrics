package dev.spotifylyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Runs a tiny PowerShell script that reads Windows' media session for Spotify. No Spotify account/API involved. */
public final class SpotifyWatcher {
    private static volatile boolean running = true;
    private static volatile Process proc;

    private SpotifyWatcher() {}

    public static void start() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            SpotifyLyricsClient.LOG.warn("Spotify Lyrics currently only supports Windows.");
            return;
        }
        Thread t = new Thread(SpotifyWatcher::loop, "SpotifyLyrics-Watcher");
        t.setDaemon(true);
        t.start();
    }

    public static void stop() {
        running = false;
        Process p = proc;
        if (p != null) p.destroy();
    }

    private static void loop() {
        while (running) {
            try {
                Path script = Files.createTempFile("spotify_np", ".ps1");
                script.toFile().deleteOnExit();
                try (InputStream in = SpotifyWatcher.class.getResourceAsStream("/spotify_now_playing.ps1")) {
                    Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
                }
                proc = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass", "-File", script.toString())
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = r.readLine()) != null) handle(line.trim());
                }
            } catch (Exception e) {
                SpotifyLyricsClient.LOG.warn("Watcher error", e);
            }
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        }
    }

    private static void handle(String line) {
        if (line.isEmpty() || line.charAt(0) != '{') return;
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            boolean found = o.get("found").getAsBoolean();
            if (!found) { Now.update(false, "", "", 0, 0, false); return; }
            Now.update(true,
                    o.get("title").getAsString(),
                    o.get("artist").getAsString(),
                    o.get("position").getAsDouble(),
                    o.get("duration").getAsDouble(),
                    o.get("playing").getAsBoolean());
        } catch (Exception ignored) {}
    }
}
