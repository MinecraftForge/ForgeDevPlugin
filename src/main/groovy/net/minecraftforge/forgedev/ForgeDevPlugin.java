/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.forgedev.publishvalidate.ValidatePublish;
import net.minecraftforge.forgedev.tasks.WriteManifest;
import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.external.javadoc.JavadocMemberLevel;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.gradle.plugins.ide.eclipse.GenerateEclipseProject;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.jetbrains.annotations.VisibleForTesting;

import javax.inject.Inject;

@VisibleForTesting
public abstract class ForgeDevPlugin extends EnhancedPlugin<Project> {
    public static final String NAME = "forgedev";
    public static final String DISPLAY_NAME = "ForgeDev";

    public static final Logger LOGGER = Logging.getLogger("ForgeDev");

    private final ForgeDevProblems problems = this.getObjects().newInstance(ForgeDevProblems.class);
    private ForgeDevExtension extension;

    @Inject
    public ForgeDevPlugin() {
        super(NAME, DISPLAY_NAME, "fdtools");
    }

    @Override
    public void setup(Project project) {
        project.setGroup("net.minecraftforge");

        this.extension = project.getExtensions().create(ForgeDevExtension.NAME, ForgeDevExtension.class, this, project);

        // These are 'static side effects' of apply the plugin.
        // Ideally there would be none, and they would all be opt-in. But I don't care to fix them right now.
        ValidatePublish.apply(project);
        tidyJavadocs(project);
        setupGenerateResources(project);
        mergeSourceSets(problems, project);
    }

    public ForgeDevExtension getExtension() {
        return extension;
    }

    /// Does a little bit of cleanup to the default javadoc tasks.
    /// This isn't actually important as we don't do any javadoc generation, but I don't wanna delete it
    /// Feel free to if we ever decide how we wanna deal with javadocs
    private static void tidyJavadocs(Project project) {
        var tasks = project.getTasks();
        project.getPluginManager().withPlugin("java", javaAppliedPlugin -> {
            tasks.withType(Javadoc.class).configureEach(task -> {
                task.setFailOnError(false);
                task.options(minimalOptions -> {
                    if (minimalOptions instanceof CoreJavadocOptions coreOptions) {
                        coreOptions.setMemberLevel(JavadocMemberLevel.PUBLIC);
                        coreOptions.addBooleanOption("Xdoclint:all,-missing", true);
                    }
                });
            });
        });
    }

    /// This sets up the 'generateResources' task which will be executed before 'processResources'
    /// This task is set as a dependency for all Jar tasks, as well as added to the eclipse
    /// synchronization tasks. Which will cause it to be run whenever the project is refreshed in eclipse
    ///
    /// It also adds the 'eclipseProject' and 'eclipseClasspath' to the eclipse sync tasks
    private static void setupGenerateResources(Project project) {
        var tasks = project.getTasks();
        project.getPluginManager().withPlugin("java", javaAppliedPlugin -> {
            var generateResources = SharedUtil.runFirst(project, tasks.register("generateResources"));
            var processResources = tasks.named("processResources", ProcessResources.class, task ->
                task.dependsOn(generateResources)
            );

            var java = project.getExtensions().getByType(JavaPluginExtension.class);

            tasks.withType(JavaCompile.class).configureEach(task -> {
                task.dependsOn(processResources);
                var options = task.getOptions();
                options.setWarnings(false); // Shut up deprecated for removal warnings
                options.getForkOptions().setMemoryMaximumSize("6G"); // Needed to make compiling faster, and not run out of heap space in some cases.
            });

            tasks.withType(Jar.class).configureEach(task -> {
                // This is a dirty hack, but there is no way for us to figure out what are just the tasks we want, as they are registered lazily
                // in a way that I have not found a way to react to
                for (var sourceSet : java.getSourceSets()) {
                    if (
                        task.getName().equals(sourceSet.getCompileJavaTaskName()) ||
                        task.getName().equals(sourceSet.getSourcesJarTaskName()) ||
                        task.getName().equals(sourceSet.getProcessResourcesTaskName())
                    ) {
                        task.dependsOn(processResources);
                        return;
                    }
                }
            });

            project.getPluginManager().withPlugin("eclipse", eclipseAppliedPlugin -> {
                var eclipse = project.getExtensions().getByType(EclipseModel.class);
                eclipse.synchronizationTasks(
                    processResources,
                    tasks.named("eclipseClasspath", GenerateEclipseClasspath.class),
                    tasks.named("eclipseProject", GenerateEclipseProject.class)
                );
            });

            // Write the manifest to our resources directory because we use it for version information
            var writeManifest = WriteManifest.register(project, tasks.named("jar", Jar.class));
            generateResources.configure(task -> task.dependsOn(writeManifest));
        });
    }

    // region Merge SourceSets =============================================
    /// We need to merge sourcesets into one directory instead of one for classes and one for resources
    /// Because the Java Module system doesn't support that structure.
    /// I'm tired of arguing with gradle folks, so this is slightly hacky, but it works in IDE and Eclipse so i'm happy
    private static void mergeSourceSets(ForgeDevProblems problems, Project project) {
        var sourceSetsDir = project.getObjects().directoryProperty().value(project.getLayout().getBuildDirectory().dir("sourceSets"));
        var mergeSourceSets = problems.test("net.minecraftforge.gradle.merge-source-sets");
        project.getPluginManager().withPlugin("java", javaAppliedPlugin -> {
            var java = project.getExtensions().getByType(JavaPluginExtension.class);
            java.getSourceSets().configureEach(sourceSet -> {
                if (mergeSourceSets) {
                    // This is documented in SourceSetOutput's javadoc comment
                    var unifiedDir = sourceSetsDir.dir(sourceSet.getName());
                    sourceSet.getOutput().setResourcesDir(unifiedDir);
                    sourceSet.getJava().getDestinationDirectory().set(unifiedDir);
                }

                project.getPluginManager().withPlugin("eclipse", eclipsePlugin -> {
                    var eclipse = project.getExtensions().getByType(EclipseModel.class);
                    if (mergeSourceSets)
                        eclipse.getClasspath().setDefaultOutputDir(sourceSetsDir.getAsFile().get());
                    else
                        System.out.println("WARNING: Source set will not be merged for " + sourceSet.getName() + "!");
                });
            });
        });
    }
    // endregion ===========================================================
}
