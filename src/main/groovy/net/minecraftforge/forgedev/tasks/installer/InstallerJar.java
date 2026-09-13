/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;

@DisableCachingByDefault(because = "Not worth caching")
public abstract class InstallerJar extends Jar {
    @Inject
    public InstallerJar() {
        // We have to `set` here because the default plugin forces the conventions after the task is created
        // But since configuration is done in order, callers can override in their actions
        this.getArchiveClassifier().set("installer");
    }

    @ApiStatus.Internal
    public void pack(Provider<MinimalResolvedArtifact> info) {
        this.from(info.map(MinimalResolvedArtifact::file), spec -> {
            spec.rename( name -> {
                var path = info.get().info().path();
                getLogger().lifecycle("Adding: " + path);
                return "maven/" + path;
            });
            spec.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        });
    }
}
