/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.shim;

import net.minecraftforge.forgedev.CleanProperties;
import net.minecraftforge.forgedev.tasks.SingleFileOutput;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import javax.inject.Inject;
import java.io.IOException;

public abstract class ShimConfig extends DefaultTask implements SingleFileOutput {
    public static final String DEFAULT_FILE_NAME = "bootstrap-shim.properties";
    @Input public abstract Property<String> getMainClass();
    @Input public abstract Property<JavaLanguageVersion> getJavaVersion();
    @Input public abstract ListProperty<String> getArgs();

    @OutputFile @Override public abstract RegularFileProperty getOutput();

    @Inject public abstract ProjectLayout getLayout();

    @Inject
    public ShimConfig() {
        var fileName = DEFAULT_FILE_NAME;
        if (!getName().equals(Shim.DEFAULT_NAME + "Config"))
            fileName = getName() + ".properties";
        this.getOutput().convention(getLayout().getBuildDirectory().file("libs/" + fileName));
        if (this.getProject().getPluginManager().hasPlugin("java"))
            this.getJavaVersion().convention(this.getProject().getExtensions().findByType(JavaPluginExtension.class).getToolchain().getLanguageVersion());
    }

    @TaskAction
    protected void exec() throws IOException {
        var cfg = new CleanProperties();
        cfg.put("Main-Class", getMainClass().get());
        cfg.put("Java-Version", getJavaVersion().get().toString());
        cfg.put("Arguments", String.join(" ", getArgs().get()));
        cfg.store(getOutput().getAsFile().get());
    }
}
