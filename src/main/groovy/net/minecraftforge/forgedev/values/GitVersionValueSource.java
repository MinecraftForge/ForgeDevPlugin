/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.values;

import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public abstract class GitVersionValueSource implements ValueSource<String, GitVersionValueSource.Parameters> {
    public static Provider<String> provider(ProviderFactory providers, EnhancedPlugin<?> plugin, Action<Parameters> config) {
        return providers.of(GitVersionValueSource.class, spec -> {
            spec.parameters(params -> {
                var tool = plugin.getTool(Tools.GITVERSION);
                params.getClasspath().setFrom(tool.getClasspath());
                params.getJavaLauncher().set(tool.getJavaLauncher().map(JavaLauncher::getExecutablePath));
                params.getRootDirectory().set(plugin.rootProjectDirectory());
                params.getProjectDirectory().set(plugin.workingProjectDirectory());
                params.getCommit().set("HEAD");
                config.execute(params);
            });
        });
    }

    public interface Parameters extends ValueSourceParameters {
        ConfigurableFileCollection getClasspath();
        RegularFileProperty getJavaLauncher();
        DirectoryProperty getRootDirectory();
        DirectoryProperty getProjectDirectory();
        Property<String> getCommit();
    }
    private final ExecOperations execOps;
    private static final Logger LOGGER = Logging.getLogger(GitVersionValueSource.class);

    @Inject
    public GitVersionValueSource(ExecOperations execOps) {
        this.execOps = execOps;
    }

    @Override
    @Nullable
    public String obtain() {
        var output = new ByteArrayOutputStream();
        this.execOps.javaexec(spec -> {
            var params = this.getParameters();
            spec.setClasspath(params.getClasspath());
            spec.setExecutable(params.getJavaLauncher().get());
            spec.args("--root-dir",  params.getRootDirectory().get().getAsFile().getAbsolutePath());
            spec.args("--project-dir",  params.getProjectDirectory().get().getAsFile().getAbsolutePath());
            spec.args("--commit", params.getCommit().get());

            LOGGER.info("Executing GitVersion: ");
            Util.logFiles(LOGGER, "  Classpath", params.getClasspath().getFiles());
            LOGGER.info("  Java: {}", params.getJavaLauncher().get().getAsFile().getAbsolutePath());
            Util.logArgs(LOGGER, "  Arguments", spec.getArgs());
            spec.setStandardOutput(output);
        }).rethrowFailure().assertNormalExitValue(); // If something fails, this should throw an exception

        var lines = output.toString(StandardCharsets.UTF_8).split(System.lineSeparator());
        // We don't want the new line characters, just the first line.
        return lines[0];
    }
}
