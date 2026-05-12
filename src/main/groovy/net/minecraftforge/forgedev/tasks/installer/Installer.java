/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.legacy.tasks.DownloadDependency;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.legacy.values.LibraryInfo;
import net.minecraftforge.forgedev.legacy.values.MavenInfo;
import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.forgedev.values.MavenArtifact;
import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class Installer {
    static final List<String> JSON_COMMENT = List.of(
        "Please do not automate the download and installation of Forge.",
        "Our efforts are supported by ads from the download page.",
        "If you MUST automate this, please consider supporting the project through https://www.patreon.com/LexManos/"
    );

    private final Project project;
    private final String name;
    // Public facing tasks
    private final TaskProvider<InstallerJar> jar;
    private final TaskProvider<InstallerJson> json;
    private final TaskProvider<LauncherJson> launcherJson;

    // Implementation details
    private final TaskProvider<InstallerJarConfig> jarConfig;
    private final TaskProvider<DownloadDependency> base;

    @Input
    public abstract Property<Boolean> getDev();
    @Input
    public abstract Property<Boolean> getOffline();

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public Installer(Project project, String name, TaskProvider<InstallerJar> jar, TaskProvider<InstallerJarConfig> jarConfig, TaskProvider<InstallerJson> json, TaskProvider<LauncherJson> launcherJson, TaskProvider<DownloadDependency> base) {
        this.project = project;
        this.name = name;
        this.jar = jar;
        this.jarConfig = jarConfig;
        this.json = json;
        this.launcherJson = launcherJson;
        this.base = base;
        this.getDev().convention(false);
        this.getOffline().convention(false);
    }
    public String getName() { return this.name; }

    public TaskProvider<InstallerJar> getJar() {
        return this.jar;
    }
    public void jar(Action<? super InstallerJar> action) {
        getJar().configure(action);
    }

    public TaskProvider<@NotNull InstallerJson> getJson() {
        return this.json;
    }
    public void json(Action<? super InstallerJson> action) {
        getJson().configure(action);
    }

    public TaskProvider<LauncherJson> getLauncherJson() {
        return this.launcherJson;
    }
    public void launcherJson(Action<? super LauncherJson> action) {
        getLauncherJson().configure(action);
    }

    public Tool tool(Provider<Dependency> dependency) {
        return tool(dependency, true);
    }
    public Tool tool(Provider<Dependency> dependency, boolean transitive) {
        return tool(MavenArtifact.from(getObjects(), dependency).get().getDescriptor(), transitive);
    }
    public Tool tool(Dependency dependency) {
        return tool(dependency, true);
    }
    public Tool tool(Dependency dependency, boolean transitive) {
        return tool(MavenArtifact.from(getObjects(), dependency).getDescriptor(), transitive);
    }
    private Tool tool(String gav, boolean transitive) {
        var tree = MinimalResolvedArtifact.from(project, gav, transitive).get();
        this.jarConfig.configure(task -> {
            for (var artifact : tree)
                task.library(project.provider(() -> artifact), Util.noop());
        });
        return new Tool(gav, tree);
    }

    public void pack(TaskProvider<? extends AbstractArchiveTask> task) {
        pack(task, Util.noop());
    }
    public void pack(TaskProvider<? extends AbstractArchiveTask> task, Action<LibraryInfo> action) {
        pack(MinimalResolvedArtifact.from(project, task), action);
    }
    public void pack(Provider<MinimalResolvedArtifact> info) {
        pack(info, Util.noop());
    }
    public void pack(Provider<MinimalResolvedArtifact> info, Action<LibraryInfo> action) {
        this.getJson().configure(task -> task.library(info, action));
        this.getJar().configure(task -> task.pack(info));
    }

    public void library(String artifact) {
        library(artifact, Util.noop());
    }
    public void library(String artifact, Action<LibraryInfo> action) {
        library(MinimalResolvedArtifact.single(project, artifact), action);
    }
    public void library(TaskProvider<? extends AbstractArchiveTask> task) {
        library(task, Util.noop());
    }
    public void library(TaskProvider<? extends AbstractArchiveTask> task, Action<LibraryInfo> action) {
        library(MinimalResolvedArtifact.from(project, task), action);
    }
    public void library(Provider<MinimalResolvedArtifact> info) {
        library(info, Util.noop());
    }
    public void library(Provider<MinimalResolvedArtifact> info, Action<LibraryInfo> action) {
        this.getJson().configure(task -> task.library(info, action));
        this.jarConfig.configure(task -> task.library(info, action));
    }

    public void launcherLibraries(Configuration configuration) {
        this.getLauncherJson().configure(task -> task.libraries(configuration));
        this.jarConfig.configure(task -> task.libraries(configuration));
    }
    public void launcherLibrary(String artifact) {
        launcherLibrary(artifact, Util.noop());
    }
    public void launcherLibrary(String artifact, Action<LibraryInfo> action) {
        launcherLibrary(MinimalResolvedArtifact.single(project, artifact), action);
    }
    public void launcherLibrary(TaskProvider<? extends AbstractArchiveTask> task) {
        launcherLibrary(task, Util.noop());
    }
    public void launcherLibrary(TaskProvider<? extends AbstractArchiveTask> task, Action<LibraryInfo> action) {
        launcherLibrary(MinimalResolvedArtifact.from(project, task), action);
    }
    public void launcherLibrary(TaskProvider<? extends SingleFileOutput> task, String classifier) {
        launcherLibrary(task, classifier, Util.noop());
    }
    public void launcherLibrary(TaskProvider<? extends SingleFileOutput> task, String classifier, Action<LibraryInfo> action) {
        launcherLibrary(MinimalResolvedArtifact.from(MavenInfo.from(project, classifier), task.flatMap(SingleFileOutput::getOutput)), action);
    }
    public void launcherLibrary(Provider<MinimalResolvedArtifact> info) {
        launcherLibrary(info, Util.noop());
    }
    public void launcherLibrary(Provider<MinimalResolvedArtifact> info, Action<LibraryInfo> action) {
        this.getLauncherJson().configure(task -> task.library(info, action));
        this.jarConfig.configure(task -> task.library(info, action));
    }

    /// Helper to set the base installer to Forge's this is meant to make it easy to do something like:
    /// installer {
    ///   baseVersion = "1.0"
    /// }
    public void setBaseVersion(String version) {
        var module = (SharedUtil.SimpleModuleVersionIdentifier)Tools.INSTALLER.getModule();
        this.setBase(module.withVersion(version).toString());
    }
    public void setBase(String artifact) {
        base.configure(task -> task.setArtifact(artifact) );
    }

    @ApiStatus.Internal
    public static final String DEFAULT_NAME = "installer";

    @ApiStatus.Internal
    public static Installer register(Project project, ForgeDevExtension ext, String name) {
        var tasks = project.getTasks();

        // This is annoying, but this allows me to pass in a reference to Installer to the tasks so they can see each other
        class Holder {
            Installer value;
        }
        var holder = new Holder();
        var self = project.getProviders().provider(() -> holder.value);

        var base = tasks.register(name + "DownloadBase", DownloadDependency.class);
        var jarConfig = tasks.register(name + "JarConfig", InstallerJarConfig.class, self, base);
        var jar = tasks.register(name + "Jar", InstallerJar.class);
        var json = tasks.register(name + "Json", InstallerJson.class);
        var launcherJson = tasks.register(name + "LauncherJson", LauncherJson.class);

        var baseDir = project.getLayout().getBuildDirectory().dir(name).get();
        var ret = project.getObjects().newInstance(Installer.class, project, name, jar, jarConfig, json, launcherJson, base);
        holder.value = ret;
        ret.getDev().convention(!ext.isCi());

        jar.configure(task -> {
            task.getArchiveClassifier().set(Util.kebab(name));
            task.dependsOn(jarConfig);
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);

            task.from(
                json,
                launcherJson
            );

            task.dependsOn(base);
            var text = project.getResources().getText();
            var baseManifest = base.flatMap(DownloadDependency::getOutput).map(file -> text.fromArchiveEntry(file, "META-INF/MANIFEST.MF", "utf-8"));
            task.manifest(manifest -> manifest.from(baseManifest));
        });

        json.configure(task -> {
            task.getOutput().set(baseDir.file("install_profile.json"));
        });

        launcherJson.configure(task -> {
            task.getOutput().set(baseDir.file("version.json"));
        });

        ret.setBase(Tools.INSTALLER.getModule().toString());

        return ret;
    }
}
