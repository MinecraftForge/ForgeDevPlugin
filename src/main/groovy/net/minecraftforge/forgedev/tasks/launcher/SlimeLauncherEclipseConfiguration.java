/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.launcher;

import net.minecraftforge.forgedev.ForgeDevProblems;
import net.minecraftforge.forgedev.ForgeDevTask;
import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.Util;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// This is mostly taken from ForgeGradle 6 but slimmed down to what we need
public abstract class SlimeLauncherEclipseConfiguration extends DefaultTask implements ForgeDevTask {
    public abstract @OutputFile RegularFileProperty getOutputFile();

    protected abstract @Input Property<String> getProjectName();

    protected abstract @Input Property<String> getSourceSetName();

    protected abstract @Input @Optional Property<String> getEclipseProjectName();

    protected abstract @Input Property<String> getRunName();

    protected abstract @Nested Property<JavaLauncher> getJavaLauncher();

    protected abstract @Input ListProperty<String> getProjectDependencies();

    protected abstract @InputFiles @Classpath ConfigurableFileCollection getClasspath();

    protected abstract @Input Property<String> getMainClass();

    protected abstract @Nested Property<SlimeLauncherOptions> getOptions();

    public abstract @Internal DirectoryProperty getCacheDir();

    public abstract @InputFiles ConfigurableFileCollection getMetadata();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProviderFactory getProviders();

    protected abstract @Inject ProjectLayout getProjectLayout();

    protected abstract @Inject WorkerExecutor getWorkerExecutor();

    final ForgeDevProblems problems = this.getObjects().newInstance(ForgeDevProblems.class);

    @Inject
    public SlimeLauncherEclipseConfiguration() {
        this.getProjectName().convention(this.getProject().getName());
        this.getEclipseProjectName().convention(getProject().provider(() -> Util.getProjectEclipseName(this.getProject())));

        var tool = this.getTool(Tools.SLIMELAUNCHER);
        this.getClasspath().from(tool.getClasspath());
        this.getMainClass().set(tool.getMainClass());
        this.getJavaLauncher().set(Util.launcherFor(getProject(),tool.getJavaVersion()));
    }

