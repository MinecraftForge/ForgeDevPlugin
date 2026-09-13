/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.compat;

import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.tasks.ToolExec;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;

@DisableCachingByDefault(because = "There is no output")
public abstract class CheckJarCompatibility extends ToolExec {
    abstract @InputFile @PathSensitive(PathSensitivity.NONE) RegularFileProperty getBaseJar();
    abstract @InputFile @PathSensitive(PathSensitivity.NONE) RegularFileProperty getInputJar();

    abstract @InputFiles @PathSensitive(PathSensitivity.NONE) ConfigurableFileCollection getCommonLibraries();
    abstract @InputFiles @PathSensitive(PathSensitivity.NONE) ConfigurableFileCollection getBaseLibraries();
    abstract @InputFiles @PathSensitive(PathSensitivity.NONE) ConfigurableFileCollection getConcreteLibraries();

    abstract @Input @Optional Property<Boolean> getBinary();
    abstract @Input @Optional Property<String> getAnnotationCheckMode();

    @Inject
    public CheckJarCompatibility() {
        super(Tools.JARCOMPATIBILITYCHECKER);
    }

    @Override
    protected ExecResult exec() throws IOException {
        return super.exec().rethrowFailure().assertNormalExitValue();
    }

    @Override
    protected void addArguments() {
        super.addArguments();

        this.args(
            "--base-jar", this.getBaseJar(),
            "--input-jar", this.getInputJar()
        );

        this.args("--lib", this.getCommonLibraries());
        this.args("--base-lib", this.getBaseLibraries());
        this.args("--concrete-lib", this.getConcreteLibraries());

        this.args(this.getBinary().getOrElse(false) ? "--binary" : "--api");
        this.args("--annotation-check-mode", this.getAnnotationCheckMode());
    }
}
