/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.patches;

import net.minecraftforge.forgedev.tasks.patching.binary.ApplyBinPatches;
import net.minecraftforge.forgedev.tasks.patching.binary.CreateBinPatches;
import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

public interface BinaryPatches {
    void setClean(Provider<File> value);
    void setDirty(Provider<?> value);
    void setDirty(RegularFileProperty value);


    TaskProvider<CreateBinPatches> getCreate();
    TaskProvider<CreateBinPatches> create(Action<? super CreateBinPatches> action);
    TaskProvider<ApplyBinPatches> getApply();
    TaskProvider<ApplyBinPatches> apply(Action<? super ApplyBinPatches> action);
}