    @TaskAction
    protected void exec() {
        List<String> args;
        List<String> jvmArgs;
        MapProperty<String, String> environment;
        DirectoryProperty workingDir;

        var options = ((SlimeLauncherOptionsInternal) this.getOptions().get()).inherit(Map.of(), this.getSourceSetName().get());

        args = new ArrayList<>(options.getArgs().getOrElse(List.of()));
        jvmArgs = new ArrayList<>(options.getJvmArgs().getOrElse(List.of()));
        if (!options.getClasspath().isEmpty())
            this.getClasspath().setFrom(options.getClasspath());
        if (options.getMinHeapSize().filter(Util.IS_NOT_BLANK).isPresent())
            jvmArgs.add("-Xms" + options.getMinHeapSize().get());
        if (options.getMaxHeapSize().filter(Util.IS_NOT_BLANK).isPresent())
            jvmArgs.add("-Xmx" + options.getMaxHeapSize().get());
        for (var property : options.getSystemProperties().getOrElse(Map.of()).entrySet())
            jvmArgs.add("-D" + property.getKey() + '=' + property.getValue());
        environment = options.getEnvironment();
        workingDir = options.getWorkingDir();
        //endregion

        //region Slime Launcher setup
        args.addAll(0, List.of("--main", options.getMainClass().get(),
            "--cache", this.getCacheDir().get().getAsFile().getAbsolutePath(),
            "--metadata", this.getMetadata().getSingleFile().getAbsolutePath(),
            "--"));

        try {
            Files.createDirectories(workingDir.get().getAsFile().toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //endregion

        var queue = this.getWorkerExecutor().classLoaderIsolation();

        queue.submit(Action.class, parameters -> {
            parameters.getOutputFile().set(this.getOutputFile());
            parameters.getEclipseProjectName().set(this.getEclipseProjectName().orElse(this.getProjectName()));
            parameters.getProjectDependencies().set(this.getProjectDependencies());
            parameters.getClasspath().setFrom(this.getClasspath());
            parameters.getMainClass().set(this.getMainClass().get());
            parameters.getArgs().set(args);
            parameters.getJvmArgs().set(jvmArgs);
            parameters.getWorkingDir().set(workingDir);
            parameters.getEnvironment().set(environment);
            parameters.getJavaHome().set(this.getJavaLauncher().map(j -> j.getMetadata().getInstallationPath()));
            parameters.getJavaVersion().set(this.getJavaLauncher().map(j -> j.getMetadata().getLanguageVersion().toString()));
        });
    }

    static abstract class Action implements WorkAction<Action.Parameters> {
        interface Parameters extends WorkParameters {
            RegularFileProperty getOutputFile();

            Property<String> getEclipseProjectName();

            ListProperty<String> getProjectDependencies();

            ConfigurableFileCollection getClasspath();

            Property<String> getMainClass();

            ListProperty<String> getArgs();

            ListProperty<String> getJvmArgs();

            DirectoryProperty getWorkingDir();

            DirectoryProperty getJavaHome();

            Property<String> getJavaVersion();

            MapProperty<String, String> getEnvironment();
        }

        @Inject
        public Action() { }

        @Override
        public void execute() {
            var parameters = getParameters();

            DocumentBuilder documentBuilder;
            Transformer transformer;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                transformer = TransformerFactory.newInstance().newTransformer();
            } catch (ParserConfigurationException | TransformerConfigurationException e) {
                throw new RuntimeException(e);
            }

            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            var launch = documentBuilder.newDocument();
            var rootElement = launch.createElement("launchConfiguration");

            rootElement.setAttribute("type", "org.eclipse.jdt.launching.localJavaApplication");
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.PROJECT_ATTR", parameters.getEclipseProjectName().get());
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.MAIN_TYPE", parameters.getMainClass().get());
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.VM_ARGUMENTS", String.join(" ", parameters.getJvmArgs().get()));
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.PROGRAM_ARGUMENTS", String.join(" ", parameters.getArgs().get()));
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.WORKING_DIRECTORY", parameters.getWorkingDir().getAsFile().get().getAbsolutePath());
            //stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.JRE_CONTAINER", parameters.getJavaHome().getAsFile().get().getAbsolutePath());
            mapAttribute(launch, rootElement, "org.eclipse.debug.core.environmentVariables", parameters.getEnvironment().get());

            var classpathList = classpathList(rootElement);
            addClasspathProjects(classpathList, parameters.getProjectDependencies());
            addClasspathLibraries(classpathList, parameters.getClasspath());
            addClasspathJava(classpathList, parameters.getJavaVersion().get());

            booleanAttribute(launch, rootElement, "org.eclipse.jdt.launching.DEFAULT_CLASSPATH", false);

            launch.appendChild(rootElement);

            var source = new DOMSource(launch);
            var result = new StreamResult(parameters.getOutputFile().getAsFile().get());

            try {
                transformer.transform(source, result);
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
        }

        private static void stringAttribute(Document document, Element parent, String key, Object value) {
            var attribute = document.createElement("stringAttribute");

            attribute.setAttribute("key", key);
            attribute.setAttribute("value", value.toString());
            parent.appendChild(attribute);
        }

        private static void booleanAttribute(Document document, Element parent, String key, boolean value) {
            var attribute = document.createElement("booleanAttribute");

            attribute.setAttribute("key", key);
            attribute.setAttribute("value", Boolean.toString(value));
            parent.appendChild(attribute);
        }

        private static void listAttribute(Document document, Element parent, String key, Iterable<?> list) {
            var attribute = document.createElement("listAttribute");
            attribute.setAttribute("key", key);

            for (var v : list) {
                var listEntry = document.createElement("listEntry");
                listEntry.setAttribute("value", v.toString());
                attribute.appendChild(listEntry);
            }
            parent.appendChild(attribute);
        }


        private static final String CLASSPATH_ENTRY_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><runtimeClasspathEntry ";
        private static final String CLASSPATH_ENTRY_SUFFIX = " path=\"5\" />";

        private static Element addChild(Element parent, String name) {
            var ret = parent.getOwnerDocument().createElement(name);
            parent.appendChild(ret);
            return ret;
        }

        private static Element classpathList(Element parent) {
            var attribute = addChild(parent, "listAttribute");
            attribute.setAttribute("key", "org.eclipse.jdt.launching.CLASSPATH");
            return attribute;
        }

        private static void classpathEntry(Element parent, int type, String value) {
            addChild(parent, "listEntry").setAttribute("value", CLASSPATH_ENTRY_PREFIX + value + " type=\"" + type + "\"" + CLASSPATH_ENTRY_SUFFIX);
        }

        private static void addClasspathLibraries(Element parent, FileCollection files) {
            for (var v : files.getFiles())
                classpathEntry(parent, 2, "externalArchive=\"" + v + "\"");
        }

        private static void addClasspathProjects(Element parent, ListProperty<String> projects) {
            for (var v : projects.get()) {
                classpathEntry(parent, 1, "projectName=\"" + v + "\"");
                classpathEntry(parent, 4, "containerPath=\"org.eclipse.buildship.core.gradleclasspathcontainer\" javaProject=\"" + v + "\"");
            }
        }

        private static void addClasspathJava(Element parent, String version) {
            classpathEntry(parent, 4, "containerPath=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-" + version + "\"");
        }

        private static void mapAttribute(Document document, Element parent, String key, Map<String, ?> map) {
            var attribute = document.createElement("mapAttribute");
            attribute.setAttribute("key", key);

            for (var entry : map.entrySet()) {
                var k = entry.getKey();
                var v = entry.getValue();

                var mapEntry = document.createElement("mapEntry");
                mapEntry.setAttribute("key", k);
                mapEntry.setAttribute("value", v.toString());
                attribute.appendChild(mapEntry);
            }
            parent.appendChild(attribute);
        }
    }
}
