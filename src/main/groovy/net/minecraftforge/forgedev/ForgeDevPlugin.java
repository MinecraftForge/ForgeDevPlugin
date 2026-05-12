/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import groovy.transform.CompileStatic;
import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.jetbrains.annotations.VisibleForTesting;

import javax.inject.Inject;

@CompileStatic
@VisibleForTesting
public abstract class ForgeDevPlugin extends EnhancedPlugin<ExtensionAware> {
    public static final String NAME = "forgedev";
    public static final String DISPLAY_NAME = "ForgeDev";

    public static final Logger LOGGER = Logging.getLogger("ForgeDev");

    private ForgeDevExtension extension;

    @Inject
    public ForgeDevPlugin() {
        super(NAME, DISPLAY_NAME, "fdtools");
    }

    @Override
    public void setup(ExtensionAware target) {
        this.extension = target.getExtensions().create(ForgeDevExtension.NAME, ForgeDevExtension.class, this, target);
    }

    public ForgeDevExtension getExtension() {
        return extension;
    }
}
