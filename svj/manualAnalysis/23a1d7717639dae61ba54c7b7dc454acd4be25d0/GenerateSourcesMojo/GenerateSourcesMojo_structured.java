/*
 * Copyright (C) 2009 Jayway AB
 * Copyright (C) 2007-2008 JVending Masa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayway.maven.plugins.android.phase01generatesources;

import com.android.manifmerger.ManifestMerger;
import com.android.manifmerger.MergerLog;
import com.android.utils.StdLogger;
import com.jayway.maven.plugins.android.AbstractAndroidMojo;
import com.jayway.maven.plugins.android.CommandExecutor;
import com.jayway.maven.plugins.android.ExecutionException;
import com.jayway.maven.plugins.android.common.ZipExtractor;
import com.jayway.maven.plugins.android.configuration.BuildConfigConstant;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import static com.jayway.maven.plugins.android.common.AndroidExtension.AAR;
import static com.jayway.maven.plugins.android.common.AndroidExtension.APK;
import static com.jayway.maven.plugins.android.common.AndroidExtension.APKLIB;
import static com.jayway.maven.plugins.android.common.AndroidExtension.APKSOURCES;

/**
 * Generates <code>R.java</code> based on resources specified by the <code>resources</code> configuration parameter.
 * Generates java files based on aidl files.
 *
 * @author hugo.josefson@jayway.com
 * @author Manfred Moser <manfred@simpligility.com>
 * @author William Ferguson <william.ferguson@xandar.com.au>
 * @author Malachi de AElfweald malachid@gmail.com
 *
 * @goal generate-sources
 * @phase generate-sources
 * @requiresProject true
 * @requiresDependencyResolution compile
 */
public class GenerateSourcesMojo extends AbstractAndroidMojo {

    /**
     * <p>
     * Override default merging. You must have SDK Tools r20+
     * </p>
     * 
     * <p>
     * <b>IMPORTANT:</b> The resource plugin needs to be disabled for the
     * <code>process-resources</code> phase, so the "default-resources"
     * execution must be added. Without this the non-merged manifest will get
     * re-copied to the build directory.
     * </p>
     * 
     * <p>
     * The <code>androidManifestFile</code> should also be configured to pull
     * from the build directory so that later phases will pull the merged
     * manifest file.
     * </p>
     * <p>
     * Example POM Setup:
     * </p>
     * 
     * <pre>
     * &lt;build&gt;
     *     ...
     *     &lt;plugins&gt;
     *         ...
     *         &lt;plugin&gt;
     *             &lt;artifactId&gt;maven-resources-plugin&lt;/artifactId&gt;
     *             &lt;version&gt;2.6&lt;/version&gt;
     *             &lt;executions&gt;
     *                 &lt;execution&gt;
     *                     &lt;phase&gt;initialize&lt;/phase&gt;
     *                     &lt;goals&gt;
     *                         &lt;goal&gt;resources&lt;/goal&gt;
     *                     &lt;/goals&gt;
     *                 &lt;/execution&gt;
     *                 <b>&lt;execution&gt;
     *                     &lt;id&gt;default-resources&lt;/id&gt;
     *                     &lt;phase&gt;DISABLED&lt;/phase&gt;
     *                 &lt;/execution&gt;</b>
     *             &lt;/executions&gt;
     *         &lt;/plugin&gt;
     *         &lt;plugin&gt;
     *             &lt;groupId&gt;com.jayway.maven.plugins.android.generation2&lt;/groupId&gt;
     *             &lt;artifactId&gt;android-maven-plugin&lt;/artifactId&gt;
     *             &lt;configuration&gt;
     *                 <b>&lt;androidManifestFile&gt;
     *                     ${project.build.directory}/AndroidManifest.xml
     *                 &lt;/androidManifestFile&gt;
     *                 &lt;mergeManifests&gt;true&lt;/mergeManifests&gt;</b>
     *             &lt;/configuration&gt;
     *             &lt;extensions&gt;true&lt;/extensions&gt;
     *         &lt;/plugin&gt;
     *         ...
     *     &lt;/plugins&gt;
     *     ...
     * &lt;/build&gt;
     * </pre>
     * <p>
     * You can filter the pre-merged APK manifest. One important note about Eclipse, Eclipse will
     * replace the merged manifest with a filtered pre-merged version when the project is refreshed.
     * If you want to review the filtered merged version then you will need to open it outside Eclipse
     * without refreshing the project in Eclipse. 
     * </p>
     * <pre>
     * &lt;resources&gt;
     *     &lt;resource&gt;
     *         &lt;targetPath&gt;${project.build.directory}&lt;/targetPath&gt;
     *         &lt;filtering&gt;true&lt;/filtering&gt;
     *         &lt;directory&gt;${basedir}&lt;/directory&gt;
     *         &lt;includes&gt;
     *             &lt;include&gt;AndroidManifest.xml&lt;/include&gt;
     *         &lt;/includes&gt;
     *     &lt;/resource&gt;
     * &lt;/resources&gt;
     * </pre>
     * 
     * @parameter property="android.mergeManifests" default-value="false"
     */
    protected boolean mergeManifests;

