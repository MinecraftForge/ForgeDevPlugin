/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.compat;

import net.minecraftforge.forgedev.Constants;
import net.minecraftforge.forgedev.tasks.MergeJars;
import net.minecraftforge.forgedev.tasks.DownloadHashedFile;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import net.minecraftforge.forgedev.tasks.patching.binary.ApplyBinPatches;
import net.minecraftforge.forgedev.values.LatestForgeVersion;
import net.minecraftforge.forgedev.values.MavenArtifact;
import net.minecraftforge.util.data.json.JsonData;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

public abstract class UserdevCompatibilityImpl implements UserdevCompatibility {
    private final Provider<String> baseForgeVersion;
    private final TaskProvider<ApplyBinPatches> apply;
    private final TaskProvider<CheckJarCompatibility> check;

    protected abstract @Inject ProviderFactory getProviders();
    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public UserdevCompatibilityImpl(Project project, String minecraftVersion, boolean first) {
        var group = "Compatibility";
        var TASK_NAME = "checkJarCompatibility" + (first ? "" : minecraftVersion.replace(".", ""));
        var buildDir = project.getLayout().getBuildDirectory();

        this.baseForgeVersion = project.getProviders().of(LatestForgeVersion.class, cfg -> {
            cfg.parameters(p -> {
                p.getOffline().set(project.getGradle().getStartParameter().isOffline());
                p.getCacheFile().set(project.getLayout().getBuildDirectory().file("promotions_slim.json"));
                p.getMinecraftVersion().set(minecraftVersion);
            });
        });

        var downloadUserdev = project.getTasks().register(TASK_NAME + "DownloadUserdev", DownloadHashedFile.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by downloading the latest available UserDev.");
            task.setGroup(group);

            task.getSrc().set(baseForgeVersion.map(ver ->  "https://maven.minecraftforge.net/net/minecraftforge/forge/" + ver + "/forge-" + ver + "-userdev.jar"));
            task.getOutput().set(baseForgeVersion.flatMap(ver -> buildDir.file(TASK_NAME + "/forge-" + ver + "-userdev.jar")));
        });

        var extractBinPatches = project.getTasks().register(TASK_NAME + "ExtractBinPatches", ExtractUserdevBinPatches.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by extracting the binary patches from the latest available UserDev.");
            task.setGroup(group);

            task.getInput().set(downloadUserdev.flatMap(DownloadHashedFile::getOutput));
            task.getOutput().set(baseForgeVersion.flatMap(ver -> buildDir.file(TASK_NAME + "/forge-" + ver + " -binpatches.lzma")));
        });

        this.apply = project.getTasks().register(TASK_NAME + "ApplyBinPatches", ApplyBinPatches.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by applying the base jar's binary patches from the latest available UserDev.");
            task.setGroup(group);

            //task.getClean().setFrom(task.getProject().getTasks().named("rawJoinedJarSrg", MavenizerRawArtifact.class).flatMap(MavenizerRawArtifact::getOutput));
            task.getApply().setFrom(extractBinPatches.flatMap(SingleFileOutput::getOutput));
            task.getOutput().set(baseForgeVersion.flatMap(ver -> buildDir.file(TASK_NAME + "/forge-" + ver + " -binpatched.jar")));
        });

        var downloadBaseForgeUniversal = project.getTasks().register(TASK_NAME + "DownloadUniversal", DownloadHashedFile.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by downloading the latest available universal JAR.");
            task.getSrc().set(downloadUserdev.flatMap(SingleFileOutput::getOutput).map(this::findUniversalUrl));
            task.getOutput().set(baseForgeVersion.flatMap(ver -> buildDir.file(TASK_NAME + "/forge-" + ver + "-universal.jar")));
        });

        // Pulled out so the lambda doesn't capture 'this'
        var applyOutput = apply.flatMap(ApplyBinPatches::getOutput);
        var universal = downloadBaseForgeUniversal.flatMap(DownloadHashedFile::getOutput);
        var mergeBaseForgeJar = project.getTasks().register(TASK_NAME + "Merge", MergeJars.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by merging the universal JAR with the binary patched JAR.");
            task.setGroup(group);

            task.getInputJars().from(applyOutput, universal);
        });

        this.check = project.getTasks().register(TASK_NAME, CheckJarCompatibility.class, task -> {
            var main = task.getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME);
            var jar = task.getProject().getTasks().named("jar", Jar.class);
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Checks the JAR compatibility between the built JAR and the latest available JAR.");
            task.onlyIf(t -> baseForgeVersion.isPresent());

            task.getBaseJar().set(mergeBaseForgeJar.flatMap(MergeJars::getOutput));
            task.getCommonLibraries().setFrom(main.get().getCompileClasspath());
            task.getInputJar().set(jar.flatMap(Jar::getArchiveFile));
        });

        var providers = project.getProviders();
        var hasMaven = providers.environmentVariable("MAVEN_USER").isPresent() && providers.environmentVariable("MAVEN_PASSWORD").isPresent();
        var checkCompatibility = providers.gradleProperty("net.minecraftforge.forge.build.check.compatibility").map(Boolean::parseBoolean).getOrElse(false);

        if (!hasMaven && checkCompatibility)
            project.getTasks().named("check", task -> task.dependsOn(check));
    }

    private String findUniversalUrl(RegularFile userdev) {
        var file = userdev.getAsFile();
        try (var zip = new ZipFile(file)) {
            var entry = zip.getEntry("config.json");
            if (entry == null)
                throw new RuntimeException("No config.json found in " + file.getAbsolutePath());
            var cfg = JsonData.patcherConfig(zip.getInputStream(entry));

            // Technically universal can be null, but that shouldn't ever happen
            var universal = MavenArtifact.from(getObjects(), cfg.universal);
            return Constants.FORGE_MAVEN + universal.getPath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find universal url from userdev jar: " + file.getAbsolutePath(), e);
        }
    }

    @Override
    public Provider<String> getBaseVersion() {
        return baseForgeVersion;
    }

    @Override
    public TaskProvider<CheckJarCompatibility> getCheck() {
        return this.check;
    }
    @Override
    public TaskProvider<CheckJarCompatibility> check(Action<? super CheckJarCompatibility> action) {
        getCheck().configure(action);
        return getCheck();
    }


    @Override
    public void setClean(RegularFileProperty clean) {
        this.apply.configure(task -> task.getClean().setFrom(clean));
    }
    @Override
    public void setClean(Provider<File> clean) {
        this.apply.configure(task -> task.getClean().setFrom(clean));
    }

    @Override
    public void setDirty(TaskProvider<?> taskProvider) {
        this.check.configure(task -> {
            task.getInputJar().fileProvider(taskProvider.map(t -> {
                if (t instanceof AbstractArchiveTask jar)
                    return jar.getArchiveFile().get().getAsFile();
                return t.getOutputs().getFiles().getSingleFile();
            }));
        });
    }

    @Override
    public void setDirty(Provider<File> provider) {
        this.check.configure(task -> task.getInputJar().fileProvider(provider));
    }
}
