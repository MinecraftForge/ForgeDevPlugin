/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.compat;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;

public interface UserdevCompatibility {
    Provider<String> getBaseVersion();

    TaskProvider<CheckJarCompatibility> getCheck();
    TaskProvider<CheckJarCompatibility> check(Action<? super CheckJarCompatibility> action);

    void setClean(RegularFileProperty clean);
    void setClean(Provider<File> clean);

    void setDirty(TaskProvider<?> task);
    void setDirty(Provider<File> dirty);
}
