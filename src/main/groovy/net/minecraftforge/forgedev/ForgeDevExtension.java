/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.forgedev.base.MCPBase;
import net.minecraftforge.forgedev.base.MCPBaseImpl;
import net.minecraftforge.forgedev.patches.BinaryPatches;
import net.minecraftforge.forgedev.patches.BinaryPatchesImpl;
import net.minecraftforge.forgedev.patches.Patches;
import net.minecraftforge.forgedev.patches.PatchesImpl;
import net.minecraftforge.forgedev.publishvalidate.ValidatePublish;
import net.minecraftforge.forgedev.runs.Run;
import net.minecraftforge.forgedev.tasks.DownloadHashedFile;
import net.minecraftforge.forgedev.tasks.MethodCallFinder;
import net.minecraftforge.forgedev.tasks.ValidateDeprecations;
import net.minecraftforge.forgedev.tasks.checks.Checks;
import net.minecraftforge.forgedev.tasks.compat.UserdevCompatibility;
import net.minecraftforge.forgedev.tasks.compat.UserdevCompatibilityImpl;
import net.minecraftforge.forgedev.tasks.installer.Installer;
import net.minecraftforge.forgedev.tasks.shim.Shim;
import net.minecraftforge.forgedev.tasks.userdev.UserDev;
import net.minecraftforge.forgedev.values.CIRuntime;
import net.minecraftforge.forgedev.values.GitVersionValueSource;
import net.minecraftforge.forgedev.values.MavenArtifact;
import net.minecraftforge.forgedev.values.MinecraftFiles;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

// TODO [ForgeDev] Hide this and make a public interface
@VisibleForTesting
public abstract class ForgeDevExtension {
    public static final String NAME = "forgedev";

    private static final Attribute<String> OS = Attribute.of("net.minecraftforge.native.operatingSystem", String.class);
    private static final Attribute<String> MAPPINGS_CHANNEL = Attribute.of("net.minecraftforge.mappings.channel", String.class);
    private static final Attribute<String> MAPPINGS_VERSION = Attribute.of("net.minecraftforge.mappings.version", String.class);

    protected abstract @Inject ProjectLayout getProjectLayout();
    protected abstract @Inject ProviderFactory getProviders();
    protected abstract @Inject ObjectFactory getObjects();

    private final ForgeDevPlugin plugin;
    private final Project project;

    @Inject
    public ForgeDevExtension(ForgeDevPlugin plugin, Project project) {
        this.plugin = plugin;
        this.project = project;

        // Note: A MAJOR design factor of this extension is that EVERYTHING is opt-in.
        // Nothing is added to the project by applying this extension
        // Almost every field **should** be lazily initialized and grouped with their specific region.
        // Typically right above their get method.
        // If Java had a way to do fields in interfaces, this would be split into multiple interfaces around each region.
        // But we don't so giant file it is!

        this.mavenizerRepo.set(plugin.globalCaches().dir("repo").map(getProblems().ensureFileLocation()));
    }

    public ForgeDevPlugin getPlugin() {
        return this.plugin;
    }

    // region Mavenizer Stuff ==============================================
    // NOTE: Pass into RepositoryHandler#maven
    private final DirectoryProperty mavenizerRepo = this.getObjects().directoryProperty();
    public Action<? super MavenArtifactRepository> getMavenizer() {
        return repo -> {
            repo.setName("Mavenizer");
            repo.setUrl(this.getMavenizerRepo());
        };
    }

    @VisibleForTesting
    public DirectoryProperty getMavenizerRepo() {
        return this.mavenizerRepo;
    }
    // endregion ===========================================================

    // region Helpers that don't belong else ware ==========================
    private final ForgeDevProblems problems = this.getObjects().newInstance(ForgeDevProblems.class);
    public ForgeDevProblems getProblems() {
        return this.problems;
    }

    private final Provider<Boolean> isCi = getProviders().of(CIRuntime.class, it -> {});
    public boolean isCi() {
        return this.isCi.get();
    }

