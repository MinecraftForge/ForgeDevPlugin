/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.values;

import net.minecraftforge.forgedev.Util;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

public abstract class MavenizerValueSource implements ValueSource<Boolean, MavenizerValueSource.Parameters> {
    public interface Parameters extends ValueSourceParameters {
        ConfigurableFileCollection getClasspath();
        RegularFileProperty getJavaLauncher();
        ListProperty<String> getArguments();
    }

    private final ExecOperations execOps;
    private static final Logger LOGGER = Logging.getLogger(MavenizerValueSource.class);

    @Inject
    public MavenizerValueSource(ExecOperations execOps) {
        this.execOps = execOps;
    }

    @Override
    @Nullable
    public Boolean obtain() {
        this.execOps.javaexec(spec -> {
            var params = this.getParameters();
            spec.setClasspath(params.getClasspath());
            spec.setExecutable(params.getJavaLauncher().get());
            spec.setArgs(params.getArguments().get());

            LOGGER.info("Executing Mavenizer: ");
            Util.logFiles(LOGGER, "  Classpath", params.getClasspath().getFiles());
            LOGGER.info("  Java: {}", params.getJavaLauncher().get().getAsFile().getAbsolutePath());
            Util.logArgs(LOGGER, "  Arguments", spec.getArgs());
        }).rethrowFailure().assertNormalExitValue();
        return false;
    }
}
