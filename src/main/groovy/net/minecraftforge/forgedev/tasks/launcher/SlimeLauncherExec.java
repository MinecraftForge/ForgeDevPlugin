/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.launcher;

import net.minecraftforge.forgedev.ForgeDevProblems;
import net.minecraftforge.forgedev.ForgeDevTask;
import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.Util;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class SlimeLauncherExec extends JavaExec implements ForgeDevTask, HasPublicType {
    public static TaskProvider<SlimeLauncherEclipseConfiguration> registerEclipse(Project project, SourceSet sourceSet, SlimeLauncherOptionsImpl options, TaskProvider<?> genEclipseRuns) {
        var generateEclipseRunTaskName = sourceSet.getTaskName("genEclipseRun", options.getName());
        var runTaskName = sourceSet.getTaskName("run", options.getName());

        var genEclipseRun = project.getTasks().register(generateEclipseRunTaskName, SlimeLauncherEclipseConfiguration.class, task -> {
            task.getRunName().set(options.getName());
            task.setDescription("Generates the '%s' Slime Launcher run configuration for Eclipse.".formatted(options.getName()));
            task.getOutputFile().set(task.getProjectLayout().getProjectDirectory().file(runTaskName + ".launch"));

            var configName = sourceSet.getRuntimeClasspathConfigurationName();
            var config = project.getConfigurations().getByName(configName);

            task.getProjectDependencies().addAll(config.getIncoming().getArtifacts().getResolvedArtifacts()
                .map(artifacts -> {
                    var ret = new ArrayList<String>();
                    // We need to reference our self as well
                    ret.add(Util.getProjectEclipseName(project));

                    var root = project.getRootProject();
                    for (var artifact : artifacts) {
                        var id = artifact.getId().getComponentIdentifier();
                        if (id instanceof ProjectComponentIdentifier projectIdentifier) {
                            var dep = root.project(projectIdentifier.getProjectPath());
                            ret.add(Util.getProjectEclipseName(dep));
                        }
                    }
                    return ret;
                }));

            task.getSourceSetName().set(sourceSet.getName());
            task.getOptions().set(options);
        });
        genEclipseRuns.configure(task -> task.dependsOn(genEclipseRun));
        return genEclipseRun;
    }

    public static TaskProvider<SlimeLauncherExec> register(Project project, SourceSet sourceSet, SlimeLauncherOptionsImpl options) {
        var runTaskName = sourceSet.getTaskName("run", options.getName());
        return project.getTasks().register(runTaskName, SlimeLauncherExec.class, task -> {
            task.getRunName().set(options.getName());
            task.getSourceSetName().set(sourceSet.getName());
            task.setDescription("Runs the '%s' Slime Launcher run configuration.".formatted(options.getName()));

            task.classpath(task.getObjectFactory().fileCollection().from(task.getProviderFactory().provider(sourceSet::getRuntimeClasspath)));
            task.getOptions().set(options);
        });
    }

    protected abstract @Input Property<String> getRunName();

    protected abstract @Input Property<String> getSourceSetName();

    protected abstract @Nested Property<SlimeLauncherOptions> getOptions();

    public abstract @Internal DirectoryProperty getCacheDir();

    public abstract @InputFiles ConfigurableFileCollection getMetadata();

    protected abstract @Input @Optional Property<Boolean> getClient();

    private final ForgeDevProblems problems = this.getObjectFactory().newInstance(ForgeDevProblems.class);

    @Inject
    public SlimeLauncherExec() {
        this.setGroup("Slime Launcher");

        var tool = this.getTool(Tools.SLIMELAUNCHER);
        this.setClasspath(tool.getClasspath());
        if (tool.hasMainClass())
            this.getMainClass().set(tool.getMainClass());
        this.getJavaLauncher().set(Util.launcherFor(getProject(),tool.getJavaVersion()));
        this.getModularity().getInferModulePath().set(false);
    }

    @Override
    public @Internal TypeOf<?> getPublicType() {
        return TypeOf.typeOf(JavaExec.class);
    }

    @Override
    public void exec() {
        Provider<String> mainClass;
        List<String> args;

        //region Launcher Metadata Inheritance
        var options = ((SlimeLauncherOptionsInternal) this.getOptions().get()).inherit(Map.of(), this.getSourceSetName().get());

        mainClass = options.getMainClass().filter(Util.IS_NOT_BLANK);
        args = new ArrayList<>(options.getArgs().getOrElse(List.of()));
        this.jvmArgs(options.getJvmArgs().get());
        if (!options.getClasspath().isEmpty())
            this.setClasspath(options.getClasspath());
        if (options.getMinHeapSize().filter(Util.IS_NOT_BLANK).isPresent())
            this.setMinHeapSize(options.getMinHeapSize().get());
        if (options.getMaxHeapSize().filter(Util.IS_NOT_BLANK).isPresent())
            this.setMinHeapSize(options.getMaxHeapSize().get());
        this.systemProperties(options.getSystemProperties().get());
        this.environment(options.getEnvironment().get());
        this.workingDir(options.getWorkingDir().get());
        //endregion

        if (!this.getMainClass().get().startsWith("net.minecraftforge.launcher")) {
            this.getLogger().warn("WARNING: Main class is not Slime Launcher! Skipping additional configuration.");
        } else {
            this.args("--main", mainClass.get(),
                "--cache", this.getCacheDir().get().getAsFile().getAbsolutePath(),
                "--metadata", this.getMetadata().getSingleFile().getAbsolutePath(),
                "--");
        }

        this.args(args);

        if (!this.getClient().getOrElse(false))
            this.setStandardInput(System.in);

        try {
            Files.createDirectories(this.getWorkingDir().toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.getLogger().info("{} {}", this.getClasspath().getAsPath(), String.join(" ", this.getArgs()));
        try {
            super.exec();
        } catch (Exception e) {
            this.getLogger().error("Something went wrong! Here is some debug info.");
            this.getLogger().error("Args: {}", this.getArgs());
            this.getLogger().error("Options: {}", options);
            throw e;
        }
    }
}