    private final Provider<String> os = getProviders().of(OSValueSource.class, it -> {});
    public String getOs() {
        return this.os.get();
    }

    public MavenArtifact getMavenArtifact() {
        return mavenArtifact(this.project);
    }
    public MavenArtifact mavenArtifact(Project project) {
        return MavenArtifact.from(project);
    }
    public MavenArtifact mavenArtifact(String descriptor) {
        return MavenArtifact.from(this.getObjects(), descriptor);
    }
    public Provider<MavenArtifact> mavenArtifact(Provider<?> provider) {
        return MavenArtifact.from(this.getObjects(), provider);
    }

    // Replaces the need for the FilterNew tasks, we just want to remove the vanilla classes from our jar
    public Spec<FileTreeElement> filterVanilla(Provider<File> vanilla) {
        var blacklist = new HashSet<String>();
        try (var zip = new ZipFile(vanilla.get())) {
            for (var itr = zip.entries().asIterator(); itr.hasNext(); ) {
                var entry = itr.next();
                blacklist.add(entry.getName());
            }
        } catch (IOException e) {
            return Util.sneak(e);
        }
        return cfg -> {
            if (cfg.isDirectory())
                return true;

            var path = cfg.getRelativePath().getPathString();
            if (blacklist.contains(path))
                return true;

            if (!path.endsWith(".class"))
                return false;

            if (path.startsWith("mcp/"))
                return true;

            int idx = path.lastIndexOf('$');
            while (idx != -1) {
                if (blacklist.contains(path.substring(0, idx) + ".class"))
                    return true;
                idx = path.lastIndexOf('$', idx - 1);
            }

            return false;
        };
    }
    // endregion ===========================================================

    // Tasks creation helpers

    // region Crowdin ======================================================
    // Downloads the crowdin zip, which is created nightly by a cron job
    // =====================================================================
    private @Nullable TaskProvider<DownloadHashedFile> downloadCrowdin;
    public TaskProvider<DownloadHashedFile> getCrowdin() {
        if (this.downloadCrowdin == null) {
            this.downloadCrowdin = project.getTasks().register("downloadCrowdin", DownloadHashedFile.class);
            this.downloadCrowdin.configure(task -> {
                task.setDescription("Download crowdin based translations");
                task.getSrc().set("https://files.minecraftforge.net/crowdin.zip");
            });
        }
        return this.downloadCrowdin;
    }
    public TaskProvider<DownloadHashedFile> crowdin(Action<? super DownloadHashedFile> action) {
        getCrowdin().configure(action);
        return getCrowdin();
    }
    // endregion ===========================================================

    // region Run Configs ==================================================
    // Adds genEclipseRuns task and SlimeLauncherExec task for each run config
    // =====================================================================
    private @Nullable TaskProvider<Task> genEclipseRuns;
    public TaskProvider<Task> getGenEclipseRuns() {
        if (this.genEclipseRuns == null) {
            genEclipseRuns = project.getTasks().register("genEclipseRuns", task -> {
                task.setGroup("IDE");
                task.setDescription("Generates the run configuration launch files for Eclipse.");
            });
        }
        return this.genEclipseRuns;
    }

    private NamedDomainObjectContainer<Run> runs;
    public NamedDomainObjectContainer<? extends Run> getRuns() {
        if (this.runs == null) {
            this.runs = this.getObjects().domainObjectContainer(Run.class, name -> {
                var run = getObjects().newInstance(Run.class, name, getProblems(), this.project, getGenEclipseRuns());
                if (this.base != null) {
                    run.getCache().set(
                        getPlugin().globalCaches().dir(
                            "slime-launcher/cache/%s".formatted(this.getMcpBase().getMcpVersion().get())
                        ).map(this.getProblems().ensureFileLocation())
                    );
                    run.metadata(this.getMcpBase().getMetadata());
                }
                return run;
            });
        }
        return this.runs;
    }
    public NamedDomainObjectContainer<? extends Run> runs(Action<NamedDomainObjectContainer<? extends Run>> action) {
        action.execute(getRuns());
        return getRuns();
    }
    // endregion ===========================================================

