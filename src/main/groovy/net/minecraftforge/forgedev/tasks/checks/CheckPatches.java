/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DisableCachingByDefault(because = "Validation tasks dont have outputs")
public abstract class CheckPatches extends CheckTask {
    private static final Pattern HUNK_START_PATTERN = Pattern.compile("^@@ -[0-9,]* \\+[0-9,_]* @@$");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("^[+\\-]\\s*$");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^[+\\-]\\s*import.*");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^[+\\-][\\s]*((public|protected|private)[\\s]*)?(static[\\s]*)?(final)?([^=;]*)(=.*)?;\\s*$");
    private static final Pattern METHOD_PATTERN = Pattern.compile("^[+\\-][\\s]*((public|protected|private)[\\s]*)?(static[\\s]*)?(final)?([^(]*)[(]([^)]*)?[)]\\s*[{]\\s*$");
    private static final Pattern CLASS_PATTERN = Pattern.compile("^[+\\-][\\s]*((public|protected|private)[\\s]*)?(static[\\s]*)?(final[\\s]*)?(class|interface)([^{]*)[{]\\s*$");
    /*
    private static final Map<String, Integer> ACCESS_MAP = new HashMap<>(Map.of(
        "private", 0,
        null, 1,
        "protected", 2,
        "public", 3
    ));
    */

    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) abstract DirectoryProperty getPatchDir();
    @Input @Optional abstract ListProperty<String> getPatchesWithS2SArtifact();

    @Override
    public void check(Reporter reporter, boolean fix) throws IOException {
        var dir = getPatchDir().getAsFile().get().toPath();
        var s2sBugged = this.getPatchesWithS2SArtifact().get();

        try (var stream = Files.walk(dir)) {
            for (var path : stream.toList()) {
                if (!Files.isRegularFile(path))
                    return;

                var relativeName = dir.relativize(path).toString();
                verifyPatch(path, reporter, fix, relativeName, s2sBugged.contains(relativeName.replace('\\', '/')));
            }
        }
    }

    private static boolean accessChange(String previous, String current) {
        //return ACCESS_MAP[previous] < ACCESS_MAP[current]
        return !previous.equals(current);
    }

    void verifyPatch(Path patch, Reporter reporter, boolean fix, String patchPath, boolean hasS2SArtifact) throws IOException {
        var oldFixedErrors = reporter.fixed.size();
        var lines = Files.readAllLines(patch);
        var hunksStart = 0;
        var onlyWhiteSpace = false;
        var newLines = new ArrayList<String>();

        // First two lines are file name ++/-- and we do not care
        newLines.add(lines.get(0) + '\n');
        newLines.add(lines.get(1) + '\n');

        int i;
        for (i = 2; i < lines.size(); ++i) {
            var line = lines.get(i);
            newLines.add(line + '\n');

            if (HUNK_START_PATTERN.matcher(line).find()) {
                if (onlyWhiteSpace) {
                    if (!hasS2SArtifact)
                        reporter.report("Patch contains only white space hunk starting at line " + (hunksStart + 1) + ", file: " + patchPath);
                    int toRemove = i - hunksStart;
                    while (toRemove-- > 0)
                        newLines.remove(newLines.size() - 1);
                }
                hunksStart = i;
                onlyWhiteSpace = true;
                continue;
            }

            if (line.startsWith("+") || line.startsWith("-")) {
                var prefixChange = false;
                var prevLine = lines.get(i - 1);

                if (line.charAt(0) == '+' && prevLine.charAt(0) == '-') {
                    var prevTrim = prevLine.substring(1).replaceAll("\\s", "");
                    var currTrim = line.substring(1).replaceAll("\\s", "");

                    if (prevTrim.equals(currTrim))
                        prefixChange = true;

                    // 2: access
                    // 3: static
                    // 4: final

                    // 5: field name
                    // 6: initializer
                    if (check(FIELD_PATTERN.matcher(prevLine), FIELD_PATTERN.matcher(line)))
                        reporter.report("Patch contains access changes or final removal at line " + (i + 1) + ", file: " + patchPath, false);

                     // 5: type/ret/name
                     // 6: params
                    if (check(METHOD_PATTERN.matcher(prevLine), METHOD_PATTERN.matcher(line)))
                        reporter.report("Patch contains access changes or final removal at line " + (i + 1) + ", file: " + patchPath, false);

                     // 5: class | interface
                     // 6: name extends ...
                    if (check(CLASS_PATTERN.matcher(prevLine), CLASS_PATTERN.matcher(line)))
                        reporter.report("Patch contains access changes or final removal at line " + (i + 1) + ", file: " + patchPath, false);
                }

                if (line.charAt(0) == '-' && i + 1 < lines.size()) {
                    var nextLine = lines.get(i + 1);
                    if (nextLine.charAt(0) == '+') {
                        var nextTrim = nextLine.substring(1).replaceAll("\\s", "");
                        var currTrim = line.substring(1).replaceAll("\\s", "");

                        if (nextTrim.equals(currTrim))
                            prefixChange = true;
                    }
                }

                var isWhiteSpaceChange = WHITESPACE_PATTERN.matcher(line).find();

                if (!prefixChange && !isWhiteSpaceChange) {
                    onlyWhiteSpace = hasS2SArtifact && IMPORT_PATTERN.matcher(line).find();
                } else if (isWhiteSpaceChange) {
                    var prevLineChange = prevLine.startsWith("+") || prevLine.startsWith("-");
                    var nextLine = i + 1 < lines.size() ? lines.get(i + 1) : null;
                    var nextLineChange = nextLine != null && (nextLine.startsWith("+") || nextLine.startsWith("-"));

                    if (!prevLineChange && !nextLineChange)
                        reporter.report("Patch contains white space change in valid hunk at line " + (i + 1) + ", file: " + patchPath + '\n' + prevLine + '\n' + line + '\n' + nextLine, false);
                }

                if (line.contains("\t")) {
                    reporter.report("Patch contains tabs on line " + (i + 1) + ", file: " + patchPath);
                    line = line.replaceAll("\t", "    ");
                    newLines.remove(newLines.size() - 1);
                    newLines.add(line + '\n');
                }

                if (IMPORT_PATTERN.matcher(line).find() && !hasS2SArtifact)
                    reporter.report("Patch contains import change on line " + (i + 1) + ", file: " + patchPath, false);
            }
        }

        if (onlyWhiteSpace) {
            if (!hasS2SArtifact)
                reporter.report("Patch contains only white space hunk starting at line " + (hunksStart + 1) + ", file: " + patchPath);
            var toRemove = i - hunksStart;
            while (toRemove-- > 0)
                newLines.remove(newLines.size() - 1);
        }

        if ((reporter.fixed.size() > oldFixedErrors && fix) || hasS2SArtifact) {
            if (newLines.size() <= 2) {
                getLogger().lifecycle("Patch is now empty removing, file: {}", patchPath);
                Files.delete(patch);
            } else {
                if (!hasS2SArtifact)
                    getLogger().lifecycle("*** Updating patch file. Please run setup then genPatches again. ***");
                Files.writeString(patch, String.join("", newLines), StandardCharsets.UTF_8);
            }
        }
    }

    private boolean check(Matcher pMatcher, Matcher cMatcher) {
        return (pMatcher.find() && cMatcher.find() &&
            pMatcher.group(6).equals(cMatcher.group(6)) && // = ...
            pMatcher.group(5).equals(cMatcher.group(5)) && // field name
            pMatcher.group(3).equals(cMatcher.group(3)) && // static
            (accessChange(pMatcher.group(2), cMatcher.group(2)) || !pMatcher.group(4).equals(cMatcher.group(4))));
    }
}
