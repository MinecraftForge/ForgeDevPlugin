/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.mappings;

import net.minecraftforge.forgedev.ForgeDevTask;
import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.util.file.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

// I have not exposed this in any way, this is basically just going to be used while upgrading old branches to
// the new toolchain so run only once.
@DisableCachingByDefault(because = "Should only be used once per branch")
public abstract class LegacyApplyMappings extends DefaultTask implements ForgeDevTask {
    public abstract @Input Property<Boolean> getJavadocs();
    public abstract @Input Property<Boolean> getLambdas();

    public abstract @InputFile @PathSensitive(PathSensitivity.NONE) RegularFileProperty getInput();
    public abstract @InputFiles @PathSensitive(PathSensitivity.NONE) ConfigurableFileCollection getMappings();
    public abstract @OutputFile RegularFileProperty getOutput();

    @Inject
    public LegacyApplyMappings() {
        this.getWorkerClasspath().from(this.getTool(Tools.FASTCSV));
        this.getOutput().convention(this.getDefaultOutputFile());
        this.getJavadocs().convention(false);
        this.getLambdas().convention(false);
    }

    protected abstract @InputFiles @Classpath ConfigurableFileCollection getWorkerClasspath();
    protected abstract @Inject WorkerExecutor getWorkerExecutor();

    @TaskAction
    protected void exec() {
        final var work = this.getWorkerExecutor().classLoaderIsolation(cfg -> {
            cfg.getClasspath().from(this.getWorkerClasspath());
        });

        work.submit(Action.class, cfg -> {
            cfg.getJavadocs().set(getJavadocs().get());
            cfg.getLambdas().set(getLambdas().get());
            cfg.getInput().set(getInput().get());
            cfg.getMappingsZip().set(getMappings().getSingleFile());
            cfg.getOutput().set(getOutput().get());
        });

        work.await();
    }

    public static abstract class Action implements WorkAction<Action.Parameters> {
        interface Parameters extends WorkParameters {
            Property<Boolean> getJavadocs();
            Property<Boolean> getLambdas();

            RegularFileProperty getInput();
            RegularFileProperty getMappingsZip();
            RegularFileProperty getOutput();
        }

        @Inject
        Action() {}

        @Override
        public void execute() {
            boolean javadocs = this.getParameters().getJavadocs().getOrElse(false);
            boolean lambdas = this.getParameters().getLambdas().getOrElse(false);

            var input = this.getParameters().getInput().get().getAsFile();
            var mappingsZip = this.getParameters().getMappingsZip().get().getAsFile();
            var output = this.getParameters().getOutput().get().getAsFile();

            try (var zin = new ZipFile(input)) {
                var names = MCPNames.load(mappingsZip);
                try (var fos = new FileOutputStream(output);
                     var out = new ZipOutputStream(fos)) {
                    for (var itr = zin.entries().asIterator(); itr.hasNext(); ) {
                        var entry = itr.next();
                        out.putNextEntry(FileUtils.getStableEntry(entry.getName()));
                        if (!entry.getName().endsWith(".java")) {
                            zin.getInputStream(entry).transferTo(out);
                        } else {
                            out.write(names.rename(zin.getInputStream(entry), javadocs, lambdas).getBytes(StandardCharsets.UTF_8));
                        }
                        out.closeEntry();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
