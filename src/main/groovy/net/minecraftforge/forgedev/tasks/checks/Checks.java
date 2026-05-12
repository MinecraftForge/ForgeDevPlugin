/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import net.minecraftforge.forgedev.base.MCPBase;
import net.minecraftforge.forgedev.Util;
import net.minecraftforge.forgedev.tasks.installertools.ExtractInheritance;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

public abstract class Checks {
    private final TaskContainer tasks;
    private final TaskProvider<Task> checkAndFix;
    private MCPBase mcpBase;

    @Inject public abstract ObjectFactory getObjects();

    @Inject
    public Checks(Project project) {
        this.tasks = project.getTasks();
        this.checkAndFix = tasks.register("checkAndFix", task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
        });
    }

    public TaskProvider<Task> getFix() {
        return this.checkAndFix;
    }
    public void fix(Action<? super Task> action) {
        this.checkAndFix.configure(action);
    }

    private TaskProvider<ExtractInheritance> inheritance;
    private TaskProvider<ExtractInheritance> getInheritance() {
        if (this.inheritance == null)
            this.inheritance = tasks.register("extractInheritance", ExtractInheritance.class);
        return this.inheritance;
    }

    public <T extends CheckTask> Check<T> check(String taskName, Class<T> clazz) {
        return check(taskName, clazz, Util.noop());
    }
    public <T extends CheckTask> Check<T> check(String taskName, Class<T> clazz, Action<? super T> action) {
        taskName = StringGroovyMethods.capitalize(taskName);

        var check = tasks.register("check" + taskName, clazz, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            action.execute(task);
            task.getFix().set(false);
        });
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(check));

        var fix = tasks.register("checkAndFix" + taskName, clazz, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            action.execute(task);
            task.getFix().set(true);
        });
        this.checkAndFix.configure(task -> task.dependsOn(fix));

        @SuppressWarnings("unchecked")
        var ret = (Check<T>)this.getObjects().newInstance(Check.class, check, fix);
        return ret;
    }

    private Check<CheckATs> ats;
    public Check<CheckATs> getAts() {
        if (this.ats == null) {
            var inheritance = getInheritance().flatMap(ExtractInheritance::getOutput);
            this.ats = check("Ats", CheckATs.class, task -> task.getInheritance().set(inheritance));
            configureAts(this.mcpBase);
        }
        return ats;
    }
    public Check<CheckATs> ats() { return ats(Util.noop()); }
    public Check<CheckATs> ats(Action<CheckATs> action) {
        var ret = getAts();
        ret.check(action);
        ret.fix(action);
        return ret;
    }
    private void configureAts(@Nullable MCPBase base) {
        if (base != null && this.ats != null)
            ats(task -> task.getAts().from(base.getAccessTransformers()));
    }

    private Check<CheckExecs> execs;
    public Check<CheckExecs> getExecs() {
        if (this.execs == null) {
            this.execs = check("Execs", CheckExecs.class, task ->
                task.getBinary().set(tasks.named("jar", Jar.class).flatMap(Jar::getArchiveFile))
            );
        }
        return execs;
    }
    public Check<CheckExecs> execs() { return execs(Util.noop()); }
    public Check<CheckExecs> execs(Action<CheckExecs> action) {
        var ret = getExecs();
        ret.check(action);
        ret.fix(action);
        return ret;
    }

    private Check<CheckSAS> sas;
    public Check<CheckSAS> getSas() {
        if (this.sas == null) {
            var inheritance = getInheritance().flatMap(ExtractInheritance::getOutput);
            this.sas = check("Sas", CheckSAS.class, task -> task.getInheritance().set(inheritance));
            if (this.mcpBase != null)
                sas(task -> task.getSass().from(mcpBase.getSideAnnotationStrippers()));
        }
        return sas;
    }
    public Check<CheckSAS> sas(Action<CheckSAS> action) {
        var ret = getSas();
        ret.check(action);
        ret.fix(action);
        return ret;
    }
    private void configureSas(@Nullable MCPBase base) {
        if (base != null && this.sas != null)
            sas(task -> task.getSass().from(base.getSideAnnotationStrippers()));
    }

    private Check<CheckPatches> patches;
    public Check<CheckPatches> getPatches() {
        if (this.patches == null)
            this.patches = check("Patches", CheckPatches.class);
        return patches;
    }
    public Check<CheckPatches> patches() { return patches(Util.noop()); }
    public Check<CheckPatches> patches(Action<CheckPatches> action) {
        var ret = getPatches();
        ret.check(action);
        ret.fix(action);
        return ret;
    }

    public void setBase(MCPBase base) {
        // TODO: [ForgeDev] Support mapped AT/SAS files
        getInheritance().configure(task -> {
            task.getInput().fileProvider(base.getClassesRaw());
            task.getLibraries().setFrom(base.getDependencyConfiguration());
        });
        this.configureAts(base);
        this.configureSas(base);
    }
    public void base(MCPBase base) {
        setBase(base);
    }
}
