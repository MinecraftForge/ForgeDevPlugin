/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.values;

import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

public abstract class CIRuntime implements ValueSource<Boolean, ValueSourceParameters.None> {
    @Override
    public Boolean obtain() {
        var env = System.getenv();
        return env.containsKey("CI")
            || env.containsKey("TEAMCITY_VERSION");
    }
}
