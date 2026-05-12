/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.shim;

import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.legacy.tasks.DownloadDependency;
import net.minecraftforge.forgedev.legacy.values.MavenInfo;
import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.util.ArrayList;

public abstract class Shim {
    public static final String DEFAULT_NAME = "serverShim";

    private final Project project;
    private final String name;
    private final TaskProvider<ShimClasspath> classpath;
    private final TaskProvider<Jar> jar;
    private final TaskProvider<ShimConfig> config;

    // Implementation details
    private final TaskProvider<DownloadDependency> base;

    @Inject
    public Shim(Project project, String name,
                TaskProvider<ShimClasspath> classpath, TaskProvider<Jar> jar, TaskProvider<DownloadDependency> base,
                TaskProvider<ShimConfig> config) {
        this.project = project;
        this.name = name;
        this.classpath = classpath;
        this.jar = jar;
        this.base = base;
        this.config = config;
    }

    public TaskProvider<ShimClasspath> getClasspath() {
        return this.classpath;
    }
    public void classpath(Action<ShimClasspath> action) {
        getClasspath().configure(action);
    }

    public TaskProvider<Jar> getJar() {
        return this.jar;
    }
    public void jar(Action<Jar> action) {
        getJar().configure(action);
    }

    public TaskProvider<ShimConfig> getConfig() {
        return this.config;
    }
    public void config(Action<ShimConfig> action) {
        getConfig().configure(action);
    }

    public void setBaseVersion(String version) {
        var module = (SharedUtil.SimpleModuleVersionIdentifier)Tools.SHIM.getModule();
        this.setBase(module.withVersion(version).toString());
    }
    public void setBase(String artifact) {
        base.configure(task -> task.setArtifact(artifact) );
    }

    public void bootstrapClasspath(Configuration cfg) {
        jar(task -> {
            var deps = new ArrayList<String>();
            for (var dep : cfg.getIncoming().getArtifacts()) {
                var info = MavenInfo.from(project, dep);
                deps.add("libraries/" + info.path());
            }
            task.getManifest().getAttributes().put("Class-Path", String.join(" ", deps));
        });
    }

    @ApiStatus.Internal
    public static Shim register(Project project, ForgeDevExtension ext, String name) {
        var base = project.getTasks().register(name + "DownloadBase", DownloadDependency.class);

        var classpath = project.getTasks().register(name + "Classpath", ShimClasspath.class);
        classpath.configure(task -> task.setGroup("Generation"));

        var config = project.getTasks().register(name + "Config", ShimConfig.class);

        var jar = project.getTasks().register(name + "Jar", Jar.class);
        jar.configure(task -> {
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            task.getArchiveClassifier().set("shim");

            task.from(config.flatMap(ShimConfig::getOutput), cfg -> cfg.rename(ignore -> ShimConfig.DEFAULT_FILE_NAME));
            task.from(classpath.flatMap(ShimClasspath::getOutput), cfg -> cfg.rename(ignore -> "bootstrap-shim.list"));

            // Add the shim base - as a Provider
            var baseZip = project.getProviders().provider(() -> project.zipTree(base.flatMap(DownloadDependency::getOutput)));
            task.from(baseZip, spec-> {
                spec.exclude("META-INF/MANIFEST.MF");
            });

            // And set the base manifest
            task.dependsOn(base);
            var text = project.getResources().getText();
            var baseManifest = base.flatMap(DownloadDependency::getOutput).map(file -> text.fromArchiveEntry(file, "META-INF/MANIFEST.MF", "utf-8"));
            task.manifest(manifest -> manifest.from(baseManifest));
        });

        var ret = project.getObjects().newInstance(Shim.class, project, name, classpath, jar, base, config);

        ret.setBase(Tools.SHIM.getModule().toString());

        return ret;
    }
}