    // region Validate Artifacts ===========================================
    // Publish validation task, the intention of this is to provide a diff of what this version and the last released version
    // actually publish, so that we can know what actually changes between versions.
    // Mainly this is for validating converting to ForgeDev from ForgeGradle
    // =====================================================================
    private ValidatePublish.@Nullable Consumer validatePublish = null;
    public ValidatePublish.Consumer getValidatePublish() {
        if (validatePublish == null)
            validatePublish = ValidatePublish.registerConsumer(this.project, this);
        return validatePublish;
    }
    public ValidatePublish.Consumer validatePublish(Action<ValidatePublish.Consumer> action) {
        var ret = getValidatePublish();
        action.execute(ret);
        return ret;
    }
    // endregion ===========================================================

    // region Version Numbers ==============================================
    // Get version numbers for projects from git
    // =====================================================================
    public Provider<String> gitVersion() {
        return gitVersion(Util.noop());
    }
    public Provider<String> gitVersion(Action<GitVersionValueSource.Parameters> action) {
        return GitVersionValueSource.provider(this.getProviders(), this.plugin, action);
    }
    public Provider<String> gitVersion(Project project) {
        return gitVersion(project, Util.noop());
    }
    public Provider<String> gitVersion(Project project, Action<GitVersionValueSource.Parameters> action) {
        return gitVersion(params -> {
            params.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            action.execute(params);
        });
    }
    public Provider<String> gitVersion(Project project, String commit) {
        return gitVersion(project, commit, Util.noop());
    }
    public Provider<String> gitVersion(Project project, String commit, Action<GitVersionValueSource.Parameters> action) {
        return gitVersion(params -> {
            params.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            params.getCommit().set(commit);
            action.execute(params);
        });
    }
    // endregion ===========================================================

    // region Vanilla Files ================================================
    // Access to vanilla files, such as the client/server jar, version json
    // =====================================================================
    private final Map<String, MinecraftFiles> minecraftFiles = new HashMap<>();
    public MinecraftFiles minecraftFiles(String version) {
        var ret = this.minecraftFiles.get(version);
        if (ret == null) {
            ret = this.getObjects().newInstance(MinecraftFiles.class, this.project, this.plugin, version);
            this.minecraftFiles.put(version, ret);
        }
        return ret;
    }
    // endregion ===========================================================

    // region Patcher Base =================================================
    // This is the 'base' of a patcher project. It should provide the 'clean'
    // jar that we generate our patches against.
    // This should also provide basically anything we need from the 'base'
    // MCPConfig setup. Libraries, Mappings, client-extra,
    // =====================================================================
    private MCPBaseImpl base;
    public MCPBase getMcpBase() {
        if (this.base == null)
            this.base = getObjects().newInstance(MCPBaseImpl.class, this.project, this.plugin, "mcpbase");
        return base;
    }
    public MCPBase mcpBase(Action<? super MCPBase> action) {
        var ret = this.getMcpBase();
        action.execute(ret);
        return ret;
    }
    // endregion ===========================================================

    // region Patches ======================================================
    // This is for configuring how we patch raw source files.
    // Typically, we only have one but might as well support multiple.
    // =====================================================================
    private final Map<String, PatchesImpl> patches = new HashMap<>();
    public Patches getPatches(String name) {
        var ret = this.patches.get(name);
        if (ret == null) {
            ret = getObjects().newInstance(PatchesImpl.class, this, name, this.project);
            this.patches.put(name, ret);
        }
        return ret;
    }
    public Patches getPatches() {
        return getPatches(Patches.DEFAULT_NAME);
    }
    public Patches patches(String name, Action<? super Patches> action) {
        var ret = this.getPatches(name);
        action.execute(ret);
        return ret;
    }
    public Patches patches(Action<? super Patches> action) {
        return patches(Patches.DEFAULT_NAME, action);
    }
    // endregion ===========================================================

