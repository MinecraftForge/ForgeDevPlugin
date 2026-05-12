/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.patches;

import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.ForgeDevPlugin;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.forgedev.tasks.patching.binary.ApplyBinPatches;
import net.minecraftforge.forgedev.tasks.patching.binary.CreateBinPatches;
import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;

public abstract class BinaryPatchesImpl implements BinaryPatches {
    private final String name;
    private final ForgeDevExtension extension;
    private final TaskProvider<CreateBinPatches> create;
    private final TaskProvider<ApplyBinPatches> apply;

    @Inject
    public BinaryPatchesImpl(String name, ForgeDevExtension extension, TaskContainer tasks) {
        this.name = name;
        this.extension = extension;
        this.create = tasks.register("create" + Util.capitalize(name) + "BinPatches", CreateBinPatches.class);
        this.apply = tasks.register("apply" + Util.capitalize(name)+ "BinPatches", ApplyBinPatches.class);
        this.apply.configure(task -> {
            task.getApply().setFrom(create.flatMap(SingleFileOutput::getOutput));
            task.getData().set(true);
            task.getUnpatched().set(true);
        });
    }

    public TaskProvider<CreateBinPatches> getCreate() {
        return create;
    }
    public TaskProvider<CreateBinPatches> create(Action<? super CreateBinPatches> action) {
        getCreate().configure(action);
        return getCreate();
    }
    public TaskProvider<ApplyBinPatches> getApply() {
        return apply;
    }
    public TaskProvider<ApplyBinPatches> apply(Action<? super ApplyBinPatches> action) {
        getApply().configure(action);
        return getApply();
    }



    @Override
    public void setClean(Provider<File> value) {
        create(task -> task.getClean().setFrom(value));
        apply(task -> task.getClean().setFrom(value));
    }

    @Override
    public void setDirty(Provider<?> value) {
        create(task -> task.getDirty().setFrom(value));
    }

    @Override
    public void setDirty(RegularFileProperty value) {
        create(task -> task.getDirty().setFrom(value));
    }
}
