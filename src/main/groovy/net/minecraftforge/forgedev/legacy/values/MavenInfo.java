/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy.values;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;

public record MavenInfo(String key, String name, String path, ArtifactInfo art) implements Serializable, Comparable<MavenInfo> {
    public record ArtifactInfo(String group, String name, String version, @Nullable String classifier,
                               String extension) implements Serializable { }

    @Override
    public int compareTo(MavenInfo that) {
        return this.key.compareTo(that.key);
    }

    public static MavenInfo from(String gav) {
        var parts =  gav.split(":");
        var group =  parts[0];
        var name = parts[1];
        var version = parts[2];

        String classifier = null;
        String extension = null;

        if (parts.length > 3) {
            classifier = parts[3];
            var idx = classifier.indexOf('@');
            if (idx != -1) {
                classifier = classifier.substring(0, idx);
                extension = classifier.substring(idx + 1);
            }
        } else {
            var idx = version.indexOf('@');
            if (idx != -1) {
                extension = version.substring(idx + 1);
                version = version.substring(0, idx);
            }
        }
        return from(group, name, version, classifier, extension);
    }

    public static MavenInfo from(String artGroup, String artName, String artVersion, @Nullable String artClassifier, @Nullable String artExtension) {
        if (artExtension == null)
            artExtension = "jar";

        var key = artGroup + ':' + artName;
        var name = key + ':' + artVersion;
        var path = MessageFormat.format("{0}/{1}/{2}/{1}-{2}", artGroup.replace('.', '/'), artName, artVersion);
        if (artClassifier != null) {
            name += ':' + artClassifier;
            path += '-' + artClassifier;
        }
        if (!"jar".equals(artExtension)) {
            name += '@' + artExtension;
        }
        path += '.' + artExtension;

        return new MavenInfo(
            key,
            name,
            path,
            new ArtifactInfo(
                artGroup,
                artName,
                artVersion,
                artClassifier,
                artExtension
            )
        );
    }

    // NOTE: Relies on Gradle internals
    //       See https://github.com/gradle/gradle/issues/23702
    public static MavenInfo from(Project project, ResolvedArtifactResult dependency) {
        String group, name, version, classifier, extension;
        {
            var component = dependency.getId();
            if (component instanceof ModuleComponentArtifactIdentifier moduleId) {
                group = moduleId.getComponentIdentifier().getGroup();
                name = moduleId.getComponentIdentifier().getModule();
                version = moduleId.getComponentIdentifier().getVersion();
                if (moduleId instanceof DefaultModuleComponentArtifactIdentifier defaultId) {
                    classifier = defaultId.getName().getClassifier();
                    extension = defaultId.getName().getExtension();
                } else {
                    classifier = null;
                    extension = null;
                }
            } else if (component.getComponentIdentifier() instanceof ProjectComponentIdentifier projectId) {
                var p = project.findProject(projectId.getProjectPath());
                if (p == null)
                    throw new IllegalArgumentException("Cannot get maven info for a project dependency that has no project: " + projectId);

                group = p.getGroup().toString();
                name = p.getName();
                version = p.getVersion().toString();
                classifier = null;
                extension = null;
            } else {
                throw new IllegalArgumentException("Cannot get maven info for a local/unknown dependency:" + component + " (" + component.getClass() + ')');
            }
        }

        return from(group, name, version, classifier, extension);
    }

    public static MavenInfo from(Project project) {
        return from(project, (String) null);
    }

    public static MavenInfo from(ProjectDependency project) {
        return from(project.getGroup(), project.getName(), project.getVersion(), null, null);
    }

    public static MavenInfo from(Project project, String classifier) {
        return from(project.getGroup().toString(), project.getName(), project.getVersion().toString(), classifier, null);
    }

    public static Provider<MavenInfo> from(Project project, TaskProvider<? extends AbstractArchiveTask> task) {
        var ret = project.getObjects().property(MavenInfo.class).value(project.getProviders().zip(task.flatMap(AbstractArchiveTask::getArchiveClassifier), task.flatMap(AbstractArchiveTask::getArchiveExtension), (classifier, extension) ->
            from(project.getGroup().toString(), project.getName(), project.getVersion().toString(), classifier, extension)
        ));

        ret.disallowChanges();
        ret.finalizeValueOnRead();
        if (project.getState().getExecuted()) {
            ret.finalizeValue();
        }

        return ret;
    }
}
