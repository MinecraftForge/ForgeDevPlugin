/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serial;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Eclipse config files are literally just java properties, with the header cleaned up.
 * <p>This does the same thing, as well as sorting alphabetically. It also ignores all comments. We can add them later
 * if someone cares.</p>
 *
 * @see <a
 * href="https://github.com/eclipse/buildship/blob/5b2c7fca7fa86cd74d71b3c099c7b1559eba038e/org.eclipse.buildship.core/src/main/java/org/eclipse/buildship/core/internal/configuration/PreferenceStore.java#L238">PreferenceStore.java:238
 * from Buildship</a>
 */
public class CleanProperties extends Properties {
    private static final @Serial long serialVersionUID = 1L;

    private static final String LINE_SEP = System.lineSeparator();
    private static final String UNIX_LINE_SEP = "\n";
    private static final Charset ENCODING = StandardCharsets.UTF_8;

    public CleanProperties load(File input) throws IOException {
        if (input.exists()) {
            try (Reader is = new InputStreamReader(new FileInputStream(input), ENCODING)) {
                super.load(is);
            }
        }

        return this;
    }

    public void store(File out) throws IOException {
        if (!out.getParentFile().exists() && !out.getParentFile().mkdirs())
            throw new IllegalStateException("Cannot create parent directory for " + out);

        try (OutputStream os = new FileOutputStream(out)) {
            store(os, null);
        }
    }

    @Override
    public synchronized Enumeration<Object> keys() {
        var ret = new TreeSet<>(Comparator.comparing(Object::toString));
        super.keys().asIterator().forEachRemaining(ret::add);
        return Collections.enumeration(ret);
    }

    @Override
    public SortedSet<Map.Entry<Object, Object>> entrySet() {
        var ret = new TreeSet<Map.Entry<Object, Object>>(Comparator.comparing(e -> e.getKey().toString()));
        ret.addAll(super.entrySet());
        return Collections.synchronizedSortedSet(ret);
    }

    @Override
    public void store(OutputStream out, String comments) throws IOException {
        out.write(clean().getBytes(ENCODING));
        out.flush();
    }

    @Override
    public void store(Writer out, String comments) throws IOException {
        out.write(clean());
        out.flush();
    }

    private String clean() throws IOException {
        var tmp = new ByteArrayOutputStream();
        try (tmp) {
            super.store(tmp, null);
        }

        var ret = tmp.toString(ENCODING).replace(LINE_SEP, UNIX_LINE_SEP);
        ret = ret.substring(ret.indexOf(UNIX_LINE_SEP) + 1);
        return ret;
    }
}
