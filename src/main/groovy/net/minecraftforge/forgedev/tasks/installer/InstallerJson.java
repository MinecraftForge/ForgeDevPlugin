/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.legacy.values.LibraryInfo;
import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.forgedev.tasks.installer.steps.Extract;
import net.minecraftforge.forgedev.tasks.installer.steps.ExtractBundle;
import net.minecraftforge.forgedev.tasks.installer.steps.Step;
import net.minecraftforge.util.hash.HashFunction;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public abstract class InstallerJson extends DefaultTask {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    protected abstract @Inject ProviderFactory getProviders();
    protected abstract @Inject ObjectFactory getObjects();

    @OutputFile public abstract RegularFileProperty getOutput();

    @InputFiles public abstract ConfigurableFileCollection getInput();
    @InputFile @Optional public abstract RegularFileProperty getIcon();
    @Input public abstract Property<String> getLauncherJsonName();
    @Input public abstract Property<String> getLogo();
    @Input public abstract Property<String> getMirrors();
    @Input public abstract Property<String> getWelcome();
    @Input public abstract Property<String> getProfileName();
    @Input public abstract Property<String> getProfileVersion();
    @Input public abstract Property<String> getExecutablePath();
    @Input public abstract Property<String> getMinecraft();
    @Input public abstract Property<String> getMinecraftServerPath();

    @Input public abstract Property<Boolean> getHideExtract();
    @Input public abstract Property<Boolean> getHideClient();
    @Input public abstract Property<Boolean> getHideServer();

    @Input @Optional public abstract MapProperty<String, Data> getData();
    @Input @Optional public abstract ListProperty<Step> getSteps();

    private final Set<Provider<LibraryInfo>> extraLibraries = new LinkedHashSet<>();

    @Inject
    public InstallerJson() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().file("libs/install_profile.json"));

        getLauncherJsonName().convention("/version.json");
        getLogo().convention("/big_logo.png");
        getMirrors().convention("https://files.minecraftforge.net/mirrors-2.0.json");
        getWelcome().convention("Welcome to the " + Util.capitalize(getProject().getName()) + " installer.");
        getMinecraftServerPath().convention("{LIBRARY_DIR}/net/minecraft/server/{MINECRAFT_VERSION}/server-{MINECRAFT_VERSION}-bundled.jar");
        getProfileName().convention(getProject().getName());

        getHideExtract().convention(true);
        getHideClient().convention(false);
        getHideServer().convention(false);
    }

    public void executable(TaskProvider<? extends AbstractArchiveTask> task) {
        var info = MinimalResolvedArtifact.from(this.getProject(), task);
        this.getExecutablePath().set(info.map(i -> i.info().name()));
    }

    public void library(Provider<MinimalResolvedArtifact> info, Action<LibraryInfo> action) {
        this.getInput().from(info.map(MinimalResolvedArtifact::file));
        this.extraLibraries.add(info.map(LibraryInfo::from).map(LibraryInfo.apply(action)));
    }

    private <R extends Step> R step(Class<R> cls, Tool tool, Action<? super R> action) {
        var ret = this.getObjects().newInstance(cls, tool);
        this.getInput().from(tool.getArtifacts().stream().map(MinimalResolvedArtifact::file).toList());
        action.execute(ret);
        return ret;
    }

    public Step step(Tool tool, Action<? super Step> action) {
        return step(Step.class, tool, action);
    }
    public Extract extract(Tool tool, Action<? super Extract> action) {
        return step(Extract.class, tool, action);
    }
    public ExtractBundle extractBundle(Tool tool, Action<? super ExtractBundle> action) {
        return step(ExtractBundle.class, tool, action);
    }

    public record Data(String client, String server) implements Serializable {}

    public void data(String key, String client, String server) {
        this.getData().put(key, new Data(client, server));
    }

    public void data(String key, Provider<?> client, Provider<?> server) {
        this.getData().put(key, client.zip(server, (l, r) -> new Data(convert(l), convert(r))));
    }

    private static String convert(Object obj) {
        if (obj instanceof String           value) return value;
        if (obj instanceof RegularFile      value) return hash(value.getAsFile());
        if (obj instanceof File             value) return hash(value);
        if (obj instanceof SingleFileOutput value) return hash(value.getOutput().getAsFile().get());
        throw new IllegalArgumentException("Cannot convert object of type " + obj.getClass() + " to data entry: " + obj);
    }

    private static String hash(File file) {
        return '\'' + HashFunction.SHA1.sneakyHash(file) + '\'';
    }

    @TaskAction
    protected void exec() throws IOException {
        var libraries = new ArrayList<LibraryInfo>();
        var json = new LinkedHashMap<String, Object>();
        json.put("_comment", Installer.JSON_COMMENT);
        json.put("spec", 1);
        if (getHideExtract().getOrElse(false))
            json.put("hideExtract", true);
        if (getHideClient().getOrElse(false))
            json.put("hideClient", true);
        if (getHideServer().getOrElse(false))
            json.put("hideServer", true);
        json.put("profile", getProfileName().get());
        json.put("version", getProfileVersion().get());
        json.put("path", getExecutablePath().get());
        json.put("minecraft", getMinecraft().get());
        json.put("serverJarPath", getMinecraftServerPath().get());

        if (getData().isPresent() && !getData().get().isEmpty())
            json.put("data", getData().get());

        if (getSteps().isPresent() && !getSteps().get().isEmpty()) {
            var processors = new ArrayList<Map<String, Object>>();
            json.put("processors", processors);
            for (var step : getSteps().get()) {
                var data = new LinkedHashMap<String, Object>();
                if (!step.sides.isEmpty())
                    data.put("sides", step.sides);

                var tool = step.getTool();
                data.put("jar", tool.getJar());
                if (!tool.getArtifacts().isEmpty()) {
                    var classpath = new ArrayList<String>();
                    for (var info : LibraryInfo.from(tool.getArtifacts()).values()) {
                        libraries.add(info);
                        if (!tool.getJar().equals(info.name()))
                            classpath.add(info.name());
                    }
                    data.put("classpath", classpath);
                }
                data.put("args", step.getArgs());
                if (!step.getOutputs().isEmpty())
                    data.put("outputs", step.getOutputs());
                processors.add(data);
            }
        }

        for (var library : this.extraLibraries) {
            var info = library.get();
            libraries.add(info);
        }

        var seen = new HashSet<String>();
        libraries.sort(Comparator.comparing(LibraryInfo::name));
        libraries.removeIf(l -> !seen.add(l.name()));
        json.put("libraries", libraries);

        var icon = getIcon().getOrNull();
        if (icon != null) {
            var data = Files.readAllBytes(icon.getAsFile().toPath());
            var base64 = Base64.getEncoder().encodeToString(data);
            json.put("icon", "data:image/png;base64," + base64);
        }
        json.put("json", getLauncherJsonName().get());
        json.put("logo",  getLogo().get());
        if (!getMirrors().get().isEmpty())
            json.put("mirrorList", getMirrors().get());
        json.put("welcome",  getWelcome().get());

        var output = getOutput().get().getAsFile().toPath();
        var jsonData = GSON.toJson(json);
        Files.writeString(output, jsonData);
    }
}
