/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy.values;

import net.minecraftforge.forgedev.Util;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MinimalResolvedArtifact(MavenInfo info, File file) implements Serializable {
    private static Provider<MinimalResolvedArtifact> from(Project project, ProjectDependency projectDependency, FileCollection files) {
        var info = MavenInfo.from(projectDependency);
        var ret = project.getObjects().property(MinimalResolvedArtifact.class).value(project.provider(files::getSingleFile).map(file ->
            new MinimalResolvedArtifact(info, file)
        ));
        return Util.finalize(project, ret);
    }

    public static Provider<MinimalResolvedArtifact> from(Project project, TaskProvider<? extends AbstractArchiveTask> task) {
        var ret = project.getObjects().property(MinimalResolvedArtifact.class).value(
            MavenInfo.from(project, task).zip(
                task.flatMap(AbstractArchiveTask::getArchiveFile).map(RegularFile::getAsFile),
                MinimalResolvedArtifact::new
            )
        );
        return Util.finalize(project, ret);
    }

    public static MinimalResolvedArtifact from(Project project, ResolvedArtifactResult artifact) {
        var info = MavenInfo.from(project, artifact);
        return new MinimalResolvedArtifact(info, artifact.getFile());
    }

    public static Provider<List<MinimalResolvedArtifact>> from(Project project, Configuration configuration) {
        var ret = project.getObjects().listProperty(MinimalResolvedArtifact.class);
        ret.addAll(configuration.getIncoming().getArtifacts().getResolvedArtifacts().map(transform(project)));
        return Util.finalize(project, ret);
    }

    private static Transformer<List<MinimalResolvedArtifact>, Set<ResolvedArtifactResult>> transform(Project project) {
        return results -> {
            var artifacts = new ArrayList<MinimalResolvedArtifact>(results.size());
            for (var artifact : results) {
                artifacts.add(MinimalResolvedArtifact.from(project, artifact));
            }
            return artifacts;
        };
    }

    public static Provider<MinimalResolvedArtifact> single(Project project, String artifact) {
        return from(project, artifact, false).map(l -> l.get(0));
    }
    public static Provider<MinimalResolvedArtifact> from(MavenInfo info, Provider<RegularFile> file) {
        return file.map(f -> new MinimalResolvedArtifact(info, f.getAsFile()));
    }
    public static Provider<List<MinimalResolvedArtifact>> from(Project project, String artifact) {
        return from(project, artifact, true);
    }
    public static Provider<List<MinimalResolvedArtifact>> from(Project project, String artifact, boolean transitive) {
        var ret = project.getObjects().listProperty(MinimalResolvedArtifact.class);

        var c = project.getConfigurations().detachedConfiguration(
            project.getDependencies().create(artifact)
        );
        c.setTransitive(transitive);
        ret.set(c.getIncoming().getArtifacts().getResolvedArtifacts().map(transform(project)));
        return Util.finalize(project, ret);
    }

    private static void from(Project project, ProjectDependency projectDependency, ListProperty<MinimalResolvedArtifact> ret) {
        var configurations = project.getConfigurations();

        var subproject = project.project(projectDependency.getPath());

        var singleFile = configurations.detachedConfiguration(projectDependency);
        singleFile.setTransitive(false);
        ret.add(MinimalResolvedArtifact.from(subproject, projectDependency, singleFile));

        var transitive = configurations.detachedConfiguration(projectDependency);
        for (var d : transitive.getAllDependencies()) {
            if (d.equals(projectDependency)) {
                continue;
            } else if (d instanceof ProjectDependency nestedProjectDependency) {
                from(project, nestedProjectDependency, ret);
            } else {
                var c = configurations.detachedConfiguration(d);
                c.setTransitive(false);
                for (var artifact : c.getIncoming().getArtifacts().getResolvedArtifacts().get()) {
                    ret.add(MinimalResolvedArtifact.from(project, artifact));
                }
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof MinimalResolvedArtifact that
            && Objects.equals(this.info.name(), that.info.name());
    }
}
