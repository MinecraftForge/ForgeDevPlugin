/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy.values;

import net.minecraftforge.forgedev.Util;
import net.minecraftforge.util.hash.HashFunction;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public record LibraryInfo(String name, Downloads downloads) implements Serializable {
    public record Downloads(ArtifactInfo artifact) implements Serializable {
        public static class ArtifactInfo  implements Serializable {
            public String path;
            public String url;
            public String sha1;
            public long size;

            public ArtifactInfo(String path, String url, String sha1, long size) {
                this.path = path;
                this.url = url;
                this.sha1 = sha1;
                this.size = size;
            }

            public ArtifactInfo validateUrl(boolean offline) {
                if (offline || !url.startsWith("https://libraries.minecraft.net/"))
                    return this;

                if (!Util.checkExists(url))
                    return new ArtifactInfo(path, "https://maven.minecraftforge.net/" + url.substring("https://libraries.minecraft.net/".length()), sha1, size);

                return this;
            }
        }
    }

    public LibraryInfo(String name, String path, String url, String sha1, long size) {
        this(name, new Downloads(new Downloads.ArtifactInfo(path, url, sha1, size)));
    }

    public LibraryInfo(MavenInfo info, File file, String url) {
        this(info.name(), info.path(), url, HashFunction.SHA1.sneakyHash(file), file.length());
    }

    public LibraryInfo validateUrl(boolean offline) {
        var artifact = this.downloads.artifact.validateUrl(offline);
        if (this.downloads.artifact == artifact)
            return this;

        return new LibraryInfo(name, new Downloads(artifact));
    }

    public static Map<String, LibraryInfo> from(Collection<MinimalResolvedArtifact> dependencies) {
        return from(dependencies, null);
    }

    public static Map<String, LibraryInfo> from(Collection<MinimalResolvedArtifact> dependencies, boolean validateUrl) {
        return from(dependencies, Boolean.valueOf(validateUrl));
    }

    public static LibraryInfo from(MinimalResolvedArtifact dependency) {
        var info = dependency.info();
        var url = "https://libraries.minecraft.net/" + info.path();
        if (!Util.checkExists(url))
            url = "https://maven.minecraftforge.net/" + info.path();

        var file = dependency.file();
        var sha1 = HashFunction.SHA1.sneakyHash(dependency.file());

        return new LibraryInfo(
            info.name(),
            info.path(),
            url,
            sha1,
            file.length()
        );
    }

    private static Map<String, LibraryInfo> from(Collection<MinimalResolvedArtifact> dependencies, Boolean offline) {
        var ret = new LinkedHashMap<String, LibraryInfo>(dependencies.size());
        var semaphore = new Semaphore(1, true);
        dependencies.parallelStream().forEachOrdered(dependency -> {
            var library = from(dependency);
            if (offline != null)
                library = library.validateUrl(offline);

            try {
                semaphore.acquire();
                ret.put(dependency.info().key(), library);
                semaphore.release();
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while trying to get library info for " + dependency.info(), e);
            }
        });

        return ret;
    }

    public static Provider<LibraryInfo> from(Project project, TaskProvider<? extends AbstractArchiveTask> task) {
        return MinimalResolvedArtifact.from(project, task).map(LibraryInfo::from);
    }

    @SafeVarargs
    public static Provider<Map<String, LibraryInfo>> from(Project project, TaskProvider<? extends AbstractArchiveTask>... tasks) {
        var dependencies = project.getObjects().listProperty(MinimalResolvedArtifact.class);
        for (var task : tasks) {
            dependencies.add(MinimalResolvedArtifact.from(project, task));
        }

        var ret = project.getObjects().mapProperty(String.class, LibraryInfo.class).value(dependencies.map(LibraryInfo::from));
        return Util.finalize(project, ret);
    }

    public static Provider<Map<String, LibraryInfo>> from(Project project, Configuration configuration) {
        return MinimalResolvedArtifact.from(project, configuration).map(LibraryInfo::from);
    }

    public static List<LibraryInfo> toList(List<MinimalResolvedArtifact> list) {
        return list.stream().map(LibraryInfo::from).toList();
    }

    public static Transformer<LibraryInfo, LibraryInfo> apply(Action<LibraryInfo> action) {
        return info -> {
            action.execute(info);
            return info;
        };
    }
}
