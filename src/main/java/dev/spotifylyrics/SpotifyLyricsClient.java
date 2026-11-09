package dev.spotifylyrics;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyLyricsClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("spotifylyrics");

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("spotifylyrics", "main"));

        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotifylyrics.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
        KeyMapping earlier = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotifylyrics.earlier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, category));
        KeyMapping later = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotifylyrics.later", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.consumeClick()) {
                LyricsHud.enabled = !LyricsHud.enabled;
                LyricsHud.toast("Lyrics " + (LyricsHud.enabled ? "on" : "off"));
            }
            while (earlier.consumeClick()) {
                LyricsHud.offset -= 0.25;
                LyricsHud.toast(String.format("Lyrics offset %+.2fs", LyricsHud.offset));
            }
            while (later.consumeClick()) {
                LyricsHud.offset += 0.25;
                LyricsHud.toast(String.format("Lyrics offset %+.2fs", LyricsHud.offset));
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("spotifylyrics", "lyrics"), LyricsHud::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> SpotifyWatcher.stop());
        SpotifyWatcher.start();
    }
}
