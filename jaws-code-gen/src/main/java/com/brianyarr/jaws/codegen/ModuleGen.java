package com.brianyarr.jaws.codegen;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

public class ModuleGen {

    private final File rootDir;

    public ModuleGen(final File rootDir) {

        this.rootDir = rootDir;
    }

    public void generateModule(final Module module) throws IOException {
        generateModule(module.serviceInterface, module.awsModuleName);
    }

    private void generateModule(final Class<?> serviceInterface, final String awsModuleName) throws IOException {
        final String moduleName = "jaws-" + awsModuleName.toLowerCase();
        final File moduleDir = new File(rootDir, moduleName);
        if (!moduleDir.exists()) {
            moduleDir.mkdir();
        }

        writeGradleFile(moduleDir, awsModuleName.toLowerCase());
        updateGradleSettings(moduleName);
        writeGitIgnoreFile(moduleDir);

        final File genSrcDir = new File(moduleDir, "src/gen/java");
        if (genSrcDir.exists()) {
            FileUtils.cleanDirectory(genSrcDir);
        } else {
            genSrcDir.mkdirs();
        }

        final String packageName = "com.brianyarr.jaws." + awsModuleName.toLowerCase().replace("-", "");
        final ServiceGenerator serviceGenerator = new ServiceGenerator(new JavaPoetClassGenerator(genSrcDir), serviceInterface, packageName);
        serviceGenerator.tryAddAllMethods();
        serviceGenerator.build();
    }

    public void cleanModule(final Module module) throws IOException {
        final String moduleName = "jaws-" + module.awsModuleName;
        final File moduleDir = new File(rootDir, moduleName);
        if (moduleDir.exists()) {
            FileUtils.cleanDirectory(moduleDir);
        }
        removeFromGradleSettings(moduleName);
    }

    private void writeGitIgnoreFile(final File moduleDir) throws IOException {
        final File ignoreFIle = new File(moduleDir, ".gitignore");
        Files.write(ignoreFIle.toPath(), "src/gen/".getBytes());
    }

    private void updateGradleSettings(final String moduleName) throws IOException {
        final File file = new File(rootDir, "settings.gradle");
        final String contents = Files.lines(file.toPath()).collect(Collectors.joining("\n"));
        GradleUtil.ensureModuleInSettings(contents, moduleName).ifPresent(s -> {
            try {
                Files.write(file.toPath(), s.getBytes(), CREATE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }

    private void removeFromGradleSettings(final String moduleName) throws IOException {
        final File file = new File(rootDir, "settings.gradle");
        final String regex = "'" + moduleName + "',?";
        final String newContents = Files.readAllLines(file.toPath()).stream().map(l -> GradleUtil.removeModuleFromSettings(l, moduleName)).collect(Collectors.joining("\n"));
        Files.write(file.toPath(), newContents.getBytes());
    }

    private void writeGradleFile(final File moduleDir, final String awsModuleName) throws IOException {
        final File file = new File(moduleDir, "build.gradle");
        if (!file.exists()) {
            Files.write(file.toPath(), getGradleFile(awsModuleName).getBytes(), CREATE_NEW);
        }
    }

    private String getGradleFile(final String awsModuleName) {
        return String.format("sourceSets {\n" +
                "    gen {\n" +
                "        java {\n" +
                "            srcDir '${build}/src/gen/java'\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "dependencies {\n" +
                "    compile project(':jaws-core')\n" +
                "    compile 'com.amazonaws:aws-java-sdk-%s'\n" +
                "    genCompile project(':jaws-core')\n" +
                "    genCompile 'com.amazonaws:aws-java-sdk-%s'\n" +
                "}", awsModuleName, awsModuleName);
    }

    public static void main(String[] args) throws IOException {
        final ModuleGen moduleGen = new ModuleGen(new File("/Users/brian.yarr/code/jaws/"));
        for (Module m : Module.MODULES) {
            moduleGen.generateModule(m);
        }
    }

}
