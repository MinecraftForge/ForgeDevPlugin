/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.gradleutils.shared.SharedUtil;
import net.minecraftforge.srgutils.MinecraftVersion;
import net.minecraftforge.util.hash.HashFunction;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.HasConfigurableValue;
import org.gradle.api.specs.Spec;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public final class Util extends SharedUtil {
    private Util() { }

    private static final Logger LOGGER = Logging.getLogger(Util.class);
    public static final Spec<String> IS_NOT_BLANK = s -> !s.isBlank();

    public static String getProjectEclipseName(Project project) {
        var eclipse = project.getExtensions().findByType(EclipseModel.class);
        var name = eclipse == null ? null : eclipse.getProject().getName();
        return name != null ? name : project.getName();
    }

    public static void logFiles(Logger logger, String prefix, Collection<File> files) {
        var itr = files.iterator();
        if (itr.hasNext())
            logger.info("{}: null", prefix);
        else {
            var padding = " ".repeat(prefix.length());
            logger.info("{}: {}", prefix, itr.next().getAbsolutePath());
            while (itr.hasNext())
                logger.info("{}: {}", padding, itr.next().getAbsolutePath());
        }
    }

    public static void logArgs(Logger logger, String prefix, List<String> args) {
        var padding = " ".repeat(prefix.length());
        for (int x = 0; x < args.size(); x++) {
            var current = args.get(x);
            var next = args.size() > x + 1 ? args.get(x + 1) : null;
            var line = current;
            if (current.startsWith("--") && next != null && !next.startsWith("--")) {
                x++;
                line += ' ' + next;
            }
            logger.info("{}{}", x == 0 ? prefix : padding, line);
        }
    }

    @SuppressWarnings("unchecked")
    public static <R, E extends Throwable> R sneak(Throwable e) throws E {
        throw (E)e;
    }


    public static String kebab(String s) {
        var buf = new StringBuilder(s.length());
        for (var chr : s.toCharArray()) {
            if (chr >= 'A' && chr <= 'Z')
                buf.append('-').append((char)(chr - 'A'));
            else
                buf.append(chr);
        }
        return buf.toString();
    }

    private static final Action<?> DO_NOTHING = input -> {};
    @SuppressWarnings("unchecked")
    public static <T> Action<T> noop() {
        return (Action<T>)DO_NOTHING;
    }

    public static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1);
    }

    public static void extractZip(File source, File dest, boolean deleteExtra) {
        try {
            LOGGER.debug("Extracting {} to {}", source.getAbsolutePath(), dest.getAbsolutePath());
            Files.createDirectories(dest.toPath());
            var existing = deleteExtra ? Files.walk(dest.toPath()).filter(Files::isRegularFile).collect(Collectors.toSet()) : new HashSet<Path>();

            try (var zip = new ZipFile(source)) {
                for (var itr = zip.entries().asIterator(); itr.hasNext(); ) {
                    var entry = itr.next();
                    if (entry.isDirectory())
                        continue;

                    var target = new File(dest, entry.getName());
                    if (!existing.isEmpty())
                        existing.remove(target.toPath());
                    var data = zip.getInputStream(entry).readAllBytes();

                    // I do a hash check before writing because reading a file is faster than writing, and less destructive to hard drives.
                    var write = !target.exists() || target.length() != data.length || !HashFunction.SHA1.hash(target).equals(HashFunction.SHA1.hash(data));
                    if (!write)
                        continue;

                    LOGGER.debug("Extracting {}", entry.getName());
                    Files.createDirectories(target.getParentFile().toPath());
                    Files.write(target.toPath(), data);
                }
            }

            if (!deleteExtra)
                return;

            for (var old : existing) {
                LOGGER.debug("Deleting {}", old);
                Files.delete(old);
            }

            Files.walkFileTree(dest.toPath(), new SimpleFileVisitor<>() {
                private final LinkedList<AtomicInteger> count = new LinkedList<>(List.of(new AtomicInteger()));

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    count.getLast().incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    count.getLast().incrementAndGet();
                    count.add(new AtomicInteger());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    // Remove and check counter for this directory
                    if (count.removeLast().get() == 0) {
                        count.getLast().decrementAndGet();
                        LOGGER.debug("Delete: {}", dir);
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            sneak(e);
        }
    }

    public static String iso8601Now() {
        return iso8601(new Date());
    }

    public static String iso8601(Date self) {
        var format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        var result = format.format(self);
        return result.substring(0, 21) + ':' + result.substring(22);
    }

    public static <R extends HasConfigurableValue> R finalize(Project project, R ret) {
        ret.disallowChanges();
        ret.finalizeValueOnRead();
        return ret;
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().build();
    public static boolean checkExists(String url) {
        try {
            return HTTP.send(HttpRequest.newBuilder(new URI(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()
            ).statusCode() == 200;
        } catch (IOException | InterruptedException | URISyntaxException e) {
            if (e.toString().contains("unable to find valid certification path to requested target"))
                throw new RuntimeException("Failed to connect to $url: Missing certificate root authority, try updating Java");
            return sneak(e);
        }
    }

    private static final Predicate<String> MCP_VERSION = Pattern.compile("-\\d{8}\\.\\d{6}$").asPredicate();
    private static final MinecraftVersion UNOBFED_START = MinecraftVersion.from("26.1-snapshot-1");
    public static boolean isObfuscated(String version) {
        if (MCP_VERSION.test(version))
            version = version.substring(0, version.length() - 16);
        try {
            return MinecraftVersion.from(version).compareTo(UNOBFED_START) < 0;
        } catch (Exception e) {
            System.out.println("Failed to parse MC Version: " + version + " Defaulting to obfuscated");
            return true;
        }
    }
}
