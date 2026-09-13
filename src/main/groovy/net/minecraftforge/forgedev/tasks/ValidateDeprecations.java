/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks;

import net.minecraftforge.srgutils.MinecraftVersion;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@DisableCachingByDefault(because = "Validation tasks shouldn't be cached")
public abstract class ValidateDeprecations extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getInput();
    @Input public abstract Property<String> getMcVersion();

    @TaskAction
    protected void exec() throws IOException {
        var mcVer = MinecraftVersion.from(getMcVersion().get());
        var errors = new ArrayList<String[]>();

        try (var zin = new ZipInputStream(new FileInputStream(getInput().getAsFile().get()))) {
            for (ZipEntry ze; (ze = zin.getNextEntry()) != null; ) {
                if (!ze.getName().endsWith(".class"))
                    continue;

                var classNode = new ClassNode();
                new ClassReader(zin).accept(classNode, 0);
                processNode(mcVer, errors, classNode);
            }
        }

        if (!errors.isEmpty()) {
            for (var error : errors)
                this.getLogger().error("Deprecated {} is marked for removal in {} but is not yet removed", error[0], error[1]);
            throw new IllegalStateException("Found deprecated members marked for removal but not yet removed in " + mcVer + "; see log for details");
        }
    }

    private static void processNode(MinecraftVersion mcVer, List<String[]> errors, ClassNode node) {
        processAnnotations(node.visibleAnnotations, mcVer, errors, "class " + node.name);
        if (node.fields != null) {
            for (var field : node.fields)
                processAnnotations(field.visibleAnnotations, mcVer, errors, "field " + node.name + '#' + field.name);
        }
        if (node.methods != null) {
            for (var method : node.methods)
                processAnnotations(method.visibleAnnotations, mcVer, errors, "method " + node.name + '#' + method.name + method.desc);
        }
    }

    private static void processAnnotations(List<AnnotationNode> annotations, MinecraftVersion mcVer, List<String[]> errors, String marker) {
        if (annotations == null) return;
        for (var annotation : annotations)
            processAnnotations(annotation, mcVer, errors, marker);
    }
    private static void processAnnotations(AnnotationNode annotation, MinecraftVersion mcVer, List<String[]> errors, String marker) {
        var values = annotation.values;
        if (values == null) return;

        int forRemoval = values.indexOf("forRemoval");
        int since = values.indexOf("since");
        if ("Ljava/lang/Deprecated;".equals(annotation.desc) && forRemoval != -1 && since != -1 && values.size() >= 4 && (Boolean)values.get(forRemoval + 1) == true) {
            var oldVersion = MinecraftVersion.from(values.get(since + 1).toString());
            int[] split = splitDots(oldVersion.toString());
            if (split.length < 2) return;
            var removeVersion = MinecraftVersion.from(split[0] + "." + (split[1] + 1));
            if (removeVersion.compareTo(mcVer) <= 0)
                errors.add(new String[]{marker, removeVersion.toString()});
        }
    }

    private static int[] splitDots(String version) {
        var pts = version.split("\\.");
        var values = new int[pts.length];
        for (int x = 0; x < pts.length; x++)
            values[x] = Integer.parseInt(pts[x]);
        return values;
    }
}