    /**
     * Override default generated folder containing R.java
     *
     * @parameter property="android.genDirectory" default-value="${project.build.directory}/generated-sources/r"
     */
    protected File genDirectory;

    /**
     * Override default generated folder containing aidl classes
     *
     * @parameter property="android.genDirectoryAidl"
     * default-value="${project.build.directory}/generated-sources/aidl"
     */
    protected File genDirectoryAidl;

    /**
     * <p>Parameter designed to generate custom BuildConfig constants
     *
     * @parameter property="android.buildConfigConstants"
     * @readonly
     */
    protected BuildConfigConstant[] buildConfigConstants;

    /**
     * Generates the source.
     *
     * @throws MojoExecutionException if it fails.
     * @throws MojoFailureException if it fails.
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!isCurrentProjectAndroid()) {
            return;
        }
        try {
            targetDirectory.mkdirs();
            copyManifest();
            extractSourceDependencies();
            extractLibraryDependencies();
            copyFolder(assetsDirectory, combinedAssets);
            final String[] relativeAidlFileNames1 = findRelativeAidlFileNames(sourceDirectory);
            final String[] relativeAidlFileNames2 = findRelativeAidlFileNames(extractedDependenciesJavaSources);
            final Map<String, String[]> relativeApklibAidlFileNames = new HashMap<String, String[]>();
            for (Artifact artifact : getTransitiveDependencyArtifacts(APKLIB)) {
                final File libSourceFolder = getUnpackedApkLibSourceFolder(artifact);
                final String[] apklibAidlFiles = findRelativeAidlFileNames(libSourceFolder);
                relativeApklibAidlFileNames.put(artifact.getId(), apklibAidlFiles);
            }
            mergeManifests();
            checkPackagesForDuplicates();
            generateR();
            generateBuildConfig();
            Map<File, String[]> files = new HashMap<File, String[]>();
            files.put(sourceDirectory, relativeAidlFileNames1);
            files.put(extractedDependenciesJavaSources, relativeAidlFileNames2);
            for (Artifact artifact : getTransitiveDependencyArtifacts(APKLIB)) {
                final File unpackedLibSourceFolder = getUnpackedApkLibSourceFolder(artifact);
                files.put(unpackedLibSourceFolder, relativeApklibAidlFileNames.get(artifact.getId()));
            }
            generateAidlFiles(files);
        } catch (MojoExecutionException e) {
            getLog().error("Error when generating sources.", e);
            throw e;
        }
    }

    /**
     * Copy the AndroidManifest.xml from sourceManifestFile to androidManifestFile
     *
     * @throws MojoExecutionException
     */
    protected void copyManifest() throws MojoExecutionException {
        getLog().debug("copyManifest: " + sourceManifestFile + " -> " + androidManifestFile);
        if (sourceManifestFile == null) {
            getLog().info("Manifest copying disabled. Using default manifest only");
            return;
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(sourceManifestFile);
            Source source = new DOMSource(doc);
            TransformerFactory xfactory = TransformerFactory.newInstance();
            Transformer xformer = xfactory.newTransformer();
            xformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            FileWriter writer = null;
            try {
                androidManifestFile.getParentFile().mkdirs();
                writer = new FileWriter(androidManifestFile, false);
                if (doc.getXmlEncoding() != null && doc.getXmlVersion() != null) {
                    String xmldecl = String.format("<?xml version=\"%s\" encoding=\"%s\"?>%n", doc.getXmlVersion(), doc.getXmlEncoding());
                    writer.write(xmldecl);
                }
                Result result = new StreamResult(writer);
                xformer.transform(source, result);
                getLog().info("Manifest copied from " + sourceManifestFile + " to " + androidManifestFile);
            } finally {
                IOUtils.closeQuietly(writer);
            }
        } catch (Exception e) {
            getLog().error("Error during copyManifest");
            throw new MojoExecutionException("Error during copyManifest", e);
        }
    }

