/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks;

import net.minecraftforge.forgedev.ForgeDevProblems;
import net.minecraftforge.forgedev.ForgeDevTask;
import net.minecraftforge.gradleutils.shared.Tool;
import net.minecraftforge.gradleutils.shared.ToolExecBase;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

@DisableCachingByDefault(because = "Executable caches probably aren't cacheable")
public abstract class ToolExec extends ToolExecBase<ForgeDevProblems> implements ForgeDevTask {
    @Inject
    public ToolExec(Tool tool) {
        super(tool);
    }
}
