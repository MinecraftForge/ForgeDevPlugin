/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.userdev;

import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.base.MCPBase;
import net.minecraftforge.forgedev.base.PatcherBase;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.forgedev.tasks.patching.binary.CreateBinPatches;
import net.minecraftforge.forgedev.tasks.patching.diff.GeneratePatches;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

public abstract class UserDev {
    private final String name;
    private final Project project;
    private final ForgeDevExtension extension;
    private final TaskProvider<Jar> jar;

    private PatcherBase patcherBase;

    private @Nullable TaskProvider<UserdevConfig> config;
    private @Nullable TaskProvider<CreateBinPatches> binaryPatches;
    private @Nullable TaskProvider<GeneratePatches> patches;

    @Inject
    public UserDev(String name, Project project, ForgeDevExtension extension) {
        this.name = name;
        this.project = project;
        this.extension = extension;
        this.jar = project.getTasks().register(this.name + "Jar", Jar.class);
        this.jar.configure(task -> {
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            task.getArchiveClassifier().set("userdev");
        });
        project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, task -> task.dependsOn(jar));
    }

    /*UserDev:
     * config.json
     * joined.lzma
     * sources.jar
     * patches/
     *   net/minecraft/item/Item.java.patch
     * ats/
     *   at1.cfg
     *   at2.cfg
     */
    public TaskProvider<Jar> getJar() {
        return jar;
    }
    public TaskProvider<Jar> jar(Action<? super Jar> action) {
        getJar().configure(action);
        return getJar();
    }
    public TaskProvider<UserdevConfig> getConfig() {
        if (config == null) {
            this.config = this.project.getTasks().register(this.name + "Config", UserdevConfig.class);
            jar(task -> task.from(this.config.flatMap(UserdevConfig::getOutput), e -> e.rename(f -> "config.json")));
        }
        return this.config;
    }
    public TaskProvider<UserdevConfig> config(Action<? super UserdevConfig> action) {
        getConfig().configure(action);
        return getConfig();
    }
    public TaskProvider<CreateBinPatches> getBinaryPatches() {
        if (this.binaryPatches == null) {
            var tool = this.extension.getPlugin().getTool(Tools.BINPATCH);

            getConfig().configure(task -> {
                task.getBinpatcherVersion().set(tool.getModule().toString());
                task.getBinpatcherArguments().addAll("--clean", "{clean}", "--output", "{output}", "--apply", "{patch}");
            });

            var genBinPatches = this.project.getTasks().register(this.name + "BinaryPatches", CreateBinPatches.class);
            if (this.patcherBase != null)
                genBinPatches.configure(this::configureBinaryPatches);

            getJar().configure(task -> {
                task.from(genBinPatches.flatMap(CreateBinPatches::getOutput), e -> e.rename(f -> "joined.lzma"));
            });

            this.binaryPatches = genBinPatches;
        }
        return this.binaryPatches;
    }
    public TaskProvider<CreateBinPatches> binaryPatches(Action<? super CreateBinPatches> action) {
        getBinaryPatches().configure(action);
        return getBinaryPatches();
    }

    public void base(PatcherBase base) {
        getJar().configure(task -> {
           task.from(base.getAccessTransformers(), cfg -> cfg.into("ats/"));
           task.from(base.getSideAnnotationStrippers(), cfg -> cfg.into("sas/"));
        });
        getConfig().configure(task -> {
           task.getATs().from(base.getAccessTransformers());
           task.getSASs().from(base.getSideAnnotationStrippers());
        });
        if (base instanceof MCPBase mcp) {
            getConfig().configure(task -> task.getMCPConfig().set(mcp.getMcpArtifact()));
            if (this.binaryPatches != null)
                this.binaryPatches.configure(this::configureBinaryPatches);
            if (this.patches != null)
                this.patches.configure(this::configurePatches);
        }
        this.patcherBase = base;
    }

    private void configureBinaryPatches(CreateBinPatches task) {
        if (this.patcherBase instanceof MCPBase mcp) {
            task.getClean().setFrom(mcp.getClasses());
            if (Util.isObfuscated(mcp.getMcpVersion().get())) {
                task.getSrg().setFrom(mcp.getMap2Srg());
                task.getReverseSrg().set(true);
            }
            task.getSas().setFrom(mcp.getSideAnnotationStrippers());
            task.getDirty().setFrom(this.project.getTasks().named("jar"));
        }
    }

    public TaskProvider<GeneratePatches> getPatches() {
        if (this.patches == null) {
            var gen = this.patches = project.getTasks().register(this.name + "GeneratePatches", GeneratePatches.class);
            this.patches.configure(task -> {
                task.getAutoHeader().set(false);
                task.getExistingOnly().set(true);
            });
            config(task -> {
                task.getPatchesOriginalPrefix().set(gen.flatMap(GeneratePatches::getBasePathPrefix));
                task.getPatchesModifiedPrefix().set(gen.flatMap(GeneratePatches::getModifiedPathPrefix));
            });
            jar(task -> {
                task.from(project.zipTree(gen.flatMap(SingleFileOutput::getOutput)), e -> e.into("patches/"));
            });
            if (this.patcherBase != null)
                patches.configure(this::configurePatches);
        }
        return this.patches;
    }

    public TaskProvider<GeneratePatches> patches(Action<? super GeneratePatches> action) {
        getPatches().configure(action);
        return getPatches();
    }

    private void configurePatches(GeneratePatches task) {
        if (this.patcherBase instanceof MCPBase mcp)
            task.getInput().setFrom(Util.isObfuscated(mcp.getMcpVersion().get()) ? mcp.getUnnamedSources() : mcp.getNamedSources());
    }

    // TODO: [ForgeDev][UserDev] CLIENT EXTRA?
    /* TODO: [ForgeDev][UserDev] Extra SRG Mappings
    if (!legacyPatcher.getExtraMappings().isEmpty()) {
        for (var extraMapping : legacyPatcher.getExtraMappings()) {
            if (extraMapping instanceof File e) {
                userdevJar.configure(t -> t.from(e, c -> c.into("srgs/")));
                userdevConfig.configure(t -> t.getSRGs().from(e));
            } else if (extraMapping instanceof String e) {
                userdevConfig.configure(t -> t.getSRGLines().add(e));
            }
        }
    }
     */
}
