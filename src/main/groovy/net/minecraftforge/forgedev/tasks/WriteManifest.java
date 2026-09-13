/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks;

import net.minecraftforge.forgedev.Util;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.ManifestInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;

@CacheableTask // Should just be writing a byte[] to disk, so as long as the input data i the same we can use the cached file
public abstract class WriteManifest extends DefaultTask implements SingleFileOutput {
    public static TaskProvider<WriteManifest> register(Project project, TaskProvider<? extends Jar> jar) {
        var write = project.getTasks().register("writeManifest", WriteManifest.class);
        write.configure(task -> {
            // We don't use a map/privider here because we explicitly are reading just the config of the task.
            task.getInputBytes().set(getManifestBytes(jar.get().getManifest()));
        });
        return write;
    }

    private static byte[] getManifestBytes(Manifest manifest) {
        try (var os = new ByteArrayOutputStream()) {
            // RATIONALE: ManifestInternal has not changed since Gradle 2.14
            // Due to the hacky nature of needing the proper manifest in the resources, this is the only good way of doing this
            // The DefaultManifest object cannot be serialized into the Gradle cache, and the normal Manifest interface does not have this method
            // This should be the only Gradle internals we need to use in all of ForgeDev, thankfully
            ((ManifestInternal)manifest).writeTo(os);
            return os.toByteArray();
        } catch (IOException e) {
            return Util.sneak(e);
        }
    }

    protected abstract @Input Property<byte[]> getInputBytes();
    // We can't use an output file because it causes task dependency hell.
    // This **should** eventually die in the launcher re-write when we stop using the manifest for runtime version info.
    public abstract @Override @OutputFile RegularFileProperty getOutput();

    @Inject
    public WriteManifest() {
        // The output name is ALWAYS "MANIFEST.MF", and output cannot be changed
        this.getOutput().value(this.getProject().getLayout().getProjectDirectory().file("src/main/resources/META-INF/MANIFEST.MF")).disallowChanges();
    }

    @TaskAction
    void exec() throws IOException{
        Files.write(this.getOutput().get().getAsFile().toPath(), getInputBytes().get());
    }
}
