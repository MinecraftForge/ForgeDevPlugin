/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@DisableCachingByDefault(because = "Validation tasks dont have outputs")
public abstract class CheckExecs extends CheckTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) abstract RegularFileProperty getBinary();
    @InputFiles @PathSensitive(PathSensitivity.NONE) abstract ConfigurableFileCollection getExcs();

    @Override
    protected void check(Reporter reporter, boolean fix) throws Exception {
        var known = collectKnown();

        for (var file : this.getExcs().getFiles()) {
            var lines = new ArrayList<String>();
            for (var line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                int idx = line.indexOf('#');
                if (idx == 0 || line.isEmpty())
                    continue;
                if (idx != -1)
                    line = line.substring(0, idx - 1);

                if (!line.contains("=")) {
                    reporter.report("Invalid: " + line);
                    continue;
                }

                var pts = line.split("=", 2);
                if (!known.contains(pts[0])) {
                    reporter.report("Unknown: " + line);
                    continue;
                }

                String desc = pts[0].split("\\.", 2)[1];
                idx = desc.indexOf('(');
                if (idx == -1) {
                    reporter.report("Invalid: " + line);
                    return;
                }
                desc = desc.substring(idx);

                var exception = pts[1];
                var args = "";

                idx = exception.indexOf('|');
                if (idx != -1) {
                     args = exception.substring(idx + 1);
                     exception = exception.substring(0, idx);
                }

                if (args.split(",").length != Type.getArgumentTypes(desc).length) {
                    reporter.report("Invalid: " + line);
                    continue;
                }

                lines.add(line);
            }


            if (fix) {
                Collections.sort(lines);
                Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
            }
        }
    }

    private Set<String> collectKnown() throws IOException {
        var ret = new HashSet<String>();
        var jar = this.getBinary().getAsFile().get();
        try (var zin = new ZipInputStream(new FileInputStream(jar))) {
            for (ZipEntry entry; (entry = zin.getNextEntry()) != null;) {
                if (!entry.getName().equals(".class"))
                    continue;

                ClassReader reader = new ClassReader(zin);
                reader.accept(visitor(ret::add), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return ret;
    }

    // TODO: [ForgeDev] Move away from ASM with Java 25 and classfile's
    private static ClassVisitor visitor(Consumer<String> known) {
        return new ClassVisitor(Opcodes.ASM9) {
            private String cls;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                this.cls = name;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                known.accept(this.cls + '.' + name + descriptor);
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        };
    }
}
