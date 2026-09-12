/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.runs;

import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherEclipseConfiguration;
import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherExec;
import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherOptions;
import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherOptionsImpl;
import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class Run implements Named {
    private final String name;
    private final EnhancedProblems problems;
    private final Project project;
    private final TaskProvider<?> genEclipseRuns;
    private final SlimeLauncherOptionsImpl options;
    private @Nullable Tasks main;
    private @Nullable Tasks test;

    private final DirectoryProperty cacheDir = this.getObjects().directoryProperty();

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public Run(String name, EnhancedProblems problems, Project project, TaskProvider<?> genEclipseRuns) {
        this.name = name;
        this.problems = problems;
        this.project = project;
        this.genEclipseRuns = genEclipseRuns;
        this.options = getObjects().newInstance(SlimeLauncherOptionsImpl.class, name);

        // Add the main and test source sets, if any source directories exist
        if (project.getPluginManager().hasPlugin("java")) {
            var sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
            for (var setName : new String[]{ SourceSet.MAIN_SOURCE_SET_NAME, SourceSet.TEST_SOURCE_SET_NAME }) {
                var sourceSet = sourceSets.named(setName).get();
                for (var dir : sourceSet.getAllSource().getSourceDirectories().getFiles()) {
                    if (dir.exists()) {
                        with(sourceSet);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    private final Map<SourceSet, Tasks> sourceSets = new HashMap<>();
    public Tasks with(SourceSet sourceSet) {
        return with(sourceSet, ignore -> {});
    }
    public Tasks with(SourceSet sourceSet, Action<? super Tasks> action) {
        var ret = this.sourceSets.computeIfAbsent(sourceSet, source ->
           this.getObjects().newInstance(Tasks.class, this, source)
        );
        action.execute(ret);

        if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()))
            this.main = ret;
        else if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName()))
            this.test = ret;

        return ret;
    }

    public void eclipsePrefix(String prefix) {
        setEclipsePrefix(prefix);
    }
    public void setEclipsePrefix(String prefix) {
        sourceSets.values().forEach(tasks -> tasks.setEclipsePrefix(prefix));
    }

    public void metadata(Provider<File> metadata) {
        setMetadata(metadata);
    }
    public void setMetadata(Provider<File> metadata) {
        setMetadata(this.project.files(metadata));
    }
    public void metadata(FileCollection metadata) {
        setMetadata(metadata);
    }
    public void setMetadata(FileCollection metadata) {
        sourceSets.values().forEach(tasks -> tasks.setMetadata(metadata));
    }

    public DirectoryProperty getCache() {
        return this.cacheDir;
    }

    public SlimeLauncherOptionsImpl getOptions() {
        return this.options;
    }
    public SlimeLauncherOptionsImpl options(Action<? super SlimeLauncherOptions> action) {
        action.execute(getOptions());
        return getOptions();
    }

    // region Legacy ========================================================================================
    private Tasks getMain() {
        return Objects.requireNonNull(this.main, "Unknown Sourceset: Call with(sourceSets.main) first");
    }
    private Tasks getTest() {
        return Objects.requireNonNull(this.test, "Unknown Sourceset: Call with(sourceSets.test) first");
    }
    public TaskProvider<SlimeLauncherEclipseConfiguration> getEclipse() {
        return this.getMain().getEclipse();
    }
    public TaskProvider<SlimeLauncherEclipseConfiguration> eclipse(Action<? super SlimeLauncherEclipseConfiguration> action) {
        return this.getMain().eclipse(action);
    }
    public TaskProvider<SlimeLauncherEclipseConfiguration> getEclipseTest() {
        return this.getTest().getEclipse();
    }
    public TaskProvider<SlimeLauncherEclipseConfiguration> eclipseTest(Action<? super SlimeLauncherEclipseConfiguration> action) {
        return this.getTest().eclipse(action);
    }
    public TaskProvider<SlimeLauncherExec> getRun() {
        return this.getMain().getRun();
    }
    public TaskProvider<SlimeLauncherExec> run(Action<? super SlimeLauncherExec> action) {
        return this.getMain().run(action);
    }
    public TaskProvider<SlimeLauncherExec> getRunTest() {
        return this.getTest().getRun();
    }
    public TaskProvider<SlimeLauncherExec> runTest(Action<? super SlimeLauncherExec> action) {
        return this.getTest().run(action);
    }
    // endregion ============================================================================================

    public static abstract class Tasks {
        private final Run owner;
        private final SlimeLauncherOptionsImpl options;
        private final SourceSet sourceSet;
        private final TaskProvider<SlimeLauncherEclipseConfiguration> eclipse;
        private final TaskProvider<SlimeLauncherExec> run;

        @Inject
        public Tasks(Run owner, SourceSet sourceSet) {
            this.owner = owner;
            this.options = owner.options;
            this.sourceSet = sourceSet;

            this.eclipse = SlimeLauncherExec.registerEclipse(owner.project, sourceSet, options, owner.genEclipseRuns);
            this.run = SlimeLauncherExec.register(owner.project, sourceSet, options);

            var ensured = owner.cacheDir.map(owner.problems.ensureFileLocation());
            eclipse(task -> task.getCacheDir().set(ensured));
            run(task -> task.getCacheDir().set(ensured));
        }

        public void eclipsePrefix(String prefix) {
            setEclipsePrefix(prefix);
        }
        public void setEclipsePrefix(String prefix) {
            var runTaskName = sourceSet.getTaskName("run", options.getName());
            var fileName = prefix + " - " + runTaskName + ".launch";
            eclipse(task -> task.getOutputFile().set(task.getProject().getLayout().getProjectDirectory().file(fileName)));
        }

        public void metadata(Provider<File> metadata) {
            setMetadata(metadata);
        }
        public void setMetadata(Provider<File> metadata) {
            setMetadata(this.owner.project.files(metadata));
        }
        public void metadata(FileCollection metadata) {
            setMetadata(metadata);
        }
        public void setMetadata(FileCollection metadata) {
            eclipse(task -> task.getMetadata().setFrom(metadata));
            run(task -> task.getMetadata().setFrom(metadata));
        }

        public TaskProvider<SlimeLauncherEclipseConfiguration> getEclipse() {
            return this.eclipse;
        }
        public TaskProvider<SlimeLauncherEclipseConfiguration> eclipse(Action<? super SlimeLauncherEclipseConfiguration> action) {
            getEclipse().configure(action);
            return getEclipse();
        }
        public TaskProvider<SlimeLauncherExec> getRun() {
            return this.run;
        }
        public TaskProvider<SlimeLauncherExec> run(Action<? super SlimeLauncherExec> action) {
            getRun().configure(action);
            return getRun();
        }
    }
}