    // region Installer ====================================================
    // =====================================================================
    private final Map<String, Installer> installers = new HashMap<>();
    public Installer getInstaller() {
        return installer(Installer.DEFAULT_NAME);
    }
    public Installer installer(Action<Installer> action) {
        return installer(Installer.DEFAULT_NAME, action);
    }
    public Installer installer(String name) {
        var ret = this.installers.get(name);
        if (ret == null) {
            ret = Installer.register(this.project, this, name);
            this.installers.put(name, ret);
        }
        return ret;
    }
    public Installer installer(String name, Action<Installer> action) {
        var ret = installer(name);
        action.execute(ret);
        return ret;
    }
    // endregion ===========================================================

    // region Checks =======================================================
    // 'Check' and 'Fix' tasks, Registers the same task twice with a 'fix'
    // boolean input. Registers the 'check{Name}' version to the 'check' group
    // and the 'checkAndFix{Name}' task to the 'checkAndFix' group.
    // =====================================================================
    private Checks checks;
    public Checks getChecks() {
        if (checks == null)
            checks = this.getObjects().newInstance(Checks.class, this.project);
        return checks;
    }
    public Checks checks(Action<Checks> action) {
        var ret = getChecks();
        action.execute(ret);
        return ret;
    }
    // endregion ===========================================================

    // region Shim Dedicated Server Luncher ================================
    // =====================================================================
    public Shim shim() {
        return shim(Util.noop());
    }
    public Shim shim(Action<Shim> action) {
        return shim(Shim.DEFAULT_NAME, action);
    }
    public Shim shim(String name) {
        return shim(name, Util.noop());
    }
    public Shim shim(String name, Action<Shim> action) {
        var ret = Shim.register(this.project, this, name);
        action.execute(ret);
        return ret;
    }
    // endregion ===========================================================

    // region Bytecode Finder ==============================================
    public TaskProvider<MethodCallFinder> methodCallFinder(String name) {
        return methodCallFinder(name, Util.noop());
    }
    public TaskProvider<MethodCallFinder> methodCallFinder(String name, Action<MethodCallFinder> action) {
        return this.project.getTasks().register(name, MethodCallFinder.class, action);
    }
    // endregion ===========================================================

    // region UserDev ======================================================
    // =====================================================================
    private @Nullable UserDev userDev;
    public UserDev getUserDev() {
        if (userDev == null)
            this.userDev = this.getObjects().newInstance(UserDev.class, "userdev", this.project, this);
        return userDev;
    }
    public UserDev userDev(Action<UserDev> action) {
        action.execute(getUserDev());
        return getUserDev();
    }
    // endregion ===========================================================

