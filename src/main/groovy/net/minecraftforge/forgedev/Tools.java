/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.gradleutils.shared.Tool;

public final class Tools {
    private Tools() { }

    // EXECUTABLE
    public static final Tool MAVENIZER = Tool.ofForge("mavenizer", "net.minecraftforge:minecraft-mavenizer:0.5.15", 25);
    public static final Tool DIFFPATCH = Tool.of("diffpatch", "io.codechicken:DiffPatch:2.1.0.42:all", Constants.MAVEN_CENTRAL, 8);
    public static final Tool BINPATCH = Tool.ofForge("binpatcher", "net.minecraftforge:binarypatcher:1.3.3:fatjar", 8);
    public static final Tool INSTALLERTOOLS = Tool.ofForge("installertools", "net.minecraftforge:installertools:1.4.4:fatjar", 8);
    public static final Tool INSTALLER = Tool.ofForge("installer", "net.minecraftforge:installer:2.2.9:fatjar", 8);
    public static final Tool SHIM = Tool.ofForge("shim", "net.minecraftforge:bootstrap-shim:2.1.8", 8);
    public static final Tool JARCOMPATIBILITYCHECKER = Tool.ofForge("jarcompatibilitychecker", "net.minecraftforge:JarCompatibilityChecker:0.1.28:all", 8);
    public static final Tool SLIMELAUNCHER = Tool.ofForge("slimelauncher", "net.minecraftforge:slime-launcher:0.1.8", 8, "net.minecraftforge.launcher.Main");
    public static final Tool GITVERSION = Tool.ofForge("gitversion", "net.minecraftforge:gitversion:0.8.0:fatjar", 17);

    // LIBRARIES
    public static final Tool FASTCSV = Tool.of("fastcsv", "de.siegmar:fastcsv:3.7.0", Constants.MAVEN_CENTRAL, 11);
}
