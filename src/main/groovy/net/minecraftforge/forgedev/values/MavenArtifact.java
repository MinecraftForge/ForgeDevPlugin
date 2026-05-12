/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.values;

import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.Serializable;
import java.io.File;
import java.util.Objects;

public abstract class MavenArtifact implements Serializable {
    private final String group;
    private final String name;
    private final @Nullable String version;
    private final @Nullable String classifier;
    private final String extension;

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public MavenArtifact(String group, String name, @Nullable String version, @Nullable String classifier, String extension) {
        this.group = group;
        this.name = name;
        this.version = version == null || version.isEmpty() ? null : version;
        this.classifier = classifier == null || classifier.isEmpty() ? null : classifier;
        this.extension = extension;
    }

    public static MavenArtifact from(ObjectFactory objects, String descriptor) {
        var pts = descriptor.split(":");
        if (pts.length < 2)
            throw new IllegalArgumentException("Descriptor is missing group and name: " + descriptor);

        String extension = "";
        int last = pts.length - 1;
        int idx = pts[last].indexOf('@');
        if (idx != -1) {
            extension = pts[last].substring(idx + 1);
            pts[last] = pts[last].substring(0, idx);
        }

        var version = pts.length > 2 ? pts[2] : "";
        var classifier = pts.length > 3 ? pts[3] : "";

        return objects.newInstance(MavenArtifact.class, pts[0], pts[1], version, classifier, extension);
    }

    private static String nonull(@Nullable String str) {
        return str == null ? "" : str;
    }

    public static MavenArtifact from(Project project) {
        return project.getObjects().newInstance(MavenArtifact.class, project.getGroup().toString(), project.getName(), project.getVersion().toString(), "", "jar");
    }

    public static MavenArtifact from(AbstractArchiveTask task) {
        return from(task.getProject(), task.getArchiveClassifier().getOrElse(""), task.getArchiveExtension().get());
    }

    public static MavenArtifact from(Project project, String classifier, String extension) {
        return project.getObjects().newInstance(MavenArtifact.class, project.getGroup().toString(), project.getName(), project.getVersion().toString(), classifier, extension);
    }

    @SuppressWarnings("unchecked")
    private static <R> @Nullable R getProperty(Object object, String property) {
        try {
            return (R) InvokerHelper.getProperty(object, property);
        } catch (MissingPropertyException e) {
            return null;
        }
    }
    private static ObjectFactory getObjects(Object value) {
        if (value instanceof ObjectFactory factory)
            return factory;
        return getProperty(value, "objectFactory");
    }
    public static MavenArtifact from(Dependency dependency) {
        return from(getObjects(dependency), dependency);
    }
    public static MavenArtifact from(ObjectFactory objects, Dependency dependency) {
        String version = dependency.getVersion() == null ? "" : dependency.getVersion();
        String classifier = getProperty(dependency, "classifier");
        if (classifier == null)
            classifier = "";
        String extension = getProperty(dependency, "artifactType");
        if (extension == null)
            extension = getProperty(dependency, "extension");
        if (extension == null)
            extension = "jar";
        return objects.newInstance(MavenArtifact.class, Objects.requireNonNull(dependency.getGroup()), dependency.getName(), version, classifier, extension);
    }

    public static Provider<MavenArtifact> from(TaskProvider<?> provider) {
        return provider.map(task -> {
            if (task instanceof AbstractArchiveTask archive)
                return from(archive);
            if (task instanceof PublishArtifact artifact)
                return from(task.getProject(), artifact.getClassifier(), artifact.getExtension());
            throw new IllegalArgumentException("Cannot create MavenArtifact from " + provider + ", Only AbstractArchiveTask or PublishArtifact is supported");
        });
    }

    public static Provider<MavenArtifact> from(Provider<?> provider) {
        return provider.map(value -> {
            if (value instanceof MavenArtifact object)
                return object;
            if (value instanceof Project project)
                return from(project);
            if (value instanceof AbstractArchiveTask task)
                return from(task);
            if (value instanceof Dependency dependency)
                return from(dependency);
            throw new IllegalArgumentException("Cannot create MavenArtifact from " + provider + ", must provide ObjectFactory");
        });
    }

