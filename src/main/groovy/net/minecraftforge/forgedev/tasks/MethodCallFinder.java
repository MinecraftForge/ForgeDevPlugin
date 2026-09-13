/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.util.data.json.JsonData;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@CacheableTask
public abstract class MethodCallFinder extends DefaultTask implements SingleFileOutput {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getJar();
    @Input public abstract Property<Boolean> getAllowEmpty();
    @Input public abstract SetProperty<String> getBlacklist();
    @Input public abstract Property<MethodReference> getTarget();
    // It should be fine to mark the output as internal as we want to control when we run it any ways.
    // This also shuts Gradle 8 up about implicit task dependencies.
    @Internal @Override public abstract RegularFileProperty getOutput();

    @Inject protected abstract ObjectFactory getObjects();

    public record MethodReference (int opcode, String name, String desc) implements Serializable {}

    @Inject
    public MethodCallFinder() {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().file(getName() + "/output.json"));
        this.getAllowEmpty().convention(false);
    }

    public void blacklist(String cls) {
        this.getBlacklist().add(cls);
    }

    public void invokeVirtual(String name, String desc) {
        call(Opcodes.INVOKEVIRTUAL, name, desc);
    }
    public void invokeSpecial(String name, String desc) {
        call(Opcodes.INVOKESPECIAL, name, desc);
    }
    public void invokeStatic(String name, String desc) {
        call(Opcodes.INVOKESTATIC, name, desc);
    }
    public void invokeInterface(String name, String desc) {
        call(Opcodes.INVOKEINTERFACE, name, desc);
    }
    private void call(int opcode, String name, String desc) {
        this.getTarget().set(new MethodReference(opcode, name, desc));
    }

    @TaskAction
    public void exec() throws IOException {
        var output = this.getOutput().getAsFile().get();
        Files.deleteIfExists(output.toPath());

        var data = new TreeMap<String, List<String>>(Comparator.naturalOrder());
        var blacklist = this.getBlacklist().get();
        var target = this.getTarget().get();
        var allowEmpty = this.getAllowEmpty().get();

        try (var zin = new ZipInputStream(new FileInputStream(getJar().getAsFile().get()))) {
            for (ZipEntry ze; (ze = zin.getNextEntry()) != null; ) {
                if (!ze.getName().endsWith(".class"))
                    continue;

                var classNode = new ClassNode();
                new ClassReader(zin).accept(classNode, 0);

                if (blacklist.contains(classNode.name) || classNode.methods == null)
                    continue;

                for (var methodNode : classNode.methods) {
                    // only add non-synthetic methods
                    if (!allowEmpty && (methodNode.access & Opcodes.ACC_SYNTHETIC) != 0)
                        continue;

                    for (var insn : methodNode.instructions) {
                        if (insn.getOpcode() != target.opcode || !(insn instanceof MethodInsnNode mtd))
                            continue;
                        if (mtd.name.equals(target.name) && mtd.desc.equals(target.desc)) {
                            var list = data.computeIfAbsent(classNode.name, k -> new ArrayList<>());
                            // allow us to add empty entries when we hit a synthetic method
                            // this is an old implementation bug kept for diff compatibility
                            // Guarded here for performance reason
                            if ((methodNode.access & Opcodes.ACC_SYNTHETIC) != 0)
                                break;

                            list.add(methodNode.name + methodNode.desc);

                            break;
                        }
                    }
                }
            }
        }

        var ret = new ArrayList<Map<String, Object>>();
        for (var entry : data.entrySet()) {
            var map = new LinkedHashMap<String, Object>();
            map.put("class", entry.getKey());
            map.put("methods", entry.getValue());
            ret.add(map);
        }

        if (ret.isEmpty())
            throw new RuntimeException("Failed to find any targets, please ensure that method names and descriptors are correct.");

        // Indent 4 spaces like Groovy does, makes diffs easier
        var string = new StringWriter();
        var jsonWriter = GSON.newJsonWriter(string);
        jsonWriter.setIndent("    ");
        GSON.toJson(ret, ret.getClass(), jsonWriter);

        Files.writeString(output.toPath(), string.toString(), StandardCharsets.UTF_8);
    }
}
