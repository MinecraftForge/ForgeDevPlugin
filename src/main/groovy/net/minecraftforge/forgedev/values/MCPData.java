/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.values;

import net.minecraftforge.forgedev.ForgeDevPlugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper around Mavenzier's --mcp task.
 * It provides access to the decompild code, as well as any mappings data as needed.
 */
public abstract class MCPData extends MavenizerData {
    private final String name;

    public abstract Property<@NotNull String> getMcpVersion();
    public abstract Property<@NotNull String> getMcpPipeline();
    public abstract Property<@NotNull String> getMappingChannelInput();
    public abstract Property<@NotNull String> getMappingVersionInput();
    public abstract ConfigurableFileCollection getAccessTransformers();
    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    @Inject
    public MCPData(final Project project, final ForgeDevPlugin plugin, String name) {
        super(project, plugin);
        this.name = name;
    }

    protected String getTaskName() {
        return this.name + "/mcp-data-" + this.getMcpVersion().get() + '-' + this.getMcpPipeline().get();
    }

    @Override
    protected @Nullable File getOutputDir() {
        return this.plugin.localCaches().dir(getTaskName()).get().getAsFile();
    }
    @Override
    public List<String> getArgs() {
        var ret = new ArrayList<>(List.of(
            "--mcp",
            "--pipeline", this.getMcpPipeline().get()
        ));

        // Version or fully qualified artifact
        var version = this.getMcpVersion().get();
        ret.add(version.indexOf(':') != -1 ? "--artifact" : "--version");
        ret.add(version);

        if (this.getMappingChannelInput().isPresent()) {
            ret.add("--mappings");
            if (this.getMappingVersionInput().isPresent())
                ret.add(this.getMappingChannelInput().get() + ':' + this.getMappingVersionInput().get());
            else
                ret.add(this.getMappingChannelInput().get());
        }

        for (var file : this.getAccessTransformers()) {
            ret.add("--at");
            ret.add(file.getAbsolutePath());
        }

        for (var file : this.getSideAnnotationStrippers()) {
            ret.add("--sas");
            ret.add(file.getAbsolutePath());
        }
        return ret;
    }

    public Provider<String> getConfig() {
        return get("config");
    }
    public Provider<String> getPipeline() {
        return get("pipeline");
    }
    public Provider<File> getOutput() {
        return getFile("output");
    }
    public Provider<File> getExtra() {
        return getFile("extra");
    }

    public Provider<String> getMappingChannel() {
        return get("mappings.channel");
    }
    public Provider<String> getMappingVersion() {
        return get("mappings.version");
    }
    public Provider<File> getMappingZip() {
        return getFile("mappings.zip");
    }
    public Provider<File> getObf2Srg() {
        return getFile("mappings.obf2srg");
    }
    public Provider<File> getMap2Obf() {
        return getFile("mappings.map2obf");
    }
    public Provider<File> getMap2Srg() {
        return getFile("mappings.map2srg");
    }

    public Provider<File> getClasses() {
        return this.optional("classes.srg").orElse(get("classes.raw")).map( path ->
            getOutputDir() == null ? project.file(path) : new File(getOutputDir(), path)
        );
    }
    public Provider<File> getClassesSrg() {
        return getFile("classes.srg");
    }
    public Provider<File> getClassesRaw() {
        return getFile("classes.raw");
    }
    public Provider<File> getSources() {
        return getFile("sources.named");
    }
    public Provider<File> getSourcesSrg() {
        return getFile("sources.srg");
    }

    public Provider<List<String>> getDependencies() {
        return get("dependencies").map(value -> value.isBlank() ? List.of() : List.of(value.split(",")));
    }
}
