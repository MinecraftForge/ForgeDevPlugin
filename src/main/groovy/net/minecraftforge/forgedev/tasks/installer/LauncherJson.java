/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.legacy.values.LibraryInfo;
import net.minecraftforge.forgedev.legacy.values.MavenInfo;
import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class LauncherJson extends DefaultTask {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    protected abstract @Inject ProviderFactory getProviders();
    protected abstract @Inject ObjectFactory getObjects();

    @OutputFile public abstract RegularFileProperty getOutput();

    @InputFiles public abstract ConfigurableFileCollection getInput();
    @Input public abstract Property<String> getTimestamp();
    @Input public abstract Property<String> getReleaseTime();
    @Input public abstract Property<String> getId();
    @Input @Optional public abstract Property<String> getInheritsFrom();
    @Input public abstract Property<String> getType();
    @Input @Optional public abstract Property<String> getMainClass();
    @Input @Optional public abstract ListProperty<Object> getGameArgs();
    @Input @Optional public abstract ListProperty<Object> getJvmArgs();
    @Input public abstract ListProperty<LibraryInfo> getLibraries();
    @Input public abstract Property<Boolean> getSortLibraries();
    @Input public abstract Property<Boolean> getLibrariesLast();
    @Input public abstract Property<Boolean> getDuplicateLibraries();

    @Inject
    public LauncherJson() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().file("libs/version.json"));

        // TODO: [ForgeDev][Reproduceable] Add helper to get timestamp from latest git commit
        var timestamp = Util.iso8601Now();
        getTimestamp().convention(timestamp);
        getReleaseTime().convention(timestamp);
        getType().convention("release");
        getMainClass().convention("main");
        getSortLibraries().convention(true);
        getLibrariesLast().convention(true);
        getDuplicateLibraries().convention(false);
    }

    @ApiStatus.Internal
    public void libraries(Configuration config) {
        this.getInput().from(config);
        this.getLibraries().addAll(MinimalResolvedArtifact.from(getProject(), config).map(LibraryInfo::toList));
    }
    @ApiStatus.Internal
    public void library(Provider<MinimalResolvedArtifact> info, Action<LibraryInfo> action) {
        this.getInput().from(info.map(MinimalResolvedArtifact::file));
        this.getLibraries().add(info.map(LibraryInfo::from).map(LibraryInfo.apply(action)));
    }

    public void generated(TaskProvider<? extends SingleFileOutput> task, String classifier) {
        generated(task, classifier, t -> {});
    }
    public void generated(TaskProvider<? extends SingleFileOutput> task, String classifier, Action<LibraryInfo> action) {
        library(MinimalResolvedArtifact.from(MavenInfo.from(getProject(), classifier), task.flatMap(SingleFileOutput::getOutput)), info -> {
            action.execute(info);
            info.downloads().artifact().url = "";
        });
    }

    @TaskAction
    protected void exec() throws IOException {
        var json = new LinkedHashMap<String, Object>();
        json.put("_comment", Installer.JSON_COMMENT);
        json.put("id", getId().get());
        json.put("time", getTimestamp().get());
        json.put("releaseTime", getReleaseTime().get());
        if (getInheritsFrom().isPresent())
            json.put("inheritsFrom", getInheritsFrom().get());
        json.put("type", getType().get());
        json.put("logging", Map.of()); // Hardcoded for now, if we ever wanna move to remotely hosted log configs we could
        json.put("mainClass", getMainClass().getOrElse(""));

        if (!getLibrariesLast().get())
            addLibraries(json);

        addArgs(json);

        if (getLibrariesLast().get())
            addLibraries(json);

        var output = getOutput().get().getAsFile().toPath();
        var jsonData = GSON.toJson(json);
        Files.writeString(output, jsonData);
    }

    private void addLibraries(Map<String, Object> json) {
        var libraries = new ArrayList<>(this.getLibraries().get());

        // Older versions didn't de-duplicate, so our 'bootstrap' config got added twice.
        if (!getDuplicateLibraries().get()) {
            var seen = new HashSet<String>();
            libraries.removeIf(l -> !seen.add(l.name()));
        }

        // Classpath order doesn't matter in anything that works in the JPMS.
        // Anything 1.13+ And Gradle's resolution of dependencies is not the same order as their old api.
        // It makes this hard to diff against
        // So lets sort by name and call it good
        if (getSortLibraries().get())
            libraries.sort(Comparator.comparing(LibraryInfo::name));

        json.put("libraries", libraries);
    }

    private void addArgs(Map<String, Object> json) {
        // This is below libraries for legacy diffing, It doesn't matter
        // I could model out this and add support for rules and shit.. but I dont want to
        var args = new LinkedHashMap<String, List<Object>>();
        if (getGameArgs().isPresent())
            args.put("game",  getGameArgs().get());
        if (getJvmArgs().isPresent())
            args.put("jvm",  getJvmArgs().get());
        if (!args.isEmpty())
            json.put("arguments", args);
    }
}
