/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import net.minecraftforge.forgedev.legacy.tasks.DownloadDependency;
import net.minecraftforge.forgedev.legacy.values.LibraryInfo;
import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import net.minecraftforge.util.download.DownloadUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;

@ApiStatus.Internal
public abstract class InstallerJarConfig extends DefaultTask {
    private final Provider<Installer> installer;
    private final TaskProvider<DownloadDependency> base;

    @InputFiles public abstract ConfigurableFileCollection getInput();
    @Input public abstract Property<Boolean> getDev();
    @Input public abstract Property<Boolean> getOffline();
    @Input public abstract ListProperty<MinimalResolvedArtifact> getLibraries();
    private final Map<Provider<String>, Action<LibraryInfo>> actions = new IdentityHashMap<>();

    @Inject protected abstract ArchiveOperations getArchiveOperations();

    @Inject
    public InstallerJarConfig(Provider<Installer> installer, TaskProvider<DownloadDependency> base) {
        this.installer = installer;
        this.base = base;
        this.getDev().convention(installer.flatMap(Installer::getDev));
        this.getOffline().convention(installer.flatMap(Installer::getOffline));
    }

    @TaskAction
    protected void exec() {
        // Add the base here, so that the buildscript configuration runs first
        installer.get().jar( task -> {
            task.from(getArchiveOperations().zipTree(base.map(DownloadDependency::getOutput)), cfg -> {
                cfg.exclude("META-INF/MANIFEST.MF");
                cfg.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            });
        });

        // If we are not making an offline installer, and we're on the CI don't check remote, assume we're gunna publish everything
        if (!getOffline().get() && !getDev().get()) {
            //getLogger().lifecycle("Skipping: " + artifact.path);
            return;
        }

        var actions = new HashMap<String, Action<LibraryInfo>>();
        for (var entry : this.actions.entrySet()) {
            actions.put(entry.getKey().get(), entry.getValue());
        }

        var seen = new HashSet<String>();
        // TODO: [ForgeDev][Optimization] Thread this, as it takes 30s/build because of all the network requests
        for (var artifact : getLibraries().get()) {
            var info = LibraryInfo.from(artifact);
            var action = actions.get(artifact.info().name());
            if (action != null)
                action.execute(info);

            // Check for duplicates without pinging remote servers
            if (!seen.add(info.downloads().artifact().path))
                continue;

            pack(artifact, info);
        }
    }

    @ApiStatus.Internal
    public void libraries(Configuration config) {
        this.getInput().from(config);
        this.getLibraries().addAll(MinimalResolvedArtifact.from(getProject(), config));
    }

    @ApiStatus.Internal
    public void library(Provider<MinimalResolvedArtifact> info, Action<LibraryInfo> action) {
        this.getInput().from(info.map(MinimalResolvedArtifact::file));
        this.getLibraries().add(info);
        actions.put(info.map(artifact -> artifact.info().name()), action);
    }

    private void pack(MinimalResolvedArtifact resolved, LibraryInfo info) {
        var artifact = info.downloads().artifact();

        // If it's an offline jar, or generated artifact always pack
        var pack = getOffline().get() || artifact.url.isEmpty();

        // If it's not, Check if the remote
        if (!pack) {
            // See if the remote hash is the same as ours
            var remote = DownloadUtils.tryDownloadString(artifact.url + ".sha1");
            if (remote == null) {
                // The file doesn't exist, Mojang's maven doesn't include them, so assume it exists if it's on there.
                pack = !artifact.url.startsWith("https://libraries.minecraft.net/");
            } else {
                pack = !artifact.sha1.equals(remote);
            }
        }

        if (!pack) {
            //getLogger().lifecycle("Skipping: " + artifact.path);
            return;
        }

        this.installer.get().getJar().configure(task -> {
            task.from(resolved.file(), spec -> {
                spec.rename(name -> {
                    getLogger().lifecycle("Adding: " + artifact.path);
                    return "maven/" + artifact.path;
                });
                spec.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            });
        });
    }
}
