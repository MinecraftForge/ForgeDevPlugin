/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.values;

import net.minecraftforge.forgedev.ForgeDevPlugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

/**
 * A wrapper around Mavenzier's --minecraft-files task.
 * This exposes a lot of vanilla files with no modifications.
 * Basically, I had the tasks written in Mavenizer and don't want to duplicate them here.
 */
public abstract class MinecraftFiles extends MavenizerData {
    private final String version;

    @Inject
    public MinecraftFiles(final Project project, final ForgeDevPlugin plugin, String version) {
        super(project, plugin);
        this.version = version;
    }

    @Override
    protected String getTaskName() {
        return "minecraft-" + version;
    }

    @Override
    public List<String> getArgs() {
        return List.of(
            "--minecraft-files",
            "--version", this.version
        );
    }

    public Provider<String> getId() {
        return get("id");
    }
    public Provider<File> getVersion() {
        return getFile("version");
    }
    public Provider<File> getClient() {
        return getFile("client");
    }
    public Provider<File> getServer() {
        return getFile("server");
    }
    public Provider<List<File>> getClientLibraries() {
        return getFile("client.libraries").map(this::loadList);
    }
    public Provider<List<File>> getServerLibraries() {
        return getFile("server.libraries").map(this::loadList);
    }
    public Provider<File> getServerExtracted() {
        return getFile("server.extracted");
    }
    // This file is optional, only available for 1.14->1.21.11, will throw an exception if requested outside that
    public Provider<File> getClientMappings() {
        return getFile("client.mappings");
    }
    // This file is optional, only available for 1.14->1.21.11, will throw an exception if requested outside that
    public Provider<File> getServerMappings() {
        return getFile("server.mappings");
    }
}