    /**
     * Extract the source dependencies.
     *
     * @throws MojoExecutionException if it fails.
     */
    protected void extractSourceDependencies() throws MojoExecutionException {
        for (Artifact artifact : getDirectDependencyArtifacts()) {
            String type = artifact.getType();
            if (type.equals(APKSOURCES)) {
                getLog().debug("Detected apksources dependency " + artifact + " with file " + artifact.getFile() + ". Will resolve and extract...");
                final File apksourcesFile = resolveArtifactToFile(artifact);
                getLog().debug("Extracting " + apksourcesFile + "...");
                extractApksources(apksourcesFile);
            }
        }
        if (extractedDependenciesJavaResources.exists()) {
            projectHelper.addResource(project, extractedDependenciesJavaResources.getAbsolutePath(), null, null);
            project.addCompileSourceRoot(extractedDependenciesJavaSources.getAbsolutePath());
        }
    }

    private void extractApksources(File apksourcesFile) throws MojoExecutionException {
        if (apksourcesFile.isDirectory()) {
            getLog().warn("The apksources artifact points to \'" + apksourcesFile + "\' which is a directory; skipping unpacking it.");
            return;
        }
        final UnArchiver unArchiver = new ZipUnArchiver(apksourcesFile) {

            @Override
            protected Logger getLogger() {
                return new ConsoleLogger(Logger.LEVEL_DEBUG, "dependencies-unarchiver");
            }
        };
        extractedDependenciesDirectory.mkdirs();
        unArchiver.setDestDirectory(extractedDependenciesDirectory);
        try {
            unArchiver.extract();
        } catch (ArchiverException e) {
            throw new MojoExecutionException("ArchiverException while extracting " + apksourcesFile.getAbsolutePath() + ". Message: " + e.getLocalizedMessage(), e);
        }
    }

    private void extractLibraryDependencies() throws MojoExecutionException {
        final Collection<Artifact> artifacts = getTransitiveDependencyArtifacts();
        getLog().info("Extracting libs");
        for (Artifact artifact : artifacts) {
            final String type = artifact.getType();
            if (type.equals(APKLIB)) {
                getLog().info("Extracting apklib " + artifact.getArtifactId() + "...");
                extractApklib(artifact);
            } else {
                if (type.equals(AAR)) {
                    getLog().info("Extracting aar " + artifact.getArtifactId() + "...");
                    extractAarLib(artifact);
                } else {
                    if (type.equals(APK)) {
                        getLog().info("Extracting apk " + artifact.getArtifactId() + "...");
                        extractApkClassesJar(artifact);
                    } else {
                        getLog().debug("Not extracting " + artifact.getArtifactId() + "...");
                    }
                }
            }
        }
    }

    /**
     * Extracts ApkLib and adds the assets and apklib sources and resources to the build.
     */
    private void extractApklib(Artifact apklibArtifact) throws MojoExecutionException {
        getUnpackedLibHelper().extractApklib(apklibArtifact);
        copyFolder(getUnpackedLibAssetsFolder(apklibArtifact), combinedAssets);
        final File apklibSourceFolder = getUnpackedApkLibSourceFolder(apklibArtifact);
        final List<String> resourceExclusions = Arrays.asList("**/*.java", "**/*.aidl");
        projectHelper.addResource(project, apklibSourceFolder.getAbsolutePath(), null, resourceExclusions);
        project.addCompileSourceRoot(apklibSourceFolder.getAbsolutePath());
    }

