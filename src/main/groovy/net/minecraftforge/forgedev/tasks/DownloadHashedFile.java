/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks;

import net.minecraftforge.forgedev.ForgeDevTask;
import net.minecraftforge.util.download.DownloadUtils;
import net.minecraftforge.util.hash.HashFunction;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;

// This is a quick replacement for https://github.com/michel-kraemer/gradle-download-task
// Which we only use for downloading crowdin, could expand to any hashed file, but for now it's mainly just for that
@DisableCachingByDefault(because = "We want to react to the server updateing the file, we do hash checking ourselves")
public abstract class DownloadHashedFile extends DefaultTask implements ForgeDevTask, SingleFileOutput {
    public abstract @Input Property<String> getSrc();
    public abstract @Override @OutputFile RegularFileProperty getOutput();

    @Inject
    public DownloadHashedFile() {
        this.getOutput().convention(this.getSrc().map(src -> {
            var tmp = src.replace('\\', '/');
            var idx = src.lastIndexOf('/');
            var name = idx == -1 ? tmp : src.substring(idx + 1);
            return this.getDefaultOutputFile(name).get();
        }));

        getOutputs().upToDateWhen(task -> false);

        onlyIf(task -> {
            final boolean isOffline = getProject().getGradle().getStartParameter().isOffline();
            if (!isOffline)
                return true;

            var output = getOutput().getAsFile().get();
            if (!output.exists())
                throw new IllegalStateException("Unable to download file '" + output.getName() + "' in offline mode.");

            return false;
        });
    }

    @TaskAction
    public void exec() throws IOException {
        var output = getOutput().getAsFile().get();
        if (output.exists()) {
            var existing_hash = HashFunction.SHA1.hash(output);
            var remote_hash = DownloadUtils.downloadString(getSrc().get() + ".sha1");
            if (remote_hash.equals(existing_hash)) {
                getState().setDidWork(false);
                return;
            }
        }

        DownloadUtils.downloadFile(output, getSrc().get());
    }
}
