/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import net.minecraftforge.forgedev.tasks.installertools.InheritanceData;
import net.minecraftforge.forgedev.tasks.installertools.InheritanceDataAnnotatable;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@DisableCachingByDefault(because = "Validation tasks dont have outputs")
public abstract class CheckSAS extends CheckTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getInheritance();
    @InputFiles @PathSensitive(PathSensitivity.NONE) public abstract ConfigurableFileCollection getSass();
    @Input public abstract Property<String> getAnnotation();

    @Inject
    public CheckSAS() {
        this.getAnnotation().convention("Lnet/minecraftforge/api/distmarker/OnlyIn;");
    }

    @Override
    public void check(Reporter reporter, boolean fix) throws IOException {
        var inheritance = InheritanceData.parse(this.getInheritance().getAsFile().get());

        for (var file : this.getSass().getFiles()) {
            var lines = new ArrayList<String>();

            for (var line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isEmpty() && line.charAt(0) == '\t') return; // Skip any tabbed lines, those are ones we add

                var idx = line.indexOf('#');
                if (idx == 0 || line.isEmpty()) {
                    lines.add(line);
                    return;
                }
                var parsed = Line.of(line);
                var clsInh = inheritance.get(parsed.cls);

                if (clsInh == null) {
                    reporter.report("Invalid: " + line);
                } else if (parsed.name.isEmpty()) { //Class SAS
                    var toAdd = new ArrayList<String>();
                    var sided = isSided(clsInh);

                    /* TODO: MergeTool doesn't do fields
                    if (json[cls]['fields'] != null) {
                        for (entry in json[cls]['fields']) {
                            if (isSided(entry.value)) {
                                sided = true
                                toAdd.add('\t' + cls + ' ' + entry.key)
                            }
						}
                    } */

                    if (clsInh.methods() != null) {
                        for (var entry : clsInh.methods().entrySet()) {
                            var key = entry.getKey();
                            if (isSided(entry.getValue())) {
                                sided = true;
                                toAdd.add('\t' + parsed.cls + ' ' + key.replaceAll(" ", ""));
                            }

                            if (sided) {
                                for (var child : findChildMethods(inheritance, parsed.cls, key)) {
                                    lines.add("\t" + child);
                                    getLogger().lifecycle(line + " -- " + child);
                                }
                            }
                        }
                    }

                    if (sided) {
                        lines.add(parsed.cls + (parsed.comment == null ? "" : " " + parsed));
                        Collections.sort(toAdd);
                        lines.addAll(toAdd);
                    } else {
                        reporter.report("Invalid: " + line);
                    }

                } else if (parsed.desc.isEmpty()) { // Fields
                    /* TODO: MergeTool doesn't do fields
                    if (json[cls]['fields'] != null && isSided(json[cls]['fields'][name]))
                        lines.add(cls + ' ' + name + (comment == null ? '' : ' ' + comment))
                    else */
                    reporter.report("Invalid: " + line);
                } else { // Methods
                    var key = parsed.name + ' ' + parsed.desc;
                    if (clsInh.methods() == null || !isSided(clsInh.methods().get(key)))
                        reporter.report("Invalid: " + line);
                    else {
                        lines.add(parsed.cls + ' ' + parsed.name + parsed.desc + (parsed.comment == null ? "" : ' ' + parsed.comment));

                        for (var child : findChildMethods(inheritance, parsed.cls, key)) {
                            lines.add("\t" + child);
                            getLogger().lifecycle(line + " -- " + child);
                        }
                    }
                }
            }

            if (fix)
                Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
        }
    }

    private boolean isSided(InheritanceDataAnnotatable annotatable) {
        if (annotatable == null)
            return false;
        var desc = this.getAnnotation().get();
        for (var ann : annotatable.annotations()) {
            if (desc.equals(ann.desc()))
                return true;
        }
        return false;
    }

    private Set<String> findChildMethods(Map<String, InheritanceData> json, String cls, String desc) {
        var ret = new TreeSet<String>();
        for (var value : json.values()) {
            if (value.methods() == null) continue;
            var mtd = value.methods().get(desc);
            if (mtd != null && cls.equals(mtd.override()) && !isSided(mtd))
                ret.add(value.name() + ' ' + desc.replace(" ", ""));
        }
        return ret;
    }

    private record Line(
        String cls, String name, String desc, String comment
    ) {
        private static Line of(String line) {
            var idx = line.indexOf('#');
            String comment = null;
            if (idx != -1) {
                comment = line.substring(idx);
                line = line.substring(0, idx - 1);
            }
            var pts = line.trim().replace("(", " (").split(" ");
            var cls = pts[0].replace('.', '/');
            return new Line(
                cls,
                pts.length > 1 ? pts[1] : "",
                pts.length > 2 ? pts[2] : "",
                comment
            );
        }
    }
}
