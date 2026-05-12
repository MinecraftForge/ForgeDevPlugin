/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.base;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public interface MCPBase extends PatcherBase {
    Property<@NotNull String> getMcpArtifact();
    Property<@NotNull String> getMcpVersion();
    Property<@NotNull String> getMcpPipeline();

    Provider<File> getClasses();
    Provider<File> getClassesRaw();

    Configuration getDependencyConfiguration();
    Provider<List<String>> getDependencies();
    Provider<File> getExtra();
}
