/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.runs;

import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherEclipseConfiguration;
import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherExec;
import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherOptions;
import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherOptionsImpl;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;

public abstract class Run implements Named {
    private final String name;
    private final Project project;
    private final SlimeLauncherOptionsImpl options;
    private final TaskProvider<SlimeLauncherEclipseConfiguration> eclipse;
    private final TaskProvider<SlimeLauncherEclipseConfiguration> eclipseTest;
    private final TaskProvider<SlimeLauncherExec> run;
    private final TaskProvider<SlimeLauncherExec> runTest;

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public Run(String name, Project project, TaskProvider<?> genEclipseRuns, String version, Provider<File> metadataProvider) {
        this.name = name;
        this.project = project;
        this.options = getObjects().newInstance(SlimeLauncherOptionsImpl.class, name);
        var metadata = getObjects().fileCollection().from(metadataProvider);

        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var main = java.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME).get();
        var test = java.getSourceSets().named(SourceSet.TEST_SOURCE_SET_NAME).get();

        this.eclipse = SlimeLauncherExec.registerEclipse(project, main, options, version, metadata, genEclipseRuns);
        this.eclipseTest = SlimeLauncherExec.registerEclipse(project, test, options, version, metadata, genEclipseRuns);

        this.run = SlimeLauncherExec.register(project, main, options, version, metadata);
        this.runTest = SlimeLauncherExec.register(project, test, options, version, metadata);
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void metadata(FileCollection metadata) {
        eclipse(task -> task.getMetadata().setFrom(metadata));
        eclipseTest(task -> task.getMetadata().setFrom(metadata));
        run(task -> task.getMetadata().setFrom(metadata));
        runTest(task -> task.getMetadata().setFrom(metadata));
    }

    public void metadata(Provider<File> metadata) {
        metadata(this.project.files(metadata));
    }

    public SlimeLauncherOptionsImpl getOptions() {
        return this.options;
    }
    public SlimeLauncherOptionsImpl options(Action<? super SlimeLauncherOptions> action) {
        action.execute(getOptions());
        return getOptions();
    }
    public TaskProvider<SlimeLauncherEclipseConfiguration> getEclipse() {
        return this.eclipse;
    }
    public TaskProvider<SlimeLauncherEclipseConfiguration> eclipse(Action<? super SlimeLauncherEclipseConfiguration> action) {
        getEclipse().configure(action);
        return getEclipse();
    }
    public TaskProvider<SlimeLauncherEclipseConfiguration> getEclipseTest() {
        return this.eclipseTest;
    }

    public TaskProvider<SlimeLauncherEclipseConfiguration> eclipseTest(Action<? super SlimeLauncherEclipseConfiguration> action) {
        getEclipseTest().configure(action);
        return getEclipseTest();
    }
    public TaskProvider<SlimeLauncherExec> getRun() {
        return this.run;
    }
    public TaskProvider<SlimeLauncherExec> run(Action<? super SlimeLauncherExec> action) {
        getRun().configure(action);
        return getRun();
    }
    public TaskProvider<SlimeLauncherExec> getRunTest() {
        return this.runTest;
    }
    public TaskProvider<SlimeLauncherExec> runTest(Action<? super SlimeLauncherExec> action) {
        getRunTest().configure(action);
        return getRunTest();
    }
}