    /**
     * Extracts AarLib and if this is an APK build then adds the assets and resources to the build.
     */
    private void extractAarLib(Artifact aarArtifact) throws MojoExecutionException {
        getUnpackedLibHelper().extractAarLib(aarArtifact);
        if (isAPKBuild()) {
            copyFolder(getUnpackedLibAssetsFolder(aarArtifact), combinedAssets);
        }
        if (isAPKBuild()) {
            final File aarClassesJar = getUnpackedAarClassesJar(aarArtifact);
            final ZipExtractor extractor = new ZipExtractor(getLog());
            final File javaResourcesFolder = getUnpackedAarJavaResourcesFolder(aarArtifact);
            extractor.extract(aarClassesJar, javaResourcesFolder, ".class");
            projectHelper.addResource(project, javaResourcesFolder.getAbsolutePath(), null, null);
            getLog().debug("Added AAR resources to resource classpath : " + javaResourcesFolder);
        }
    }

    /**
     * Copies a dependent APK jar over the top of the placeholder created for it in AarMavenLifeCycleParticipant.
     *
     * This is necessary because we need the classes of the APK added to the compile classpath.
     * NB APK dependencies are uncommon as they should really only be used in a project that tests an apk.
     *
     * @param artifact  APK dependency for this project whose classes will be copied over.
     */
    private void extractApkClassesJar(Artifact artifact) throws MojoExecutionException {
        final File apkClassesJar = getUnpackedLibHelper().getJarFileForApk(artifact);
        final File unpackedClassesJar = getUnpackedLibHelper().getUnpackedClassesJar(artifact);
        try {
            FileUtils.copyFile(apkClassesJar, unpackedClassesJar);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not copy APK classes jar " + apkClassesJar + " to " + unpackedClassesJar, e);
        }
        getLog().debug("Copied APK classes jar into compile classpath : " + unpackedClassesJar);
    }

    /**
     * Checks packages of project and all dependent libraries for duplicates.
     * <p>Generate warning if duplicates presents.
     * <p>(in case of packages similarity R.java and BuildConfig files will be overridden)
     *
     * @throws MojoExecutionException
     */
    private void checkPackagesForDuplicates() throws MojoExecutionException {
        Set<Artifact> dependencyArtifacts = getTransitiveDependencyArtifacts(AAR, APKLIB);
        if (dependencyArtifacts.isEmpty()) {
            return;
        }
        Map<String, Set<Artifact>> packageCompareMap = new HashMap<String, Set<Artifact>>();
        HashSet<Artifact> artifactSet = new HashSet<Artifact>();
        artifactSet.add(project.getArtifact());
        packageCompareMap.put(extractPackageNameFromAndroidManifest(androidManifestFile), artifactSet);
        for (Artifact artifact : dependencyArtifacts) {
            File unpackDir = getUnpackedLibFolder(artifact);
            File apklibManifest = new File(unpackDir, "AndroidManifest.xml");
            String libPackage = extractPackageNameFromAndroidManifest(apklibManifest);
            Set<Artifact> artifacts = packageCompareMap.get(libPackage);
            if (artifacts == null) {
                artifacts = new HashSet<Artifact>();
                packageCompareMap.put(libPackage, artifacts);
            }
            artifacts.add(artifact);
        }
        StringBuilder messageBuilder = new StringBuilder();
        for (Map.Entry<String, Set<Artifact>> entry : packageCompareMap.entrySet()) {
            Set<Artifact> artifacts = entry.getValue();
            if (artifacts != null && artifacts.size() > 1) {
                messageBuilder.append("\n").append("    artifacts: ");
                for (Artifact item : artifacts) {
                    messageBuilder.append("\"").append(item.getArtifactId()).append("\"").append(" ");
                }
                messageBuilder.append("have similar package: ").append(entry.getKey());
            }
        }
        if (messageBuilder.length() > 0) {
            getLog().warn("Duplicate packages detected in next artifacts:" + messageBuilder.toString());
        }
    }

