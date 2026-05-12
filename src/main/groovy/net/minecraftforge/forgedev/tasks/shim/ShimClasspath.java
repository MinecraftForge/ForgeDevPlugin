/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.shim;

import net.minecraftforge.forgedev.ForgeDevTask;
import net.minecraftforge.forgedev.legacy.values.MavenInfo;
import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.util.hash.HashFunction;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.TreeMap;
import java.util.zip.ZipFile;

public abstract class ShimClasspath extends DefaultTask implements ForgeDevTask, SingleFileOutput {
    @InputFiles public abstract ConfigurableFileCollection getInput();
    @Input public abstract ListProperty<MinimalResolvedArtifact> getLibraries();
    @InputFile @Optional public abstract RegularFileProperty getServerBundle();
    @Override @OutputFile public abstract RegularFileProperty getOutput();

    public ShimClasspath() {
        getOutput().convention(this.getDefaultOutputFile("list"));
    }

    public void libraries(Configuration config) {
        this.getInput().from(config);
        this.getLibraries().addAll(MinimalResolvedArtifact.from(getProject(), config));
    }
    public void library(TaskProvider<? extends AbstractArchiveTask> task) {
        library(MinimalResolvedArtifact.from(getProject(), task));
    }
    public void library(TaskProvider<? extends SingleFileOutput> task, String classifier) {
        library(MinimalResolvedArtifact.from(MavenInfo.from(getProject(), classifier), task.flatMap(SingleFileOutput::getOutput)));
    }
    public void library(Provider<MinimalResolvedArtifact> info) {
        this.getInput().from(info.map(MinimalResolvedArtifact::file));
        this.getLibraries().add(info);
    }

    @TaskAction
    public void run() throws IOException {
        var entries = new TreeMap<String, String>();
        for (var lib : getLibraries().get()) {
            var info = lib.info().art();
            var key = info.group() + ':' + info.name();
            if (info.classifier() != null)
                key += ':' + info.classifier();
            entries.put(key, HashFunction.SHA256.hash(lib.file()) + '\t' + lib.info().name() + '\t' + lib.info().path());
        }

        // Copy from vanilla if we don't overwrite the version
        var bundle = this.getServerBundle().map(RegularFile::getAsFile).getOrNull();
        if (bundle != null) {
            try (var zip = new ZipFile(bundle)) {
                var entry = zip.getEntry("META-INF/libraries.list");
                if (entry == null)
                    throw new IllegalStateException("Server Jar does not have bundle list: " + bundle.getAbsolutePath());

                try (var buf = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)))) {
                    for (String line; (line = buf.readLine()) != null; ) {
                        var tabs = line.split("\t");
                        var info = MavenInfo.from(tabs[1]).art();
                        var key = info.group() + ':' + info.name();
                        if (info.classifier() != null)
                            key += ':' + info.classifier();
                        entries.putIfAbsent(key, line);
                    }
                }
            }
        }

        var output = getOutput().getAsFile().get();
        Files.writeString(output.toPath(), String.join("\n", entries.values()), StandardCharsets.UTF_8);
    }
}
