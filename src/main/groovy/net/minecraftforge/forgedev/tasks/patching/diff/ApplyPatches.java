/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.diff;

import net.minecraftforge.forgedev.Util;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@DisableCachingByDefault(because = "Part of the setup task for each workspace, don't think it's cacheable")
public abstract class ApplyPatches extends BaseDiffPatchExec {
    public abstract @InputFiles @PathSensitive(PathSensitivity.RELATIVE) ConfigurableFileCollection getPatches();
    public abstract @OutputFiles @Optional ConfigurableFileCollection getRejects();

    // Patch specific
    public abstract @Input Property<Boolean> getFailOnError();
    public abstract @Input @Optional Property<String> getArchiveRejects();
    public abstract @Input @Optional Property<Float> getFuzz();
    public abstract @Input @Optional Property<Integer> getOffset();
    public abstract @Input @Optional Property<String> getMode();
    public abstract @Input @Optional Property<String> getArchivePatches();

    // Patch shared
    public abstract @Input @Optional Property<String> getPrefix();

    @Inject
    public ApplyPatches() {
        this.getStandardOutputLogLevel().convention(LogLevel.WARN);
        this.getFailOnError().convention(true);
        this.getLogLevel().convention("warn");
        this.getMode().convention("access");
    }

    @Override
    protected void addArguments() {
        super.addArguments();

        //region Patch specific
        if (!this.getRejects().isEmpty())
            this.args("--reject", getRejects().getSingleFile());
        if (this.getArchiveRejects().isPresent())
            this.args("--archive-rejects", this.getArchiveRejects().get());
        if (this.getFuzz().isPresent())
            this.args("--fuzz", this.getFuzz().get());
        if (this.getOffset().isPresent())
            this.args("--offset", this.getOffset().get());
        if (this.getMode().isPresent())
            this.args("--mode", this.getMode().get());
        if (this.getArchivePatches().isPresent())
            this.args("--archive-patches", this.getArchivePatches().get());
        //endregion

        //region Patch shared
        if (this.getPrefix().isPresent())
            this.args("--prefix", this.getPrefix().get());
        //endregion

        //region Patch Task
        // https://github.com/TheCBProject/DiffPatch/blob/204d393ee23f5cd4298f771c7b9157ee21eb3b62/src/main/java/io/codechicken/diffpatch/cli/DiffPatchCli.java#L191
        // --patch {base} {patches}
        this.args(
            "--patch",
            getInput().getSingleFile(),
            getPatches().getSingleFile()
        );
        //endregion
    }

    @Override
    protected @Nullable ExecResult exec() throws IOException {
        ExecResult result = null;
        var output = getOutput().getAsFile().get();

        // No patches, not sure when this would ever come up, but isn't hard to support
        if (this.getPatches().isEmpty()) {
            var input =  getInput().getSingleFile();
            if (output.getParent() != null)
                Files.createDirectories(output.getParentFile().toPath());
            Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            result = super.exec();

            var exitValue = result.getExitValue();
            if (exitValue != 0) {
                // patches failed
                if (exitValue != 1)
                    result.rethrowFailure();

                // some other error
                if (this.getFailOnError().get())
                    result.assertNormalExitValue();
            }
        }
        if (this.getOutputDirectory().isPresent())
            Util.extractZip(output, this.getOutputDirectory().getAsFile().get(), true);
        return result;
    }
}
