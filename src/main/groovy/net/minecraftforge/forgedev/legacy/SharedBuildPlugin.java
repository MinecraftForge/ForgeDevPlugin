/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy;

import net.minecraftforge.forgedev.tasks.WriteManifest;
import net.minecraftforge.forgedev.publishvalidate.ValidatePublish;
import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.external.javadoc.JavadocMemberLevel;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.gradle.plugins.ide.eclipse.GenerateEclipseProject;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import javax.inject.Inject;
import java.util.List;

abstract class SharedBuildPlugin implements Plugin<Project> {
    protected abstract @Inject ProjectLayout getLayout();

    @Inject
    public SharedBuildPlugin() { }

    @Override
    public void apply(Project project) {
        project.setGroup("net.minecraftforge");

        var tasks = project.getTasks();

        project.getPluginManager().withPlugin("java", javaAppliedPlugin -> {
            var generateResources = SharedUtil.runFirst(project, tasks.register("generateResources"));
            var processResources = tasks.named("processResources", ProcessResources.class, task ->
                task.dependsOn(generateResources)
            );

            var java = project.getExtensions().getByType(JavaPluginExtension.class);
            tasks.withType(Javadoc.class).configureEach(task -> {
                task.setFailOnError(false);
                task.options(minimalOptions -> {
                    if (minimalOptions instanceof CoreJavadocOptions coreOptions) {
                        coreOptions.setMemberLevel(JavadocMemberLevel.PUBLIC);
                        coreOptions.addBooleanOption("Xdoclint:all,-missing", true);
                    }
                });
            });

            tasks.withType(JavaCompile.class).configureEach(task -> {
                task.dependsOn(processResources);
                var options = task.getOptions();
                options.setWarnings(false); // Shutup deprecated for removal warnings
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

            // Write the manifest to our resources directory because we use it for version information
            var writeManifest = WriteManifest.register(project, tasks.named("jar", Jar.class));
            generateResources.configure(task -> task.dependsOn(writeManifest));

            project.getPluginManager().withPlugin("eclipse", eclipseAppliedPlugin -> {
                var eclipse = project.getExtensions().getByType(EclipseModel.class);
                eclipse.synchronizationTasks(
                    processResources,
                    tasks.named("eclipseClasspath", GenerateEclipseClasspath.class),
                    tasks.named("eclipseProject", GenerateEclipseProject.class)
                );
            });
        });

        project.getPluginManager().withPlugin("maven-publish", maven -> ValidatePublish.onApplyMavenPublish(project));
    }
}
