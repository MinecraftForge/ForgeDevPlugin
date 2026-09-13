/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.userdev;

import net.minecraftforge.forgedev.ForgeDevTask;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.values.MavenArtifact;
import net.minecraftforge.gradleutils.shared.Closures;
import net.minecraftforge.util.data.json.JsonData;
import net.minecraftforge.util.data.json.PatcherConfig;
import net.minecraftforge.util.data.json.RunConfig;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;

@CacheableTask
public abstract class UserdevConfig extends DefaultTask implements ForgeDevTask {
    private static final String DEFAULT_PATCHES_PREFIX_ORIGINAL = "a/";
    private static final String DEFAULT_PATCHES_PREFIX_MODIFIED = "b/";

    public abstract @OutputFile RegularFileProperty getOutput();

    public abstract @Input @Optional Property<PatcherConfig.V2.DataFunction> getProcessor();
    public abstract @Input @Optional MapProperty<String, File> getProcessorData();
    public abstract @Input @Optional Property<String> getPatchesOriginalPrefix();
    public abstract @Input @Optional Property<String> getPatchesModifiedPrefix();
    public abstract @Input @Optional Property<Boolean> getNotchObf();
    public abstract @Input @Optional Property<String> getSourceFileEncoding();
    public abstract @Input @Optional ListProperty<String> getUniversalFilters();

    public abstract @Input Property<String> getMCPConfig();
    public abstract @Input ListProperty<String> getLibraries();
    public abstract @Input ListProperty<String> getModules();
    public abstract @Input Property<String> getUniversal();
    public abstract @Input Property<String> getSource();
    public abstract @Input @Optional Property<String> getInject();
    public abstract @Input @Optional Property<String> getPatches();
    public abstract @InputFiles @PathSensitive(PathSensitivity.NAME_ONLY) ConfigurableFileCollection getATs();
    public abstract @InputFiles @PathSensitive(PathSensitivity.NAME_ONLY) ConfigurableFileCollection getSASs();
    public abstract @InputFiles @PathSensitive(PathSensitivity.NAME_ONLY) ConfigurableFileCollection getSRGs();
    public abstract @Input @Optional ListProperty<String> getSRGLines();

    protected abstract @Input MapProperty<String, RunConfig> getRuns();

    public abstract @Input Property<String> getBinpatcherVersion();
    public abstract @Input ListProperty<String> getBinpatcherArguments();

    public abstract @Input @Optional ListProperty<String> getExtraRuntimeDeps();
    public abstract @Input @Optional ListProperty<String> getExtraCompileDeps();
    public abstract @Input @Optional ListProperty<String> getExtraAnnotationProcessorDeps();

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public UserdevConfig() {
        this.getOutput().convention(this.getDefaultOutputFile("json"));

        this.getPatchesOriginalPrefix().convention(DEFAULT_PATCHES_PREFIX_ORIGINAL);
        this.getPatchesModifiedPrefix().convention(DEFAULT_PATCHES_PREFIX_MODIFIED);
        this.getSourceFileEncoding().convention(StandardCharsets.UTF_8.name());
        this.getInject().convention(""); // ""inject/" add inject(Provider<Folder>) to UserDev in older versions
        this.getPatches().convention("patches/");
    }

    @TaskAction
    protected void exec() throws IOException {
        var config = new PatcherConfig();

        config.spec = 1;
        config.binpatches = "joined.lzma";
        config.sources = this.getSource().filter(Util.IS_NOT_BLANK).getOrNull();
        config.universal = this.getUniversal().filter(Util.IS_NOT_BLANK).getOrNull();
        config.patches = this.getPatches().filter(Util.IS_NOT_BLANK).getOrNull();
        config.inject = this.getInject().filter(Util.IS_NOT_BLANK).getOrNull();
        config.libraries = this.getLibraries().get();
        config.ats = DefaultGroovyMethods.collect(this.getATs(), Closures.<File, String>function(f -> "ats/" + f.getName()));
        if (config.ats.isEmpty()) config.ats = null;
        config.sass = DefaultGroovyMethods.collect(this.getSASs(), Closures.<File, String>function(f -> "sas/" + f.getName()));
        if (config.sass.isEmpty()) config.sass = null;
        config.srgs = DefaultGroovyMethods.collect(this.getSRGs(), Closures.<File, String>function(f -> "srgs/" + f.getName()));
        config.srgs.addAll(this.getSRGLines().get());
        if (config.srgs.isEmpty()) config.srgs = null;
        config.mcp = this.getMCPConfig().filter(Util.IS_NOT_BLANK).get();

        config.runs = this.getRuns().get();

        config.binpatcher = new PatcherConfig.Function();
        config.binpatcher.version = this.getBinpatcherVersion().filter(Util.IS_NOT_BLANK).getOrNull();
        config.binpatcher.args = this.getBinpatcherArguments().getOrNull();

        if (this.isV2()) {
            var v2 = (PatcherConfig.V2) (config = new PatcherConfig.V2(config));
            v2.spec = 2;
            v2.modules = this.getModules().get();
            if (v2.modules.isEmpty()) v2.modules = null;
            v2.processor = this.getProcessor().getOrNull();
            v2.patchesOriginalPrefix = this.getPatchesOriginalPrefix().filter(Util.IS_NOT_BLANK).getOrElse(DEFAULT_PATCHES_PREFIX_ORIGINAL);
            v2.patchesModifiedPrefix = this.getPatchesModifiedPrefix().filter(Util.IS_NOT_BLANK).getOrElse(DEFAULT_PATCHES_PREFIX_MODIFIED);
            v2.notchObf = this.getNotchObf().filter(b -> b).getOrNull();
            v2.sourceFileCharset = this.getSourceFileEncoding().filter(Util.IS_NOT_BLANK).getOrNull();
            v2.universalFilters = this.getUniversalFilters().get();
            if (v2.universalFilters.isEmpty()) v2.universalFilters = null;
            addExtraDependencies(v2);
        }

        JsonData.toJson(config, this.getOutput().getAsFile().get());
    }

