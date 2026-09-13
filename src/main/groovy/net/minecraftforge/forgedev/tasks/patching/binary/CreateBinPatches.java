/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.binary;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;

@CacheableTask
public abstract class CreateBinPatches extends BinaryPatcherExec {
    // Create
    public abstract @InputFiles @PathSensitive(PathSensitivity.NONE) ConfigurableFileCollection getDirty();
    public abstract @InputFiles @PathSensitive(PathSensitivity.RELATIVE) @Optional ConfigurableFileCollection getPatches();
    public abstract @InputFiles @PathSensitive(PathSensitivity.NONE) @Optional ConfigurableFileCollection getSrg();
    public abstract @InputFiles @PathSensitive(PathSensitivity.NONE) @Optional ConfigurableFileCollection getSas();
    public abstract @Input Property<Boolean> getReverseSrg();

    @Inject
    public CreateBinPatches() {
        getReverseSrg().convention(false);
        getOutput().convention(this.getDefaultOutputFile("lzma"));
    }

    @Override
    protected void addArguments() {
        super.addArguments();

        if (!this.getDirty().isEmpty()) {
            this.getDirty().forEach(file ->
                this.args("--create", file.getAbsolutePath())
            );
        } else {
            throw new IllegalArgumentException("No create!");
        }

        this.getPatches().forEach(file ->
            this.args("--patches", file.getAbsolutePath())
        );

        if (this.getReverseSrg().getOrElse(false)) {
            this.args("--reverse-srg");
        }

        this.getSrg().forEach(file ->
            this.args("--srg", file.getAbsolutePath())
        );

        this.getSas().forEach(file ->
            this.args("--sas", file.getAbsolutePath())
        );
    }
}
