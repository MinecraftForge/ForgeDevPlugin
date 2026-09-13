/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@DisableCachingByDefault(because = "Validation tasks are not cacheable")
public abstract class CheckTask extends DefaultTask implements VerificationTask {
    @Input public abstract Property<Boolean> getFix();
    private final Property<Boolean> ignoreFailures = getObjects().property(Boolean.class).convention(false);

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public CheckTask() {
        setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
    }

    @Override
    public boolean getIgnoreFailures() {
        return this.ignoreFailures.getOrElse(false);
    }

    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures.set(ignoreFailures);
    }

    @TaskAction
    protected void run() throws Exception {
        var name = getName();
        var logger = getLogger();

        var doFix = getFix().get();
        var reporter = getObjects().newInstance(Reporter.class);
        check(reporter, doFix);

        if (!reporter.messages.isEmpty()) {
            if (!doFix) {
                logger.error("Check task '{}' found errors:\n{}", name, String.join("\n", reporter.messages));
                if (!getIgnoreFailures())
                    throw new IllegalArgumentException(reporter.messages.size() + " errors were found");
            } else {
                if (logger.isEnabled(LogLevel.DEBUG))
                    logger.warn("Check task '{}' found {} errors and fixed {}:\n{}", name, reporter.messages.size(), reporter.fixed.size(), String.join("\n", reporter.fixed));
                else
                    logger.warn("Check task '{}' found {} errors and fixed {}.", name, reporter.messages.size(), reporter.fixed.size());

                if (!reporter.notFixed.isEmpty()) {
                    logger.error("{} errors could not be fixed:\n{}", reporter.notFixed.size(), String.join("\n", reporter.notFixed));
                    if (!getIgnoreFailures())
                        throw new IllegalArgumentException(reporter.notFixed.size() + " errors which cannot be fixed were found!");
                }
            }
        }
    }

    protected abstract void check(Reporter reporter, boolean fix) throws Exception;

    public static abstract class Reporter {
        public final List<String> messages = new ArrayList<>();

        public final List<String> fixed = new ArrayList<>();
        public final List<String> notFixed = new ArrayList<>();

        public void report(String message) {
            report(message, true);
        }

        public void report(String message, boolean canBeFixed) {
            messages.add(message);
            (canBeFixed ? fixed : notFixed).add(message);
        }
    }
}
