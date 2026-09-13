/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import net.minecraftforge.forgedev.tasks.installertools.InheritanceData;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

@DisableCachingByDefault(because = "No output files")
public abstract class CheckATs extends CheckTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getInheritance();
    @InputFiles @PathSensitive(PathSensitivity.NONE) public abstract ConfigurableFileCollection getAts();
    @InputFile @PathSensitive(PathSensitivity.NONE) @Optional public abstract RegularFileProperty getMappings();

    @Override
    protected void check(Reporter reporter, boolean fix) throws Exception {
        var inheritance = InheritanceData.parse(this.getInheritance().get().getAsFile());
        for (var at : getAts().getFiles()) {
            var parsed = process(at, reporter, inheritance);
            if (fix) {
                var mappings = IMappingFile.load(getMappings().getAsFile().get());
                var lines = joinBack(parsed, inheritance, mappings);
                Files.writeString(at.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
            }
        }
    }

    public void mappings(Provider<?> provider) {

    }

    protected ATFile process(File file, Reporter reporter, Map<String, InheritanceData> inheritance) throws IOException {
        ATFile ret = ATFile.parse(this.getObjects(), file);
        for (var entry : ret.errors) {
            for (var error : entry.errors)
                reporter.report(error, false);
        }

        final Map<String, ATFile.Group> constructorGroups = new TreeMap<>();

        var toRemove = new HashSet<String>();
        for (var itr = ret.getEntries().entrySet().iterator(); itr.hasNext(); ) {
            var next = itr.next();
            String key = next.getKey();
            var entry = next.getValue();
            if (entry == null) continue;

            for (var error : entry.errors)
                reporter.report(error, false);

            var binaryName = entry.cls.replace('.', '/');
            var jcls = inheritance.get(binaryName);
            if (jcls == null) {
                itr.remove();
                reporter.report("Invalid Entry, Missing class: " + key);
                continue;
            }

            var group = entry.asGroup();
            // Process Groups, this will remove any entries outside the group that is covered by the group
            if (group != null) {
                if ("*".equals(entry.desc)) {
                    if (jcls.fields() == null || jcls.fields().isEmpty()) {
                        itr.remove();
                        reporter.report("Invalid group, class has no fields: " + key);
                    } else {
                        handleGroup(reporter, ret, entry.cls, toRemove, group, jcls.fields(), InheritanceData.Field::access);
                    }
                } else if ("*()".equals(entry.desc)) {
                    if (jcls.methods() == null || jcls.methods().isEmpty()) {
                        itr.remove();
                        reporter.report("Invalid group, class has no methods: " + key);
                    } else {
                        handleGroup(reporter, ret, entry.cls, toRemove, group, jcls.methods(), InheritanceData.Method::access);
                    }
                } else if ("<init>".equals(entry.desc)) { //Make all public non-abstract subclasses
                    constructorGroups.put(binaryName, group);
                }
            } else if (entry.desc.isEmpty()) { // Class
                if (strength(jcls.access()) > entry.strength() && !entry.isForced()) {
                    itr.remove();
                    reporter.report("Invalid Narrowing: " + key);
                }
            } else if (!entry.desc.contains("(")) { // Field
                if (jcls.fields() == null || !jcls.fields().containsKey(entry.desc)) {
                    itr.remove();
                    reporter.report("Invalid: " + key);
                } else {
                    var value = jcls.fields().get(entry.desc);
                    if (strength(value.access()) > entry.strength() && !entry.isForced()) {
                        itr.remove();
                        reporter.report("Invalid Narrowing: " + key);
                    }
                }
            } else { // Methods
                // Inheritance uses spaces, so add it in
                var jdesc = entry.desc.replace("(", " (");
                if (jcls.methods() == null || !jcls.methods().containsKey(jdesc)) {
                    itr.remove();
                    reporter.report("Invalid: $key");
                } else {
                    var value = jcls.methods().get(jdesc);
                    if (strength(value.access()) > entry.strength() && !entry.isForced()) {
                        itr.remove();
                        reporter.report("Invalid Narrowing: " + key);
                    }
                }
            }
        }

        // Make all Constructors for subclasses of our target public
        for (var entry : inheritance.entrySet()) {
            var tcls = entry.getKey();
            var value = entry.getValue();
            if (value.methods() == null || value.methods().isEmpty() || ((value.access() & Opcodes.ACC_ABSTRACT) != 0))
                continue;
            String parent = tcls;
            while (parent != null) {
                var group = constructorGroups.get(parent);
                if (group != null) {
                    for (var mentry : value.methods().entrySet()) {
                        if (!mentry.getKey().startsWith("<init>"))
                            continue;

                        var child = tcls.replace('/', '.') + ' ' + mentry.getKey().replace(" ", "");
                        if (strength(mentry.getValue().access()) < 3) {
                            if (ret.getEntries().containsKey(child))
                                toRemove.add(child);
                            else if (!group.existing.contains(child))
                                reporter.report("Missing group entry: " + child);
                            group.children.add(child);
                        } else if (ret.getEntries().containsKey(child)) {
                            toRemove.add(child);
                            reporter.report("Found invalid group entry: " + child);
                        }
                    }
                }

                var iparent = inheritance.get(parent);
                parent = iparent == null ? null : iparent.superName();
            }
        }

        for (var entry : constructorGroups.values()) {
            for (var existing : entry.existing) {
                if (!entry.children.contains(existing))
                    reporter.report("Found invalid group entry: " + existing);
            }
        }

        for (var entry : toRemove)
            ret.getEntries().remove(entry);

        return ret;
    }

    private <M> void handleGroup(
        Reporter reporter, ATFile ret, String cls, Set<String> toRemove,
        ATFile.Group group, Map<String, M> members, ToIntFunction<M> toAccess) {
        for (var entry : members.entrySet()) {
            var ikey = entry.getKey();
            if (ikey.startsWith("<clinit>") || ikey.startsWith("lambda$")) continue;
            // Inheritance data uses space between name and desc, we don't
            var key = cls + ' ' + ikey.replace(" ", "");

            if (strength(toAccess.applyAsInt(entry.getValue())) < group.strength()) {
                toRemove.add(key);
                if (!group.existing.contains(key))
                    reporter.report("Missing group entry: " + key);
                group.children.add(key);
            } else if (ret.getEntries().containsKey(key)) {
                toRemove.add(key);
                reporter.report("Found ungrouped group entry: " + key);
            }
        }
        for (var existing : group.existing) {
            if (!group.children.contains(existing))
                reporter.report("Found extra group entry: " + existing);
        }
    }

    private static int strength(int access) {
        if ((access & Opcodes.ACC_PUBLIC)    != 0) return 3;
        if ((access & Opcodes.ACC_PROTECTED) != 0) return 2;
        if ((access & Opcodes.ACC_PRIVATE)   != 0) return 0;
        return 1;
    }

    private List<String> joinBack(ATFile at, Map<String, InheritanceData> inheritance, IMappingFile mappings) {
        var data = new ArrayList<String>();

        for (var entry : at.getEntries().entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            var group = value.asGroup();

            if (group == null) {
                add(data, value.modifier + ' ' + value.key, remapComment(mappings, inheritance, value));
            } else {
                add(data, "#group " + value.modifier + ' ' + key, group.comment);
                for (var child : group.children) {
                    var childEntry = this.getObjects().newInstance(ATFile.Entry.class, value.modifier + ' ' + child);
                    add(data, value.modifier + ' ' + child, remapComment(mappings, inheritance, childEntry));
                }
                data.add("#endgroup");
            }
        }

        return data;
    }

    private static void add(List<String> lst, String line, String comment) {
        if (comment != null)
            lst.add((line + ' ' + comment).trim());
        else
            lst.add(line.trim());
    }

    private static String remapComment(IMappingFile mappings, Map<String, InheritanceData> inheritance, ATFile.Entry entry) {
        if (entry.desc == null || entry.desc.isEmpty())
            return null;
        var comment = entry.comment != null ? entry.comment.substring(1).trim() : null;
        var jsonCls = inheritance.get(entry.cls.replace('.', '/'));
        var mapCls = mappings.getClass(jsonCls.name());
        if (mapCls == null)
            return entry.comment;

        var idx = entry.desc.indexOf('(');

        var mappedName = idx == -1
                ? mapCls.remapField(entry.desc)
                : mapCls.remapMethod(entry.desc.substring(0, idx), entry.desc.substring(idx));

        if (mappedName == null || mappedName.isEmpty())
            return entry.comment;

        if ("<init>".equals(mappedName))
            mappedName = "constructor";

        if (comment == null)
            return "# " + mappedName;

        if (comment.startsWith(mappedName))
            return "# " + comment;

        if (comment.indexOf(' ') != -1) {
            var split = new ArrayList<>(List.of(comment.split( " - ")));
            // The first string is more than one word, so append before it
            if (split.get(0).indexOf(' ') != -1)
                return "# " + mappedName + " - " + comment;

            split.remove(0);
            return "# " + mappedName + " - " + String.join(" - ", split);
        }

        return "# " + mappedName;
    }
}
