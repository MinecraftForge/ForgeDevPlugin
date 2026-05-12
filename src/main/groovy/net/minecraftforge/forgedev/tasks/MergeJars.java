/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks;

import net.minecraftforge.forgedev.ForgeDevTask;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// This **could** be a standard Copy task, but Gradle still extracts ZipTrees to temporary directories, instead of dealing with them in memory.
// So to save hard drives we do it this way
public abstract class MergeJars extends DefaultTask implements ForgeDevTask, SingleFileOutput {
    public abstract @InputFiles ConfigurableFileCollection getInputJars();
    public abstract @Override @OutputFile RegularFileProperty getOutput();

    @Inject
    public MergeJars() {
        this.getOutput().convention(this.getDefaultOutputFile());
    }

    @TaskAction
    public void run() throws IOException {
        var output = getOutput().get().getAsFile();
        if (output.getParentFile() != null)
            Files.createDirectories(output.getParentFile().toPath());

        try (var zout = new ZipOutputStream(new FileOutputStream(output))) {
            for (var jar : getInputJars().getFiles()) {
                try (var zin = new ZipInputStream(new FileInputStream(jar))) {
                    for (var entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                        ZipEntry _new = new ZipEntry(entry.getName());
                        _new.setTime(0); //SHOULD be the same time as the main entry, but NOOOO _new.setTime(entry.getTime()) throws DateTimeException, so you get 0, screw you!
                        zout.putNextEntry(_new);
                        zin.transferTo(zout);
                    }
                }
            }
        }
    }
}