    @SuppressWarnings("deprecation")
    private void addExtraDependencies(PatcherConfig.V2 v2) {
        v2.extraDependencies = new PatcherConfig.V2.ScopedDependencies();
        v2.extraDependencies.compileOnly = new ArrayList<>(getExtraCompileDeps().get());
        v2.extraDependencies.runtimeOnly = new ArrayList<>(getExtraRuntimeDeps().get());
        v2.extraDependencies.annotationProcessor = new ArrayList<>(getExtraAnnotationProcessorDeps().get());
    }

    private boolean isV2() {
        return this.getNotchObf().getOrElse(false)
            || this.getProcessor().isPresent()
            || this.getUniversalFilters().isPresent()
            || !"a/".equals(getPatchesOriginalPrefix().getOrNull())
            || !"b/".equals(getPatchesModifiedPrefix().getOrNull());
    }

    public void universal(TaskProvider<?> task) {
        this.getUniversal().set(MavenArtifact.from(task).map(MavenArtifact::getFullDescriptor));
    }

    public void sources(TaskProvider<?> task) {
        this.getSource().set(MavenArtifact.from(task).map(MavenArtifact::getFullDescriptor));
    }

    private static String depToString(Dependency value) {
        String group = value.getGroup();
        if (group == null)
            throw new IllegalArgumentException("Dependency group cannot be null");

        String version = value.getVersion();
        if (version == null)
            throw new IllegalArgumentException("Dependency version cannot be null");

        return group + ':' + value.getName() + ':' + version;
    }

    public final void addLibraries(Configuration config) {
        for (var dep : config.getAllDependencies()) {
            if (dep instanceof ProjectDependency project)
                getLibraries().add(project.getGroup() + ':' + project.getName() + ':' + project.getVersion());
            else
                getLibraries().add(dep.toString());
        }
    }

    public final void addCompileDependency(Dependency value) {
        this.getExtraCompileDeps().add(depToString(value));
    }

    public final void addCompileDependency(Provider<? extends Dependency> value) {
        this.addCompileDependency(value.get());
    }

    public final void addCompileDependency(ProviderConvertible<? extends Dependency> value) {
        this.addCompileDependency(value.asProvider());
    }

    public final void addRuntimeDependency(Dependency value) {
        this.getExtraRuntimeDeps().add(depToString(value));
    }

    public final void addRuntimeDependency(Provider<? extends Dependency> value) {
        this.addRuntimeDependency(value.get());
    }

    public final void addRuntimeDependency(ProviderConvertible<? extends Dependency> value) {
        this.addRuntimeDependency(value.asProvider());
    }

    public final void addAnnotationProcessorDependency(Dependency value) {
        this.getExtraAnnotationProcessorDeps().add(depToString(value));
    }

    public final void addAnnotationProcessorDependency(Provider<? extends Dependency> value) {
        this.addAnnotationProcessorDependency(value.get());
    }

    public final void addAnnotationProcessorDependency(ProviderConvertible<? extends Dependency> value) {
        this.addAnnotationProcessorDependency(value.asProvider());
    }

    public void runs(Action<? super NamedDomainObjectContainer<? extends RunConfig>> action) {
        var container = this.getObjects().domainObjectContainer(BeanRunConfig.class);
        action.execute(container);
        this.getRuns().set(getProject().provider(() -> {
            // .getAsMap does not actually resolve anything, so we have to manually force the resolution
            var ret = new LinkedHashMap<String, RunConfig>();
            for (var run : container)
                ret.put(run.getName(), run);
            return ret;
        }));
    }

    static abstract class BeanRunConfig extends RunConfig implements Named, Serializable {
        private static final @Serial long serialVersionUID = -7975487107404098482L;

        @Inject
        public BeanRunConfig(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }

        public void environment(String key, String value) {
            var map = this.env == null ? this.env = new HashMap<>() : this.env;
            map.put(key, value);
        }

        public void property(String key, String value) {
            var map = this.props == null ? this.props = new HashMap<>() : this.props;
            map.put(key, value);
        }

        public void args(String... args) {
            var list = this.args == null ? this.args = new ArrayList<>() : this.args;
            list.addAll(Arrays.asList(args));
        }

        public void jvmArgs(String... jvmArgs) {
            var list = this.jvmArgs == null ? this.jvmArgs = new ArrayList<>() : this.jvmArgs;
            list.addAll(Arrays.asList(jvmArgs));
        }
    }
}
