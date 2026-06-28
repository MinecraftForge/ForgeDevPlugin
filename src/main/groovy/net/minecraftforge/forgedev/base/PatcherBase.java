/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.base;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface PatcherBase {
    String getName();
    Property<@NotNull String> getMappingChannel();
    Property<@NotNull String> getMappingVersion();

    // Inputs
    ConfigurableFileCollection getAccessTransformers();
    ConfigurableFileCollection getSideAnnotationStrippers();

    // Outputs
    Provider<File> getNamedSources();
    Provider<File> getUnnamedSources();
    Provider<File> getObf2Srg();
    Provider<File> getMap2Srg();
    Provider<File> getMappingZip();
    Provider<File> getMetadata();
}
