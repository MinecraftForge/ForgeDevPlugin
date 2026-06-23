/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.values;

import com.google.gson.reflect.TypeToken;
import net.minecraftforge.forgedev.ForgeDevPlugin;
import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.util.data.json.JsonData;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.impldep.com.google.common.io.Files;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A wrapper around Mavenzier's tasks that output a json map
 * This is intended to be extended by classes that expose providers for the returned data.
 */
public abstract class MavenizerData {
    protected static final Logger LOGGER = Logging.getLogger(MavenizerData.class);
    protected final Project project;
    protected final ForgeDevPlugin plugin;
    private final MapProperty<String, String> data;

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public MavenizerData(final Project project, final ForgeDevPlugin plugin) {
        this.project = project;
        this.plugin = plugin;

        this.data = getObjects().mapProperty(String.class, String.class)
            .convention(getProviders().provider(this::mavenizer));
        this.data.finalizeValueOnRead();
    }

    protected abstract String getTaskName();
    protected abstract List<String> getArgs();
    protected @Nullable File getOutputDir() {
        return null;
    }

    protected Provider<String> optional(String key) {
        return this.data.getting(key);
    }

    protected Provider<String> get(String key) {
        return this.data.getting(key).orElse(getProviders().provider(() -> {
            throw new IllegalStateException("Mavenizer did not output expected json data " + key);
        }));
    }

    protected Provider<String> get(String key, @Nullable String _default, String requiredVersion) {
        return this.data.getting(key).orElse(getProviders().provider(() -> {
            // This should only happen when someone hardcodes their tool version, warn them
            var message = "Mavenizer did not output expected json data " + key +", Make sure you're using Mavenizer >= " + requiredVersion;
            if (_default != null) {
                LOGGER.warn(message);
                return _default;
            }
            throw new IllegalStateException("Mavenizer did not output expected json data " + key +", Make sure you're using Mavenizer >= " + requiredVersion);
        }));
    }

    protected Provider<File> getFile(String key) {
        return get(key).map(this::file);
    }
    private File file(String path) {
        return getOutputDir() == null ? project.file(path) : new File(getOutputDir(), path);
    }

    protected List<File> loadList(File source) {
        var ret = new ArrayList<File>();
        try {
            for (var line : Files.readLines(source, StandardCharsets.UTF_8))
                ret.add(file(line));
        } catch (IOException e) {
            throw new RuntimeException("Could not read file: " + source, e);
        }
        return ret;
    }

    private Map<String, String> mavenizer() {
        var outputJson = plugin.localCaches().file(getTaskName() + ".json").get().getAsFile();
        return getProviders().of(MavenizerValueSource.class, spec -> {
            spec.parameters(params -> {
                var tool = plugin.getTool(Tools.MAVENIZER);
                params.getClasspath().setFrom(tool.getClasspath());
                params.getJavaLauncher().set(tool.getJavaLauncher().map(JavaLauncher::getExecutablePath));

                var toolCache = plugin.globalCaches()
                    .dir(tool.getName().toLowerCase(Locale.ENGLISH))
                    .map(plugin.getExtension().getProblems().ensureFileLocation());
                var cache = toolCache.get().dir("caches").getAsFile();

                params.getArguments().set(getProviders().provider(() -> {
                    var ret = new ArrayList<>(List.of(
                        "--cache", cache.getAbsolutePath(),
                        "--output",  outputJson.getAbsolutePath()
                    ));
                    if (getOutputDir() != null) {
                        ret.add("--output-dir");
                        ret.add(getOutputDir().getAbsolutePath());
                    }
                    ret.addAll(getArgs());
                    return ret;
                }));
            });
        })
        .map(v -> JsonData.fromJson(outputJson, new TypeToken<Map<String, String>>(){})).get();
    }
}
