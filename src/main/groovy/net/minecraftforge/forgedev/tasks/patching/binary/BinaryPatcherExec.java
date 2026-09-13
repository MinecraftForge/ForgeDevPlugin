/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.binary;

import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.forgedev.tasks.ToolExec;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;

@CacheableTask
abstract class BinaryPatcherExec extends ToolExec implements SingleFileOutput {
    // Shared
    public abstract @InputFiles @PathSensitive(PathSensitivity.NONE) ConfigurableFileCollection getClean();
    public abstract @Input @Optional ListProperty<String> getPrefix();
    public abstract @Input Property<Boolean> getPack200();
    public abstract @Deprecated @Input Property<Boolean> getLegacy();

    public abstract @Override @OutputFile RegularFileProperty getOutput();

    @Inject
    public BinaryPatcherExec() {
        super(Tools.BINPATCH);

        this.getOutput().convention(this.getDefaultOutputFile());
        this.getPack200().convention(false);
        this.getLegacy().convention(false);

        this.getStandardOutputLogLevel().set(LogLevel.INFO);
    }

    @Override
    protected void addArguments() {
        if (!this.getClean().isEmpty()) {
            this.getClean().forEach( file ->
                this.args("--clean", file.getAbsolutePath())
            );
        } else {
            throw new IllegalArgumentException("no clean!");
        }

        this.args("--output", this.getOutput().getAsFile().get().getAbsolutePath());

        if (this.getPrefix().isPresent()) {
            this.getPrefix().get().forEach( prefix ->
                this.args("--prefix", prefix)
            );
        }

        if (this.getPack200().getOrElse(false))
            this.args("--pack200");

        if (this.getLegacy().getOrElse(false))
            this.args("--legacy");

        super.addArguments();
    }
}