    public static Provider<MavenArtifact> from(ObjectFactory objects, Provider<?> provider) {
        return provider.map(value -> {
            if (value instanceof MavenArtifact object)
                return object;
            if (value instanceof Project project)
                return from(project);
            if (value instanceof AbstractArchiveTask task)
                return from(task);
            if (value instanceof Dependency dependency)
                return from(objects, dependency);
            if (value instanceof String string)
                return from(objects, string);
            throw new IllegalArgumentException("Cannot create MavenArtifact from " + provider + ", Unknown type");
        });
    }

    public static MavenArtifact from(ObjectFactory objects, File base, File file) {

        // Parse the file path as a maven coordinate: {group}/{name}/{version}/{name}-{version}[-{classifier}.{ext}
        var basePath = base.getAbsolutePath();
        if (!basePath.endsWith(File.separator))
            basePath += File.separator;
        if (!file.getAbsolutePath().startsWith(basePath))
            throw new IllegalArgumentException("Cannot get maven info for " + file.getAbsolutePath() + " it is not a subfile of " + base.getAbsolutePath());

        var filename = file.getName();
        var versionDir = file.getParentFile();
        var version = versionDir.getName();
        var nameDir = versionDir.getParentFile();
        var name = nameDir.getName();

        var groupDir = nameDir.getParentFile();
        var group = groupDir.getAbsolutePath().substring(basePath.length()).replace('\\', '/').replace('/', '.');

        var classifier = "";
        var idx = name.length() + 1 + version.length();
        if (idx > filename.length())
            throw new IllegalArgumentException("Could not determine maven coordinates from: " + file.getAbsoluteFile());

        var chr = filename.charAt(idx);
        if (chr == '-') {
            // Find the next . which should be the extension.
            // This does mean that clssifiers cant have . in them. But fuck it
            var dot = filename.indexOf('.', idx + 1);
            classifier = filename.substring(idx + 1, dot);
            idx = dot;
            chr = filename.charAt(idx);
        }

        if (chr != '.')
            throw new IllegalArgumentException("Could not determine maven coordinates from: " + file.getAbsoluteFile());
        var extension = filename.substring(idx + 1);

        return objects.newInstance(MavenArtifact.class, group, name, version, classifier, extension);
    }

    public String group() {
        return this.group;
    }

    public MavenArtifact withGroup(String group) {
        return getObjects().newInstance(MavenArtifact.class, group, name, nonull(version), nonull(classifier), extension);
    }

    public String name() {
        return this.name;
    }

    public MavenArtifact withName(String name) {
        return getObjects().newInstance(MavenArtifact.class, group, name, nonull(version), nonull(classifier), extension);
    }

    public @Nullable String version() {
        return this.version;
    }

    public MavenArtifact withVersion(@Nullable String version) {
        return getObjects().newInstance(MavenArtifact.class, group, name, nonull(version), nonull(classifier), extension);
    }

    public @Nullable String classifier() {
        return this.classifier;
    }

    public MavenArtifact withClassifier(@Nullable String classifier) {
        return getObjects().newInstance(MavenArtifact.class, group, name, nonull(version), nonull(classifier), extension);
    }

    public String extension() {
        return this.extension;
    }

    public MavenArtifact withExtension(String extension) {
        return getObjects().newInstance(MavenArtifact.class, group, name, nonull(version), nonull(classifier), extension);
    }

    public String getDescriptor() {
        return getDescriptor(false);
    }
    public String getFullDescriptor() {
        return getDescriptor(true);
    }
    private String getDescriptor(boolean full) {
        var ret = new StringBuilder();
        ret.append(this.group).append(':').append(this.name);
        if (this.version != null) {
            ret.append(':').append(this.version);
            if (this.classifier != null)
                ret.append(':').append(this.classifier);
        }
        if (full || !"jar".equals(this.extension))
            ret.append('@').append(this.extension);
        return ret.toString();
    }

    public String getDirectory() {
        var ret = new StringBuilder();
        ret.append(this.group.replace('.', '/')).append('/').append(this.name);
        if (this.version != null)
            ret.append('/').append(this.version);
        return ret.toString();
    }

    public String getFileName() {
        var ret = new StringBuilder();
        ret.append(this.name);
        if (this.version != null) {
            ret.append('-').append(this.version);
            if (this.classifier != null)
                ret.append('-').append(this.classifier);
        }
        ret.append('.').append(this.extension);
        return ret.toString();
    }

    public String getPath() {
        return getDirectory() + '/' + getFileName();
    }

    @Override
    public int hashCode() {
        return getDescriptor().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MavenArtifact o && this.getDescriptor().equals(o.getDescriptor());
    }
}
