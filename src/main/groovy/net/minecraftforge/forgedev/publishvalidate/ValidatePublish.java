/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.publishvalidate;

import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.Util;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import javax.naming.spi.ObjectFactory;

public class ValidatePublish {
    static final String NAME = "validate";
    static final String CONFIG_PRODUCER = NAME + "Producer";
    static final String CONFIG_CONSUMER = NAME + "Consumer";
    static final String TASK_NAME = NAME + "Published";

    private static boolean enabled(Project project) {
        return "true".equalsIgnoreCase(project.getProviders().gradleProperty("net.minecraftforge.forgedev.validate.publish").getOrElse("false"));
    }
    public static void apply(Project project) {
        if (!enabled(project))
            return;

        project.getPluginManager().withPlugin("maven-publish", maven -> ValidatePublish.onApplyMavenPublish(project));
    }

    // Creates a consumable {NAME} configuration, with the usage of {NAME}
    // and forces all publication tasks publish to a flat folder in our builder folder
    public static void onApplyMavenPublish(Project project) {
        if (!enabled(project))
            return;

        configurtion(project, true);

        var targetMaven = project.getLayout().getBuildDirectory().file("validate-publish");

        var cleanupTask = project.getTasks().register("cleanupValidatePublish", DeleteDirectoryTask.class, task -> {
            task.getDirectory().set(targetMaven);
            task.getOutputs().upToDateWhen(t -> false);
        });

        project.getPluginManager().withPlugin("maven-publish", maven -> {
            var publishing = project.getExtensions().getByType(PublishingExtension.class);
            // Publish to our
            publishing.getRepositories().maven(repo -> {
               repo.setName(NAME);
               repo.setUrl(targetMaven.get().getAsFile().toURI());
            });

            // Add the output directory as an artifact of the ALL publish task
            var publishAll = project.getTasks().named("publishAllPublicationsTo" + Util.capitalize(NAME) + "Repository");
            publishAll.configure(task -> task.setGroup(null));
            project.getArtifacts().add(CONFIG_PRODUCER, targetMaven, cfg -> cfg.builtBy(publishAll));

            publishing.getPublications().configureEach(pub -> {
                if (!(pub instanceof MavenPublication))
                    return;
                var publishToDir = project.getTasks().named("publish" + Util.capitalize(pub.getName()) + "PublicationTo" + Util.capitalize(NAME) + "Repository");
                publishToDir.configure(task -> {
                    task.setGroup(null);
                    // All publications share this output directory, having every task depend on the cleanup
                    // should make the cleanup run before any of them
                    task.dependsOn(cleanupTask);
                    task.getOutputs().upToDateWhen(t -> false);
                });
            });
        });
    }

    public static Consumer registerConsumer(Project project, ForgeDevExtension extension) {
        // Create consumer configuration
        var cfg = configurtion(project, false);

        // Add all projects to consumer configuration
        if (enabled(project)) {
            project.allprojects(proj -> {
                var dep = project.getDependencies().create(proj);
                project.getDependencies().add(CONFIG_CONSUMER, dep);
            });
        }

        var task = project.getTasks().register(TASK_NAME, ValidateTask.class, extension);
        task.configure(t -> t.getFiles().setFrom(cfg));

        return project.getObjects().newInstance(Consumer.class, cfg, task);
    }

    private static Configuration configurtion(Project project, boolean producer) {
        return project.getConfigurations().create(producer ? CONFIG_PRODUCER : CONFIG_CONSUMER, cfg -> {
            cfg.setCanBeConsumed(producer);
            cfg.setCanBeResolved(!producer);
            cfg.attributes(attrs -> {
                attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, NAME));
            });
        });
    }

    public static abstract class Consumer {
        private final Configuration config;
        private final TaskProvider<ValidateTask> task;

        @Inject public abstract ObjectFactory getObjects();

        @Inject
        public Consumer(Configuration config, TaskProvider<ValidateTask> task) {
            this.config = config;
            this.task = task;
        }

        public Configuration getConfig() {
            return config;
        }
        public void config(Action<Configuration> action) {
            action.execute(config);
        }

        public TaskProvider<ValidateTask> getTask() {
            return task;
        }
        public void task(Action<ValidateTask> action) {
            //getTask().configure(action);
            action.execute(task.get());
        }
    }
}
