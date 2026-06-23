/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.base;

import net.minecraftforge.forgedev.ForgeDevPlugin;
import net.minecraftforge.forgedev.values.MCPData;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class MCPBaseImpl implements MCPBase {
    private final Project project;
    private final ForgeDevPlugin plugin;
    private final String name;
    private final MCPData mavenizer;
    private final Configuration depConfig;

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public MCPBaseImpl(Project project, ForgeDevPlugin plugin, String name) {
        this.project = project;
        this.plugin = plugin;
        this.name = name;
        this.depConfig = project.getConfigurations().create(name + "Dependencies", cfg -> {
           cfg.setCanBeResolved(true);
           cfg.setCanBeConsumed(false);
        });

        this.getMcpPipeline().convention("joined");
        this.getMappingChannel().convention("official");
        this.getMappingVersion().convention(this.getMcpVersion().map(MCPBaseImpl::mcpToMinecraft));

        this.getMcpArtifact().convention(this.getMcpVersion().map(s -> {
            if (s.indexOf(':') != -1) // Full artifact
                return s;
            return "de.oceanlabs.mcp:mcp_config:" + s + "@zip";
        }));

        this.mavenizer = this.getObjects().newInstance(MCPData.class, this.project, this.plugin, name);
        this.mavenizer.getMcpVersion().convention(this.getMcpVersion());
        this.mavenizer.getMcpPipeline().convention(this.getMcpPipeline());
        this.mavenizer.getMappingChannelInput().convention(this.getMappingChannel());
        this.mavenizer.getMappingVersionInput().convention(this.getMappingVersion());
        this.mavenizer.getAccessTransformers().convention(this.getAccessTransformers());
        this.mavenizer.getSideAnnotationStrippers().convention(this.getSideAnnotationStrippers());

        var depFactory = project.getDependencies();
        this.depConfig.getDependencies().addAllLater(getDependencies().map(list -> {
            var ret = new ArrayList<Dependency>(list.size());
            for (var desc : list) {
                ret.add(depFactory.create(desc));
            }
            return ret;
        }));
    }

    private static String mcpToMinecraft(String version) {
        return version;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Provider<File> getNamedSources() {
        return this.mavenizer.getSources();
    }

    @Override
    public Provider<File> getUnnamedSources() {
        return this.mavenizer.getSourcesSrg();
    }

    @Override
    public Provider<File> getObf2Srg() {
        return this.mavenizer.getObf2Srg();
    }

    @Override
    public Provider<File> getMap2Srg() {
        return this.mavenizer.getMap2Srg();
    }

    @Override
    public Provider<File> getClasses() {
        return this.mavenizer.getClasses();
    }

    @Override
    public Provider<File> getClassesRaw() {
        return this.mavenizer.getClassesRaw();
    }

    @Override
    public Provider<File> getMappingZip() {
        return this.mavenizer.getMappingZip();
    }

    @Override
    public Provider<List<String>> getDependencies() {
        return this.mavenizer.getDependencies();
    }

    @Override
    public Configuration getDependencyConfiguration() {
        return this.depConfig;
    }

    @Override
    public Provider<File> getExtra() {
        return this.mavenizer.getExtra();
    }
}
