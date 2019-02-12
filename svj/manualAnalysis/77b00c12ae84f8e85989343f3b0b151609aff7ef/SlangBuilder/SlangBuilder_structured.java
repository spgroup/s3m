/*
 * (c) Copyright 2014 Hewlett-Packard Development Company, L.P.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0 which accompany this distribution.
 *
 * The Apache License is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package io.cloudslang.lang.tools.build;

import io.cloudslang.lang.compiler.modeller.model.Executable;
import io.cloudslang.lang.entities.CompilationArtifact;
import io.cloudslang.lang.tools.build.tester.IRunTestResults;
import io.cloudslang.lang.tools.build.tester.RunTestsResults;
import io.cloudslang.lang.tools.build.tester.SlangTestRunner;
import io.cloudslang.lang.tools.build.tester.parse.SlangTestCase;
import io.cloudslang.lang.tools.build.verifier.CompilationResult;
import io.cloudslang.lang.tools.build.verifier.SlangContentVerifier;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Verifies all files with extensions: .sl, .sl.yaml or .sl.yml in a given directory are valid
 */
@Component
public class SlangBuilder {

    @Autowired
    private SlangContentVerifier slangContentVerifier;

    @Autowired
    private SlangTestRunner slangTestRunner;

    private static final Logger log = Logger.getLogger(SlangBuilder.class);

    public SlangBuildResults buildSlangContent(String projectPath, String contentPath, String testsPath, List<String> testSuits, Boolean shouldValidateDescription, boolean runTestsInParallel) {
        String projectName = FilenameUtils.getName(projectPath);
        log.info("");
        log.info("------------------------------------------------------------");
        log.info("Building project: " + projectName);
        log.info("------------------------------------------------------------");
        log.info("");
        log.info("--- compiling sources ---");
        CompilationResult compilationResult = slangContentVerifier.createModelsAndValidate(contentPath, shouldValidateDescription);
        Map<String, Executable> slangModels = compilationResult.getResults();
        Map<String, CompilationArtifact> compiledSources = compileModels(slangModels);
        IRunTestResults runTestsResults = new RunTestsResults();
        if (StringUtils.isNotBlank(testsPath) && new File(testsPath).isDirectory()) {
            runTestsResults = runTests(slangModels, projectPath, testsPath, testSuits, runTestsInParallel);
        }
        List<RuntimeException> exceptions = new ArrayList<>(runTestsResults.getExceptions());
        exceptions.addAll(compilationResult.getExceptions());
        return new SlangBuildResults(compiledSources.size(), runTestsResults, exceptions);
    }

    /**
     * Compiles all CloudSlang models
     * @return the number of valid CloudSlang files in the given directory
     */
    private Map<String, CompilationArtifact> compileModels(Map<String, Executable> slangModels) {
        Map<String, CompilationArtifact> compiledSlangFiles = slangContentVerifier.compileSlangModels(slangModels);
        if (compiledSlangFiles.size() != slangModels.size()) {
            throw new RuntimeException("Some Slang files were not compiled.\n" + "Found: " + slangModels.size() + " slang models, but managed to compile only: " + compiledSlangFiles.size());
        }
        log.info("Successfully finished Compilation of: " + compiledSlangFiles.size() + " Slang files");
        return compiledSlangFiles;
    }

    IRunTestResults runTests(Map<String, Executable> contentSlangModels, String projectPath, String testsPath, List<String> testSuites, boolean runTestsInParallel) {
        log.info("");
        log.info("--- compiling tests sources ---");
        CompilationResult compilationResult = slangContentVerifier.createModelsAndValidate(testsPath, false);
        Map<String, Executable> testFlowModels = compilationResult.getResults();
        Map<String, Executable> allTestedFlowModels = new HashMap<>(testFlowModels);
        allTestedFlowModels.putAll(contentSlangModels);
        Map<String, CompilationArtifact> compiledFlows = slangContentVerifier.compileSlangModels(allTestedFlowModels);
        Set<String> allTestedFlowsFQN = mapExecutablesToFullyQualifiedName(allTestedFlowModels.values());
        Map<String, SlangTestCase> testCases = slangTestRunner.createTestCases(testsPath, allTestedFlowsFQN);
        log.info("");
        log.info("--- running tests ---");
        log.info("Found " + testCases.size() + " tests");
        IRunTestResults runTestsResults;
        if (runTestsInParallel) {
            runTestsResults = slangTestRunner.runAllTestsParallel(projectPath, testCases, compiledFlows, testSuites);
        } else {
            runTestsResults = slangTestRunner.runAllTestsSequential(projectPath, testCases, compiledFlows, testSuites);
        }
        runTestsResults.addExceptions(compilationResult.getExceptions());
        addCoverageDataToRunTestsResults(contentSlangModels, testFlowModels, testCases, runTestsResults);
        return runTestsResults;
    }

    private Set<String> mapExecutablesToFullyQualifiedName(Collection<Executable> executables) {
        Set<String> fullyQualifiedNames = new HashSet<>();
        for (Executable executable : executables) {
            fullyQualifiedNames.add(executable.getId());
        }
        return fullyQualifiedNames;
    }

    void addCoverageDataToRunTestsResults(Map<String, Executable> contentSlangModels, Map<String, Executable> testFlowModels, Map<String, SlangTestCase> testCases, IRunTestResults runTestsResults) {
        Set<String> coveredContent = new HashSet<>();
        Set<String> uncoveredContent = new HashSet<>();
        for (SlangTestCase testCase : testCases.values()) {
            String testFlowPath = testCase.getTestFlowPath();
            Executable testFlowModel;
            if (testFlowModels.containsKey(testFlowPath)) {
                testFlowModel = testFlowModels.get(testFlowPath);
            } else {
                testFlowModel = contentSlangModels.get(testFlowPath);
            }
            if (testFlowModel == null) {
                continue;
            }
            addAllDependenciesToCoveredContent(coveredContent, testFlowModel.getExecutableDependencies(), contentSlangModels);
        }
        Set<String> contentExecutablesNames = contentSlangModels.keySet();
        for (SlangTestCase testCase : testCases.values()) {
            String testFlowPath = testCase.getTestFlowPath();
            if (contentExecutablesNames.contains(testFlowPath)) {
                coveredContent.add(testFlowPath);
            }
        }
        for (String contentModelName : contentExecutablesNames) {
            if (!coveredContent.contains(contentModelName)) {
                uncoveredContent.add(contentModelName);
            }
        }
        runTestsResults.addCoveredExecutables(coveredContent);
        runTestsResults.addUncoveredExecutables(uncoveredContent);
    }

    /**
     * Collect recursively all the dependencies of an executable
     * @param allDependencies
     * @param directDependencies
     * @param contentSlangModels
     */
    private void addAllDependenciesToCoveredContent(Set<String> allDependencies, Set<String> directDependencies, Map<String, Executable> contentSlangModels) {
        for (String dependency : directDependencies) {
            if (allDependencies.contains(dependency)) {
                continue;
            }
            allDependencies.add(dependency);
            Executable executable = contentSlangModels.get(dependency);
            if (executable != null) {
                addAllDependenciesToCoveredContent(allDependencies, executable.getExecutableDependencies(), contentSlangModels);
            }
        }
    }
}