    // region Published Artifacts Validation ===============================
    // Validates the published artifacts for the entire project tree.
    // This is basically the gradelized version of our 'dump.sh' script
    // Not Fully implemented
    // =====================================================================
    public TaskProvider<ValidateDeprecations> validateDeprecations() {
        return validateDeprecations(this.project.getTasks().named("jar", Jar.class));
    }
    public TaskProvider<ValidateDeprecations> validateDeprecations(TaskProvider<? extends AbstractArchiveTask> jar) {
        var tasks = this.project.getTasks();
        var ret = tasks.register("validate" + Util.capitalize(jar.getName()) + "Deprecations", ValidateDeprecations.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.getInput().set(jar.flatMap(AbstractArchiveTask::getArchiveFile));
        });
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, check -> check.dependsOn(ret));
        return ret;
    }
    public TaskProvider<ValidateDeprecations> validateDeprecations(TaskProvider<? extends AbstractArchiveTask> jar, Action<ValidateDeprecations> action) {
        var ret = validateDeprecations(jar);
        ret.configure(action);
        return ret;
    }
    // endregion ===========================================================

    // region Compatibility checking =======================================
    // For now, this is just a creation helper so we don't have to reference the hard object and can it accepts groovy closures correctly
    private final Map<String, UserdevCompatibility> userdevCompatibility = new HashMap<>();
    public UserdevCompatibility userdevCompatibility(String minecraftVersion) {
        var ret = this.userdevCompatibility.get(minecraftVersion);
        if (ret == null) {
            ret = getObjects().newInstance(UserdevCompatibilityImpl.class, this.project, minecraftVersion, userdevCompatibility.isEmpty());
            this.userdevCompatibility.put(minecraftVersion, ret);
        }
        return ret;
    }
    public UserdevCompatibility userdevCompatibility(String minecraftVersion, Action<? super UserdevCompatibility> action) {
        var ret = userdevCompatibility(minecraftVersion);
        action.execute(ret);
        return ret;
    }
    // endregion ===========================================================

    // region Binary Patches ===============================================
    private final Map<String, BinaryPatches> binaryPatches = new HashMap<>();
    public BinaryPatches binaryPatches(String name) {
        var ret = this.binaryPatches.get(name);
        if (ret == null) {
            ret = getObjects().newInstance(BinaryPatchesImpl.class, name, this, this.project.getTasks());
            if (this.base != null) {
                ret.apply(task -> {
                    task.getClean().setFrom(this.base.getClasses());
                });
                ret.create(task -> {
                    task.getClean().setFrom(this.base.getClasses());
                    if (Util.isObfuscated(this.base.getMcpVersion().get())) {
                        task.getSrg().setFrom(this.base.getMap2Srg());
                        task.getReverseSrg().set(true);
                    }
                    task.getSas().setFrom(this.base.getSideAnnotationStrippers());
                    task.getDirty().setFrom(this.project.getTasks().named("jar"));
                });
            }

            var patches = this.patches.get(Patches.DEFAULT_NAME);
            if (patches != null) {
                ret.create(task -> {
                    task.mustRunAfter(patches.getMake());
                    task.getPatches().from(patches.getPatches());
                });
            }

            this.binaryPatches.put(name, ret);
        }
        return ret;
    }
    public BinaryPatches binaryPatches(String name, Action<? super BinaryPatches> action) {
        var ret = binaryPatches(name);
        action.execute(ret);
        return ret;
    }
    // endregion ===========================================================

    // region Sharing Files Between projects ===============================
    // Following 'best practices' defined in https://docs.gradle.org/current/userguide/how_to_share_outputs_between_projects.html
    private static final Attribute<String> FILE_TYPE = Attribute.of("net.minecraftforge.gradle.file.type", String.class);
    private final Set<String> files = new HashSet<>();
    public void provideFile(String name, Object file) {
        var cfg = this.project.getConfigurations().consumable("provider" + Util.capitalize(name), c ->
            c.attributes(attr -> attr.attribute(FILE_TYPE, name))
        );
        this.project.getArtifacts().add(cfg.getName(), file);
    }

    private final Map<String, Provider<ResolvableConfiguration>> consumers = new HashMap<>();
    public Provider<ResolvableConfiguration> consumeFile(String name, Project project) {
        var cfgName = "consumer" + Util.capitalize(project.getName()) + Util.capitalize(name);
        return consumers.computeIfAbsent(cfgName, prefix -> {
            var deps = this.project.getConfigurations().dependencyScope(prefix + "Dependencies");
            var projectDep = this.project.getDependencyFactory().createProjectDependency(project.getPath());
            this.project.getDependencies().add(deps.getName(), projectDep);

            return this.project.getConfigurations().resolvable(prefix, cfg -> {
                cfg.extendsFrom(deps.get());
                cfg.attributes(attr -> attr.attribute(FILE_TYPE, name));
            });
        });
    }
    // endregion ===========================================================
}
