/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.values;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.util.download.DownloadUtils;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public abstract class LatestForgeVersion implements ValueSource<String, LatestForgeVersion.Parameters> {
    private static final Logger LOGGER = Logging.getLogger(LatestForgeVersion.class);

    private static final String PROMOTIONS_SLIM = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final Gson GSON = new Gson();

    public interface Parameters extends ValueSourceParameters {
        Property<Boolean> getOffline();

        RegularFileProperty getCacheFile();

        Property<String> getMinecraftVersion();
    }

    @Override
    @Nullable
    public String obtain() {
        try {
            var file = getParameters().getCacheFile().getAsFile().get();

            // Always attempt to re-download promotions UNLESS we are offline
            if (getParameters().getOffline().getOrElse(false)) {
                if (!file.exists())
                    throw new IllegalStateException("Cannot download Forge promotions while offline! Please build the project at least once while online.");
            } else {
                DownloadUtils.downloadFile(file, PROMOTIONS_SLIM);
            }

            var mcVersion = getParameters().getMinecraftVersion().get();
            var jsonStr = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            var json = GSON.fromJson(jsonStr, new TypeToken<Map<String, Object>>(){});
            @SuppressWarnings("unchecked")
            var promos = (Map<String, String>)json.get("promos");
            if (promos == null) {
                LOGGER.error("Could not find \"promos\" entry in json, Checks disabled:\n{}", jsonStr);
                return null;
            }
            var ret = promos.get(mcVersion + "-latest");
            if (ret == null)
                LOGGER.error("Could not find \"{}-latest\" entry in json, Checks disabled:\n{}", mcVersion, jsonStr);
            else
                LOGGER.lifecycle("Found latest Forge version: {}", ret);
            return mcVersion + '-' + ret;
        } catch (Exception e) {
            LOGGER.error("ERROR: Failed to get latest Forge version. Checks using this data will be skipped.", e);
            return null;
        }
    }
}
