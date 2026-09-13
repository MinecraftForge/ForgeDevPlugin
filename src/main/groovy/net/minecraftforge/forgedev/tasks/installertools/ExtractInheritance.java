/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installertools;

import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.forgedev.tasks.ToolExec;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.IOException;

@CacheableTask
public abstract class ExtractInheritance extends ToolExec implements SingleFileOutput {
    public abstract @InputFile @PathSensitive(PathSensitivity.NONE) RegularFileProperty getInput();

    public abstract @InputFiles @Classpath ConfigurableFileCollection getLibraries();

    public abstract @Override @OutputFile RegularFileProperty getOutput();

    @Inject
    public ExtractInheritance() {
        super(Tools.INSTALLERTOOLS);
        this.getOutput().convention(this.getDefaultOutputFile("json"));
        this.getPreferToolchainJvm().convention(true);
        this.getStandardOutputLogLevel().convention(LogLevel.INFO);
    }

    @Override
    protected ExecResult exec() throws IOException {
        return super.exec().assertNormalExitValue().rethrowFailure();
    }

    @Override
    protected void addArguments() {
        this.args(
            "--task", "extract_inheritance",
            "--input", this.getInput().getAsFile().get().getAbsolutePath(),
            "--output", this.getOutput().getAsFile().get().getAbsolutePath(),
            "--annotations"
        );

        this.args("--lib", this.getLibraries());

        super.addArguments();
    }
}
