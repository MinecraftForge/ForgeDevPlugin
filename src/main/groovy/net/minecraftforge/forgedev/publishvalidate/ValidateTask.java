/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.publishvalidate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.forgedev.Constants;
import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.legacy.values.MavenInfo;
import net.minecraftforge.forgedev.values.MavenArtifact;
import net.minecraftforge.util.download.DownloadUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@DisableCachingByDefault(because = "Validate tasks have no outputs")
public abstract class ValidateTask extends DefaultTask {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();
    public abstract @InputFiles @PathSensitive(PathSensitivity.NONE) ConfigurableFileCollection getFiles();
    public abstract @Input MapProperty<String, String> getBaseVersions();
    public abstract @Input Property<String> getBaseBranch();
    public abstract @Optional @Input Property<String> getVersionPrefix();
    public abstract @Internal DirectoryProperty getCacheDirectory();

    protected abstract @Inject ObjectFactory getObjects();

    private final ForgeDevExtension extension;

    @Inject
    public ValidateTask(ForgeDevExtension extension) {
        this.extension = extension;
        this.getCacheDirectory().convention(getProject().getLayout().getBuildDirectory().dir(getName()));
    }

    @TaskAction
    public void exec() throws IOException {
        getLogger().lifecycle("Executing ValidateTask");
        for (var dir : getFiles()) {
            getLogger().lifecycle("Dir: {}", dir.getAbsoluteFile());
            var base = extension.gitVersion( parameters -> {
                parameters.getCommit().set(getBaseBranch());
                // {project}/build/validate-publish -> {project}
                parameters.getProjectDirectory().set(dir.getParentFile().getParentFile());
            }).get();
            if (getVersionPrefix().isPresent())
                base = getVersionPrefix().get() + '-' + base;
            getLogger().lifecycle("  Base Version: {}", base);

            var files = new ArrayList<File>();
            try (var stream = Files.walk(dir.toPath())) {
                stream.filter(Files::isRegularFile).map(Path::toFile).forEach(files::add);
            }

            // Gather all artifacts that this project has published. This **should** only be one artifact. But might as well make it generic
            var artifacts = new HashMap<MavenArtifact, Map<String, File>>();
            for (var file : files) {
                if (isMetadata(file))
                    continue;

                var info = MavenArtifact.from(getObjects(), dir, file);
                getLogger().lifecycle("File: {}", info.getDescriptor());
                var key = MavenArtifact.from(getObjects(), info.group() + ':' + info.name() + ':' +  info.version());
                var suffix = (info.classifier() == null ? "" : '-' + info.classifier()) + '.' + info.extension();
                artifacts.computeIfAbsent(key, k -> new HashMap<>()).put(suffix, file);
            }

            var cacheDir = getCacheDirectory().getAsFile().get();

            for (var entry : artifacts.entrySet()) {
                var artifact = entry.getKey();
                var baseVersion = artifact.withVersion(base);
                var expected = getKnown(baseVersion);
                getLogger().lifecycle("  Known:    {}", entry.getValue().keySet());
                getLogger().lifecycle("  Expected: {}", expected);

                for (var e2 : entry.getValue().entrySet()) {
                    var suffix = e2.getKey();
                    var file = e2.getValue();
                    var path = baseVersion.getDirectory() + '/' + baseVersion.name() + '-' + baseVersion.version() + suffix;
                    var target = new File(cacheDir, path);
                    if (!target.exists())
                        DownloadUtils.downloadFile(target, Constants.FORGE_MAVEN + path);
                    compare(file,  artifact.version(), target, baseVersion.version());
                }
            }
        }
    }

    private Set<String> getKnown(MavenArtifact artifact) {
        var url = Constants.FORGE_FILES + artifact.getDirectory() + "/meta.json";
        var jsonStr = DownloadUtils.tryDownloadString(url);
        if (jsonStr == null) {
            getLogger().lifecycle("  No metadata file found at {}", url);
            return Set.of();
        }

        var meta = GSON.<Map<String, Map<String, Map<String, String>>>>fromJson(jsonStr, new TypeToken<>(){}.getType());
        var ret = new HashSet<String>();
        for (var e : meta.get("classifiers").entrySet()) {
            for (var ext : e.getValue().keySet())
                ret.add((e.getKey().isEmpty() ? "" : '-' + e.getKey()) + '.' + ext);
        }
        return ret;
    }

    private static final Set<String> BLACKLIST = Set.of("md5", "sha1", "sha256", "sha512", "pom", "module");
    private static boolean isMetadata(File path) {
        var name = path.getName();
        var ext = name.substring(name.lastIndexOf('.') + 1);
        return BLACKLIST.contains(ext) || name.startsWith("maven-metadata.xml");
    }

    private void compare(File current, String currentVersion, File base, String baseVersion) {

    }

    private Map<String, byte[]> loadZip(File file) {
        return null;
    }
}
