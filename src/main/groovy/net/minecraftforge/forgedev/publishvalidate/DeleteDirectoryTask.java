/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.publishvalidate;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Objects;

public abstract class DeleteDirectoryTask extends DefaultTask {
    @Internal
    abstract RegularFileProperty getDirectory();

    public DeleteDirectoryTask() {
    }

    @TaskAction
    public void exec() {
        var dir = getDirectory().getAsFile().get();
        delete(dir);
    }

    private void delete(File dir) {
        var files = dir.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isDirectory())
                    delete(file);
                else
                    file.delete();
            }
        }
        dir.delete();
    }
}
