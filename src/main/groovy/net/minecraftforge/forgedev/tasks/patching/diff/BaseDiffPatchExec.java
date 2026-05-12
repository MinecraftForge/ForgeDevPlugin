/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.diff;

import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.forgedev.tasks.ToolExec;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;

import javax.inject.Inject;

public abstract class BaseDiffPatchExec extends ToolExec implements SingleFileOutput {
    /* CLI FLAGS - See io.codechicken.diffpatch.cli.DiffPatchCli#mainI, or run --help on the fat jar */

    // Utility
    public abstract @Input @Console Property<Boolean> getVerbose();
    public abstract @Input @Optional @Console Property<String> getLogLevel();
    public abstract @Input @Console Property<Boolean> getSummary();

    public abstract @InputFiles ConfigurableFileCollection getInput();

    public abstract @OutputFile RegularFileProperty getOutput();
    public abstract @Optional @OutputDirectory DirectoryProperty getOutputDirectory();

    public abstract @Input @Optional Property<String> getArchive();
    public abstract @Input @Optional Property<String> getArchiveBase();
    public abstract @Input @Optional Property<String> getBasePathPrefix();
    public abstract @Input @Optional Property<String> getModifiedPathPrefix();
    public abstract @Input @Optional Property<String> getLineEndings();

    @Inject
    protected BaseDiffPatchExec() {
        super(Tools.DIFFPATCH);
        this.getOutput().convention(this.getDefaultOutputFile());
        this.getVerbose().convention(false);
        this.getSummary().convention(false);
    }

    protected void addArguments() {
        //region Utility
        if (this.getVerbose().get())
            this.args("--verbose");
        if (this.getLogLevel().isPresent())
            this.args("--log-level", this.getLogLevel().get());
        if (this.getSummary().get())
            this.args("--summary");
        //endregion

        //region Shared
        this.args("--output", this.getOutput().getAsFile().get());
        if (this.getArchive().isPresent())
            this.args("--archive", this.getArchive().get());
        if (this.getArchiveBase().isPresent())
            this.args("--archive-base", this.getArchiveBase().get());
        if (this.getBasePathPrefix().isPresent())
            this.args("--base-path-prefix", this.getBasePathPrefix().get());
        if (this.getModifiedPathPrefix().isPresent())
            this.args("--modified-path-prefix", this.getModifiedPathPrefix().get());
        if (this.getLineEndings().isPresent()) {
            this.args("--line-endings", this.getLineEndings().map(val -> switch (val) {
                case "\r" -> "CR";
                case "\n" -> "LF";
                case "\r\n" -> "CRLF";
                default -> val;
            }).get());
        }
        //endregion

        super.addArguments();
    }

    /*
    protected File resolve(String name, ConfigurableFileCollection cfg) {
        getLogger().lifecycle("Resolving " + name);
        for (var from : cfg.getFrom()) {
            getLogger().lifecycle("  From: " + from);
        }
        for (var file : cfg.getFiles()) {
            getLogger().lifecycle("  File: " + file);
        }
        var itr = cfg.iterator();
        if (!itr.hasNext())
            throw new IllegalStateException("Can not find Files for " + name + " no values specified");

        var ret = itr.next();
        var absolute = ret.getAbsolutePath();
        if (itr.hasNext()) {
            // If there are extra files then assume we are a directory and try and find the common root.
            if (!ret.isDirectory())
                throw new IllegalStateException("Can not find Files for " + name + " first entry not a directory: " + ret);
            var next = itr.next().getAbsolutePath();
            if (!next.startsWith(absolute))
                throw new IllegalStateException("Could not find shared directory for " + name + " child was not a sub-entry: " + next);
        }
        getLogger().lifecycle("  Resolved: " + ret);
        return ret;
    }
     */
}
