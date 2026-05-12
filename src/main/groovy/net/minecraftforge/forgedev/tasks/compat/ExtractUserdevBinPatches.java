/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.compat;


import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.util.data.json.JsonData;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipFile;

abstract class ExtractUserdevBinPatches extends DefaultTask implements SingleFileOutput {
    public abstract @InputFile RegularFileProperty getInput();

    public abstract @Override @OutputFile RegularFileProperty getOutput();

    @Inject
    public ExtractUserdevBinPatches() {
    }

    @TaskAction
    protected void exec() {
        var file = getInput().getAsFile().get();
        try (var zip = new ZipFile(file)) {
            var entry = zip.getEntry("config.json");
            if (entry == null)
                throw new RuntimeException("No config.json found in " + file.getAbsolutePath());
            var cfg = JsonData.patcherConfig(zip.getInputStream(entry));

            var patches = zip.getEntry(cfg.binpatches);
            if (patches == null)
                throw new RuntimeException("No binpatches found in " + file.getAbsolutePath());

            Files.copy(zip.getInputStream(patches), getOutput().getAsFile().get().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract binary patches from userdev jar: " + file.getAbsolutePath(), e);
        }
    }
}