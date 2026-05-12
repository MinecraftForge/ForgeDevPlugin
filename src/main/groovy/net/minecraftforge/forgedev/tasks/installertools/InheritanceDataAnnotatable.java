/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installertools;

import java.util.List;

public interface InheritanceDataAnnotatable {
    List<InheritanceData.Annotation> annotations();
}
