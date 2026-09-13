/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy.tasks;

import net.minecraftforge.forgedev.ForgeDevTask;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@CacheableTask
public abstract class DownloadDependency extends DefaultTask implements SingleFileOutput, ForgeDevTask {
    public abstract @InputFiles @PathSensitive(PathSensitivity.NONE) ConfigurableFileCollection getInput();
    public abstract @Override @OutputFile RegularFileProperty getOutput();

    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public DownloadDependency() {
        this.getOutput().convention(this.getDefaultOutputFile());
    }

    public void setArtifact(Object artifact) {
        // Eagerly resolve configurations during configuration time, this is not great, but gradle...
        var unpacked = Util.unpack(artifact);
        unpacked = unpacked instanceof Dependency dep && !(unpacked instanceof MinimalExternalModuleDependency) ? dep.copy() : unpacked;
        var dep = getProject().getDependencies().create(unpacked);
        var cfg = getProject().getConfigurations().detachedConfiguration(dep);
        cfg.setTransitive(false);
        this.getInput().setFrom(cfg);
    }

    // We have to actually copy to a different output file because gradle task caching can't handle the output being equal to the input.
    // It makes the task always up to date
    @TaskAction
    public void exec() {
        try {
            var file = getInput().getSingleFile();
            var output = getOutput().getAsFile().get();
            if (output.getParentFile() != null && output.getParentFile().exists())
                output.getParentFile().mkdirs();
            try {
                Files.copy(file.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("Could not copy downloaded file from " + file + " to " + output);
            }
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Downloaded dependency variant is not a single file", e);
        }
    }

}
