/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.diff;

import net.minecraftforge.forgedev.Util;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@DisableCachingByDefault(because = "Don't know how to cache this")
public abstract class GeneratePatches extends BaseDiffPatchExec {
    public abstract @InputFiles @PathSensitive(PathSensitivity.RELATIVE) ConfigurableFileCollection getModified();
    public abstract @OutputFile RegularFileProperty getOutput();
    public abstract @Optional @OutputDirectory DirectoryProperty getOutputDirectory();

    // Diff specific
    public abstract @Input Property<Boolean> getAutoHeader();
    public abstract @Input @Optional Property<Integer> getContext();
    public abstract @Input @Optional Property<String> getArchiveModified();
    // Filter jar for existing files only before generating
    public abstract @Input Property<Boolean> getExistingOnly();

    private final Provider<RegularFile> filteredFile;

    @Inject
    public GeneratePatches() {
        this.getAutoHeader().convention(false);
        this.getExistingOnly().convention(false);
        this.getOutput().convention(this.getDefaultOutputFile());
        this.filteredFile = this.getOutputFile("filtered.jar");
    }

    @Override
    protected void addArguments() {
        super.addArguments();

        //region Diff specific
        if (this.getAutoHeader().get())
            this.args("--auto-header");
        if (this.getContext().isPresent())
            this.args("--context", this.getContext().get());
        if (this.getArchiveModified().isPresent())
            this.args("--archive-modified", this.getArchiveModified().get());
        //endregion

        // https://github.com/TheCBProject/DiffPatch/blob/204d393ee23f5cd4298f771c7b9157ee21eb3b62/src/main/java/io/codechicken/diffpatch/cli/DiffPatchCli.java#L155
        // --diff {base} {modified}
        this.args(
            "--diff",
            this.getInput().getSingleFile(),
            filterModified()
        );
    }

    @Override
    protected @Nullable ExecResult exec() throws IOException {
        var result = super.exec().rethrowFailure();
        var output = getOutput().getAsFile().get();
        if (this.getOutputDirectory().isPresent())
            Util.extractZip(output, this.getOutputDirectory().getAsFile().get(), true);
        return result;
    }

    private File filterModified() {
        var modified = this.getModified().getSingleFile();
        if (!this.getExistingOnly().getOrElse(false))
            return modified;

        var input = this.getInput().getSingleFile();
        var filtered = this.filteredFile.get().getAsFile();
        var known = new HashSet<String>();
        try {
            try (var zin = new ZipFile(input)) {
                for (var itr = zin.entries().asIterator(); itr.hasNext(); ) {
                    var entry = itr.next();
                    known.add(entry.getName());
                }
            }

            if (filtered.getParent() != null)
                Files.createDirectories(filtered.getParentFile().toPath());

            try (
                var zin = new ZipInputStream(new FileInputStream(modified));
                var zout = new ZipOutputStream(new FileOutputStream(filtered));
            ) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    if (!known.contains(entry.getName()))
                        continue;
                    zout.putNextEntry(new ZipEntry(entry.getName()));
                    zin.transferTo(zout);
                    zout.closeEntry();
                }
            }
        } catch (IOException e) {
            return Util.sneak(e);
        }

        return filtered;
    }
}