    private void generateR() throws MojoExecutionException {
        getLog().info("Generating R file for " + project.getArtifact());
        genDirectory.mkdirs();
        final File[] overlayDirectories = getResourceOverlayDirectories();
        getLog().debug("Resource overlay folders : " + Arrays.asList(overlayDirectories));
        final CommandExecutor executor = CommandExecutor.Factory.createDefaultCommmandExecutor();
        executor.setLogger(this.getLog());
        final List<String> commands = new ArrayList<String>();
        commands.add("package");
        commands.add("-f");
        commands.add("--no-crunch");
        commands.add("-I");
        commands.add(getAndroidSdk().getAndroidJar().getAbsolutePath());
        commands.add("-M");
        commands.add(androidManifestFile.getAbsolutePath());
        for (File resOverlayDir : overlayDirectories) {
            if (resOverlayDir != null && resOverlayDir.exists()) {
                getLog().debug("Adding resource overlay folder : " + resOverlayDir);
                commands.add("-S");
                commands.add(resOverlayDir.getAbsolutePath());
            }
        }
        if (resourceDirectory.exists()) {
            getLog().debug("Adding resource folder : " + resourceDirectory);
            commands.add("-S");
            commands.add(resourceDirectory.getAbsolutePath());
        }
        addLibraryResourceFolders(commands);
        if (combinedAssets.exists()) {
            getLog().debug("Adding assets folder : " + combinedAssets);
            commands.add("-A");
            commands.add(combinedAssets.getAbsolutePath());
        }
        commands.add("-m");
        commands.add("-J");
        commands.add(genDirectory.getAbsolutePath());
        if (proguardFile != null) {
            final File parentFolder = proguardFile.getParentFile();
            if (parentFolder != null) {
                parentFolder.mkdirs();
            }
            getLog().debug("Adding proguard file : " + proguardFile);
            commands.add("-G");
            commands.add(proguardFile.getAbsolutePath());
        }
        if (StringUtils.isNotBlank(customPackage)) {
            getLog().debug("Adding custom-package : " + customPackage);
            commands.add("--custom-package");
            commands.add(customPackage);
        }
        if (AAR.equals(project.getArtifact().getType())) {
            getLog().debug("Adding non-constant-id");
            commands.add("--non-constant-id");
        }
        for (String aaptExtraArg : aaptExtraArgs) {
            getLog().debug("Adding aapt arg : " + aaptExtraArg);
            commands.add(aaptExtraArg);
        }
        if (StringUtils.isNotBlank(configurations)) {
            getLog().debug("Adding resource configurations : " + configurations);
            commands.add("-c");
            commands.add(configurations);
        }
        if (aaptVerbose) {
            commands.add("-v");
        }
        commands.add("--output-text-symbols");
        commands.add(targetDirectory.getAbsolutePath());
        commands.add("--auto-add-overlay");
        getLog().debug(getAndroidSdk().getAaptPath() + " " + commands.toString());
        try {
            targetDirectory.mkdirs();
            executor.setCaptureStdOut(true);
            executor.executeCommand(getAndroidSdk().getAaptPath(), commands, project.getBasedir(), false);
        } catch (ExecutionException e) {
            throw new MojoExecutionException("", e);
        }
        ResourceClassGenerator resourceGenerator = new ResourceClassGenerator(this, targetDirectory, genDirectory);
        generateCorrectRJavaForApklibDependencies(resourceGenerator);
        generateCorrectRJavaForAarDependencies(resourceGenerator);
        getLog().info("Adding R gen folder to compile classpath: " + genDirectory);
        project.addCompileSourceRoot(genDirectory.getAbsolutePath());
    }

    /**
     * Generate correct R.java for apklibs dependencies of a current project
     *
     * @throws MojoExecutionException
     */
    private void generateCorrectRJavaForApklibDependencies(ResourceClassGenerator resourceGenerator) throws MojoExecutionException {
        // Generate R.java for apklibs
        // Compatibility with Apklib which isn't present in AndroidBuilder
        generateApkLibRs();
        // Generate error corrected R.java for APKLIB dependencies, but only if this is an APK build.
        final Set<Artifact> apklibDependencies = getTransitiveDependencyArtifacts(APKLIB);
        if (!apklibDependencies.isEmpty() && APK.equals(project.getArtifact().getType())) {
            // Generate R.java for each APKLIB based on R.txt
            getLog().debug("Generating R file for APKLIB dependencies");
            resourceGenerator.generateLibraryRs(apklibDependencies, "apklib");
        }
    }

