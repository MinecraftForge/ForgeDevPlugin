/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.patches;

import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.base.PatcherBase;
import net.minecraftforge.forgedev.tasks.patching.diff.ApplyPatches;
import net.minecraftforge.forgedev.tasks.patching.diff.GeneratePatches;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

public abstract class PatchesImpl implements Patches {
    private final String name;
    private final TaskProvider<ApplyPatches> apply;
    private final TaskProvider<GeneratePatches> make;

    private PatcherBase base;

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public PatchesImpl(ForgeDevExtension extension, String name, Project project) {
        this.name = name;
        var suffix = DEFAULT_NAME.equals(name) ? "" : Util.capitalize(name);
        this.apply = project.getTasks().register("applyPatches" + suffix, ApplyPatches.class);
        this.make = project.getTasks().register("makePatches" + suffix, GeneratePatches.class);
        var buildDir = project.getLayout().getProjectDirectory();
        var updating = extension.getProblems().test("net.minecraftforge.forge.build.updating");

        apply.configure(task -> {
            task.getInput().setFrom(getBase().getNamedSources());
            task.getPatches().setFrom(getPatches());
            task.getOutputDirectory().set(getPatched());
            task.getFailOnError().set(false);

            if (updating) {
                task.getMode().set("fuzzy");
                task.getRejects().setFrom(buildDir.dir("rejects"));
                task.getArchiveRejects().unsetConvention();
                task.getFailOnError().set(false);
            }
        });

        make.configure(task -> {
            task.setOnlyIf(t -> getPatches().isPresent());
            task.getInput().setFrom(getBase().getNamedSources());
            task.getModified().setFrom(getPatched());
            task.getAutoHeader().set(true);
            task.getLineEndings().convention("\n");
            task.getOutputDirectory().set(getPatches());
        });

        // Is this needed? AfterEvaluate should be avoided
        project.afterEvaluate(p -> {
            // Automatically create the patches folder if it does not exist
            try {
                Files.createDirectories(getPatches().get().getAsFile().toPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create patches folder", e);
            }
        });
    }

    @Override
    public PatcherBase getBase() {
        return base;
    }
    @Override
    public void setBase(PatcherBase base) {
        this.base = base;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public TaskProvider<ApplyPatches> getApply() {
        return apply;
    }
    @Override
    public TaskProvider<GeneratePatches> getMake() {
        return make;
    }
}
