/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.patches;

import net.minecraftforge.forgedev.base.PatcherBase;
import net.minecraftforge.forgedev.tasks.patching.diff.ApplyPatches;
import net.minecraftforge.forgedev.tasks.patching.diff.GeneratePatches;
import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.TaskProvider;

public interface Patches {
    String DEFAULT_NAME = "default";

    PatcherBase getBase();
    void setBase(PatcherBase base);

    String getName();
    TaskProvider<ApplyPatches> getApply();
    default void apply(Action<? super ApplyPatches> action) {
        getApply().configure(action);
    }

    TaskProvider<GeneratePatches> getMake();
    default void make(Action<? super GeneratePatches> action) {
        getMake().configure(action);
    }

    DirectoryProperty getPatches();
    DirectoryProperty getPatched();
}