    /**
     * Generate correct R.java for aar dependencies of a current project
     *
     * @throws MojoExecutionException
     */
    private void generateCorrectRJavaForAarDependencies(ResourceClassGenerator resourceGenerator) throws MojoExecutionException {
        // Generate error corrected R.java for AAR dependencies.
        final Set<Artifact> aarLibraries = getTransitiveDependencyArtifacts(AAR);
        if (!aarLibraries.isEmpty()) {
            // Generate R.java for each AAR based on R.txt
            getLog().debug("Generating R file for AAR dependencies");
            resourceGenerator.generateLibraryRs(aarLibraries, "aar");
        }
    }

    private void generateApkLibRs() throws MojoExecutionException {
        getLog().debug("Generating Rs for apklib deps of project " + project.getArtifact());
        for (final Artifact artifact : getTransitiveDependencyArtifacts(APKLIB)) {
            getLog().debug("Generating apklib R.java for " + artifact.getArtifactId() + "...");
            generateRForApkLibDependency(artifact);
        }
    }

    private void addLibraryResourceFolders(List<String> commands) {
        for (Artifact artifact : getTransitiveDependencyArtifacts()) {
            getLog().debug("Considering dep artifact : " + artifact);
            if (artifact.getType().equals(APKLIB) || artifact.getType().equals(AAR)) {
                final File apklibResDirectory = getUnpackedLibResourceFolder(artifact);
                if (apklibResDirectory.exists()) {
                    getLog().debug("Adding apklib or aar resource folder : " + apklibResDirectory);
                    commands.add("-S");
                    commands.add(apklibResDirectory.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Executes aapt to generate the R class for the given apklib.
     *
     * @param apklibArtifact    apklib for which to generate the R class.
     * @throws MojoExecutionException if it fails.
     */
    private void generateRForApkLibDependency(Artifact apklibArtifact) throws MojoExecutionException {
        final File unpackDir = getUnpackedLibFolder(apklibArtifact);
        getLog().info("Generating R file for apklibrary: " + apklibArtifact.getGroupId() + ":" + apklibArtifact.getArtifactId());
        final File apklibManifest = new File(unpackDir, "AndroidManifest.xml");
        final File apklibResDir = new File(unpackDir, "res");
        final CommandExecutor executor = CommandExecutor.Factory.createDefaultCommmandExecutor();
        executor.setLogger(getLog());
        List<String> commands = new ArrayList<String>();
        commands.add("package");
        commands.add("--non-constant-id");
        commands.add("-m");
        commands.add("-J");
        commands.add(genDirectory.getAbsolutePath());
        commands.add("--custom-package");
        commands.add(extractPackageNameFromAndroidManifest(apklibManifest));
        commands.add("-M");
        commands.add(apklibManifest.getAbsolutePath());
        if (apklibResDir.exists()) {
            commands.add("-S");
            commands.add(apklibResDir.getAbsolutePath());
        }
        final Set<Artifact> apklibDeps = getDependencyResolver().getLibraryDependenciesFor(project, apklibArtifact);
        getLog().debug("apklib dependencies = " + apklibDeps);
        for (Artifact dependency : apklibDeps) {
            final String extension = dependency.getType();
            final File dependencyResDir = getUnpackedLibResourceFolder(dependency);
            if ((extension.equals(APKLIB) || extension.equals(AAR)) && dependencyResDir.exists()) {
                commands.add("-S");
                commands.add(dependencyResDir.getAbsolutePath());
            }
        }
        commands.add("--auto-add-overlay");
        final File apklibCombAssets = new File(getUnpackedLibFolder(apklibArtifact), "combined-assets");
        for (Artifact dependency : apklibDeps) {
            final String extension = dependency.getType();
            final File dependencyAssetsDir = getUnpackedLibAssetsFolder(dependency);
            if ((extension.equals(APKLIB) || extension.equals(AAR))) {
                copyFolder(dependencyAssetsDir, apklibCombAssets);
            }
        }
        final File apkLibAssetsDir = getUnpackedLibAssetsFolder(apklibArtifact);
        copyFolder(apkLibAssetsDir, apklibCombAssets);
        if (apklibCombAssets.exists()) {
            commands.add("-A");
            commands.add(apklibCombAssets.getAbsolutePath());
        }
        commands.add("-I");
        commands.add(getAndroidSdk().getAndroidJar().getAbsolutePath());
        if (StringUtils.isNotBlank(configurations)) {
            commands.add("-c");
            commands.add(configurations);
        }
        for (String aaptExtraArg : aaptExtraArgs) {
            commands.add(aaptExtraArg);
        }
        if (aaptVerbose) {
            commands.add("-v");
        }
        commands.add("--output-text-symbols");
        commands.add(unpackDir.getAbsolutePath());
        getLog().debug(getAndroidSdk().getAaptPath() + " " + commands.toString());
        try {
            executor.setCaptureStdOut(true);
            executor.executeCommand(getAndroidSdk().getAaptPath(), commands, project.getBasedir(), false);
        } catch (ExecutionException e) {
            throw new MojoExecutionException("", e);
        }
    }

    private void mergeManifests() throws MojoExecutionException {
        getLog().debug("mergeManifests: " + mergeManifests);
        if (!mergeManifests) {
            getLog().info("Manifest merging disabled. Using project manifest only");
            return;
        }
        getLog().info("Getting manifests of dependent apklibs");
        List<File> libManifests = new ArrayList<File>();
        for (Artifact artifact : getTransitiveDependencyArtifacts(APKLIB, AAR)) {
            final File libManifest = new File(getUnpackedLibFolder(artifact), "AndroidManifest.xml");
            if (!libManifest.exists()) {
                throw new MojoExecutionException(artifact.getArtifactId() + " is missing AndroidManifest.xml");
            }
            libManifests.add(libManifest);
        }
        if (!libManifests.isEmpty()) {
            final File mergedManifest = new File(androidManifestFile.getParent(), "AndroidManifest-merged.xml");
            final StdLogger stdLogger = new StdLogger(StdLogger.Level.VERBOSE);
            final ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(stdLogger), null);
            getLog().info("Merging manifests of dependent apklibs");
            final boolean mergeSuccess = merger.process(mergedManifest, androidManifestFile, libManifests.toArray(new File[libManifests.size()]), null, null);
            if (mergeSuccess) {
                androidManifestFile.delete();
                mergedManifest.renameTo(androidManifestFile);
                getLog().info("Done Merging Manifests of APKLIBs");
            } else {
                getLog().error("Manifests were not merged!");
                throw new MojoExecutionException("Manifests were not merged!");
            }
        } else {
            getLog().info("No APKLIB manifests found. Using project manifest only.");
        }
    }

    private void generateBuildConfig() throws MojoExecutionException {
        getLog().debug("Generating BuildConfig file");
        String packageName = extractPackageNameFromAndroidManifest(androidManifestFile);
        if (StringUtils.isNotBlank(customPackage)) {
            packageName = customPackage;
        }
        generateBuildConfigForPackage(packageName);
        for (Artifact artifact : getTransitiveDependencyArtifacts(APKLIB, AAR)) {
            if (skipBuildConfigGeneration(artifact)) {
                continue;
            }
            final File manifest = new File(getUnpackedLibFolder(artifact), "AndroidManifest.xml");
            final String depPackageName = extractPackageNameFromAndroidManifest(manifest);
            generateBuildConfigForPackage(depPackageName);
        }
    }

    private boolean skipBuildConfigGeneration(Artifact artifact) throws MojoExecutionException {
        if (artifact.getType().equals(AAR)) {
            if (isBuildConfigPresent(artifact)) {
                return true;
            }
            Set<Artifact> transitiveDep = getArtifactResolverHelper().getFilteredArtifacts(project.getArtifacts(), AAR);
            for (Artifact transitiveArtifact : transitiveDep) {
                if (isBuildConfigPresent(transitiveArtifact)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBuildConfigPresent(Artifact artifact) throws MojoExecutionException {
        try {
            File manifest = new File(getUnpackedLibFolder(artifact), "AndroidManifest.xml");
            String depPackageName = extractPackageNameFromAndroidManifest(manifest);
            JarFile jar = new JarFile(getUnpackedAarClassesJar(artifact));
            JarEntry entry = jar.getJarEntry(depPackageName.replace('.', '/') + "/BuildConfig.class");
            if (entry != null) {
                getLog().info("Skip BuildConfig.java generation for " + artifact.getGroupId() + " " + artifact.getArtifactId());
                return true;
            }
            return false;
        } catch (IOException e) {
            getLog().error("Error generating BuildConfig ", e);
            throw new MojoExecutionException("Error generating BuildConfig", e);
        }
    }

    private void generateBuildConfigForPackage(String packageName) throws MojoExecutionException {
        File outputFolder = new File(genDirectory, packageName.replace(".", File.separator));
        outputFolder.mkdirs();
        StringBuilder buildConfig = new StringBuilder();
        buildConfig.append("package ").append(packageName).append(";\n\n");
        buildConfig.append("public final class BuildConfig {\n");
        buildConfig.append("  public static final boolean DEBUG = ").append(!release).append(";\n");
        for (BuildConfigConstant constant : buildConfigConstants) {
            String value = constant.getValue();
            if ("String".equals(constant.getType())) {
                value = "\"" + value + "\"";
            }
            buildConfig.append("  public static final ").append(constant.getType()).append(" ").append(constant.getName()).append(" = ").append(value).append(";\n");
        }
        buildConfig.append("}\n");
        File outputFile = new File(outputFolder, "BuildConfig.java");
        try {
            FileUtils.writeStringToFile(outputFile, buildConfig.toString());
        } catch (IOException e) {
            getLog().error("Error generating BuildConfig ", e);
            throw new MojoExecutionException("Error generating BuildConfig", e);
        }
    }

    /**
     * Given a map of source directories to list of AIDL (relative) filenames within each,
     * runs the AIDL compiler for each, such that all source directories are available to
     * the AIDL compiler.
     *
     * @param files Map of source directory File instances to the relative paths to all AIDL files within
     * @throws MojoExecutionException If the AIDL compiler fails
     */
    private void generateAidlFiles(Map<File, String[]> files) throws MojoExecutionException {
        List<String> protoCommands = new ArrayList<String>();
        protoCommands.add("-p" + getAndroidSdk().getPathForFrameworkAidl());
        genDirectoryAidl.mkdirs();
        getLog().info("Adding AIDL gen folder to compile classpath: " + genDirectoryAidl);
        project.addCompileSourceRoot(genDirectoryAidl.getPath());
        Set<File> sourceDirs = files.keySet();
        for (File sourceDir : sourceDirs) {
            protoCommands.add("-I" + sourceDir);
        }
        for (File sourceDir : sourceDirs) {
            for (String relativeAidlFileName : files.get(sourceDir)) {
                File targetDirectory = new File(genDirectoryAidl, new File(relativeAidlFileName).getParent());
                targetDirectory.mkdirs();
                final String shortAidlFileName = new File(relativeAidlFileName).getName();
                final String shortJavaFileName = shortAidlFileName.substring(0, shortAidlFileName.lastIndexOf(".")) + ".java";
                final File aidlFileInSourceDirectory = new File(sourceDir, relativeAidlFileName);
                List<String> commands = new ArrayList<String>(protoCommands);
                commands.add(aidlFileInSourceDirectory.getAbsolutePath());
                commands.add(new File(targetDirectory, shortJavaFileName).getAbsolutePath());
                try {
                    CommandExecutor executor = CommandExecutor.Factory.createDefaultCommmandExecutor();
                    executor.setLogger(this.getLog());
                    executor.setCaptureStdOut(true);
                    executor.executeCommand(getAndroidSdk().getAidlPath(), commands, project.getBasedir(), false);
                } catch (ExecutionException e) {
                    throw new MojoExecutionException("", e);
                }
            }
        }
    }

    private String[] findRelativeAidlFileNames(File sourceDirectory) {
        final String[] relativeAidlFileNames = findFilesInDirectory(sourceDirectory, "**/*.aidl");
        if (relativeAidlFileNames.length == 0) {
            getLog().debug("ANDROID-904-002: Found aidl files: Count = " + relativeAidlFileNames.length);
        } else {
            getLog().info("ANDROID-904-002: Found aidl files: Count = " + relativeAidlFileNames.length);
        }
        return relativeAidlFileNames;
    }

    /**
     * @return true if the pom type is APK, APKLIB, or APKSOURCES
     */
    private boolean isCurrentProjectAndroid() {
        Set<String> androidArtifacts = new HashSet<String>() {

            {
                addAll(Arrays.asList(APK, APKLIB, APKSOURCES, AAR));
            }
        };
        return androidArtifacts.contains(project.getArtifact().getType());
    }
}

