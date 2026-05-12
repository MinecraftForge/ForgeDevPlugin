/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.binary;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;

public abstract class ApplyBinPatches extends BinaryPatcherExec {
    // Create
    public abstract @InputFiles ConfigurableFileCollection getApply();
    public abstract @Input Property<Boolean> getData();
    public abstract @Input Property<Boolean> getUnpatched();
    public abstract @Input Property<Boolean> getStore();
    public abstract @Input @Optional Property<String> getMarker();

    @Inject
    public ApplyBinPatches() {
        this.getData().convention(false);
        this.getUnpatched().convention(false);
        this.getStore().convention(false);
    }

    @Override
    protected void addArguments() {
        super.addArguments();

        if (!this.getApply().isEmpty()) {
            this.getApply().forEach(file ->
                this.args("--apply", file.getAbsolutePath())
            );
        } else {
            throw new IllegalArgumentException("No apply!");
        }

        if (this.getData().getOrElse(false))
            this.args("--data");

        if (this.getUnpatched().getOrElse(false))
            this.args("--unpatched");

        if (this.getStore().getOrElse(false))
            this.args("--store");

        if (this.getMarker().isPresent())
            this.args("--marker", this.getMarker().get());
    }
}
