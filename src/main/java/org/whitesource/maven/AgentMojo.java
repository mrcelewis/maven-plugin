package org.whitesource.maven;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.whitesource.agent.api.ChecksumUtils;
import org.whitesource.agent.api.dispatch.CheckPoliciesResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.ExclusionInfo;
import org.whitesource.agent.report.PolicyCheckReport;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Concrete implementation holding common functionality to all goals in this plugin that use the agent API.
 *
 * @author tom.shapira
 */
public abstract class AgentMojo extends WhitesourceMojo {

    /* --- Static members --- */

    public static final String POM = "pom";
    public static final String TYPE = "type";

    /* --- Members --- */

    /**
     * Unique identifier of the organization to update.
     */
    @Parameter(alias = "orgToken", property = Constants.ORG_TOKEN, required = true)
    protected String orgToken;

    /**
     * Product to update Name or Unique identifier.
     */
    @Parameter(alias = "product", property = Constants.PRODUCT, required = false)
    protected String product;

    /**
     * Product to update version.
     */
    @Parameter(alias = "productVersion", property = Constants.PRODUCT_VERSION, required = false)
    protected String productVersion;

    /**
     * Optional. Set to false to include test scope dependencies.
     */
    @Parameter(alias = "ignoreTestScopeDependencies", property = Constants.IGNORE_TEST_SCOPE_DEPENDENCIES, required = false, defaultValue = "true")
    protected boolean ignoreTestScopeDependencies;

    /**
     * Output directory for checking policies results.
     */
    @Parameter( alias = "outputDirectory", property = Constants.OUTPUT_DIRECTORY, required = false, defaultValue = "${project.reporting.outputDirectory}")
    protected File outputDirectory;

    /**
     * Optional. Unique identifier of the White Source project to update.
     * If omitted, default naming convention will apply.
     */
    @Parameter(alias = "projectToken", property = Constants.PROJECT_TOKEN, required = false)
    protected String projectToken;

    /**
     * Optional. Map of module artifactId to White Source project token.
     */
    @Parameter(alias = "moduleTokens", property = Constants.MODULE_TOKENS, required = false)
    protected Map<String, String> moduleTokens = new HashMap<String, String>();

    /**
     * Optional. Use name value pairs for specifying the project tokens to use for modules whose artifactId
     * is not a valid XML tag.
     */
    @Parameter(alias = "specialModuleTokens", property = Constants.SPECIAL_MODULE_TOKENS, required = false)
    protected Properties specialModuleTokens = new Properties();

    /**
     * Optional. Set to true to ignore this maven project. Overrides any include patterns.
     */
    @Parameter(alias = "ignore", property = Constants.IGNORE, required = false, defaultValue = "false")
    protected boolean ignore;

    /**
     * Optional. Only modules with an artifactId matching one of these patterns will be processed by the plugin.
     */
    @Parameter(alias = "includes", property = Constants.INCLUDES, required = false, defaultValue = "")
    protected String[] includes;

    /**
     * Optional. Modules with an artifactId matching any of these patterns will not be processed by the plugin.
     */
    @Parameter(alias = "excludes", property = Constants.EXCLUDES, required = false, defaultValue = "")
    protected String[] excludes;

    /**
     * Optional. Set to true to ignore this maven modules of type pom.
     */
    @Parameter(alias = "ignorePomModules", property = Constants.IGNORE_POM_MODULES, required = false, defaultValue = "true")
    protected boolean ignorePomModules;

    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    protected Collection<MavenProject> reactorProjects;

    /* --- Protected methods --- */

    protected DependencyInfo getDependencyInfo(Dependency dependency) {
        DependencyInfo info = new DependencyInfo();

        // dependency data
        info.setGroupId(dependency.getGroupId());
        info.setArtifactId(dependency.getArtifactId());
        info.setVersion(dependency.getVersion());
        info.setScope(dependency.getScope());
        info.setClassifier(dependency.getClassifier());
        info.setOptional(dependency.isOptional());
        info.setType(dependency.getType());
        info.setSystemPath(dependency.getSystemPath());

        // exclusions
        Collection<ExclusionInfo> exclusions = info.getExclusions();
        for (Exclusion exclusion : dependency.getExclusions()) {
            exclusions.add(new ExclusionInfo(exclusion.getArtifactId(), exclusion.getGroupId()));
        }

        return info;
    }

    private DependencyInfo getDependencyInfo(org.sonatype.aether.graph.DependencyNode dependencyNode) {
        DependencyInfo info = new DependencyInfo();

        // dependency data
        org.sonatype.aether.graph.Dependency dependency = dependencyNode.getDependency();
        org.sonatype.aether.artifact.Artifact artifact = dependency.getArtifact();
        info.setGroupId(artifact.getGroupId());
        info.setArtifactId(artifact.getArtifactId());
        info.setVersion(artifact.getVersion());
        info.setScope(dependency.getScope());
        info.setClassifier(artifact.getClassifier());
        info.setOptional(dependency.isOptional());
        info.setType(artifact.getProperty(TYPE, ""));

        // try to calculate SHA-1
        File artifactFile = artifact.getFile();
        if (artifactFile != null && artifactFile.exists()) {
            try {
                info.setSystemPath(artifactFile.getAbsolutePath());
                info.setSha1(ChecksumUtils.calculateSHA1(artifactFile));
            } catch (IOException e) {
                debug(Constants.ERROR_SHA1 + " for " + dependency.toString());
            }
        }

        // exclusions
        for (org.sonatype.aether.graph.Exclusion exclusion : dependency.getExclusions()) {
            info.getExclusions().add(new ExclusionInfo(exclusion.getArtifactId(), exclusion.getGroupId()));
        }

        // recursively collect children
        for (org.sonatype.aether.graph.DependencyNode child : dependencyNode.getChildren()) {
            info.getChildren().add(getDependencyInfo(child));
        }

        return info;
    }

    protected void debugProjectInfos(Collection<AgentProjectInfo> projectInfos) {
        debug("----------------- dumping projectInfos -----------------");
        debug("Total number of projects : " + projectInfos.size());

        for (AgentProjectInfo projectInfo : projectInfos) {
            debug("Project coordinates: " + projectInfo.getCoordinates().toString());
            debug("Project parent coordinates: " + (projectInfo.getParentCoordinates() == null ? "" : projectInfo.getParentCoordinates().toString()));
            debug("Project token: " + projectInfo.getProjectToken());
            debug("total # of dependencies: " + projectInfo.getDependencies().size());
            for (DependencyInfo info : projectInfo.getDependencies()) {
                debug(info.toString() + " SHA-1: " + info.getSha1());
            }
        }

        debug("----------------- dump finished -----------------");
    }

    protected AgentProjectInfo processProject(MavenProject project) throws MojoExecutionException {
        long startTime = System.currentTimeMillis();

        info("processing " + project.getId());

        AgentProjectInfo projectInfo = new AgentProjectInfo();

        // project token
        if (project.equals(mavenProject)) {
            projectInfo.setProjectToken(projectToken);
        } else {
            projectInfo.setProjectToken(moduleTokens.get(project.getArtifactId()));
        }

        // project coordinates
        projectInfo.setCoordinates(extractCoordinates(project));

        // parent coordinates
        if (project.hasParent()) {
            projectInfo.setParentCoordinates(extractCoordinates(project.getParent()));
        }

        // collect dependencies
        try {
           projectInfo.getDependencies().addAll(collectDependencyStructure(project));
        } catch (DependencyResolutionException e) {
            debug("error resolving project dependencies, fallback to direct dependencies only", e);
            projectInfo.getDependencies().clear();
            projectInfo.getDependencies().addAll(collectDirectDependencies(project));
        }

        info("Total processing time is " + (System.currentTimeMillis() - startTime) + " [msec]");

        return projectInfo;
    }

    protected Collection<DependencyInfo> collectDirectDependencies(MavenProject project) {
        Collection<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();

        Map<Dependency, Artifact> lut = createLookupTable(project);
        for (Dependency dependency : project.getDependencies()) {
            if (ignoreTestScopeDependencies && Artifact.SCOPE_TEST.equals(dependency.getScope())) {
                continue; // exclude test scope dependencies from being sent to the server
            }

            DependencyInfo dependencyInfo = getDependencyInfo(dependency);

            // try to calculate SHA-1
            Artifact artifact = lut.get(dependency);
            if (artifact != null) {
                File artifactFile = artifact.getFile();
                if (artifactFile != null && artifactFile.exists()) {
                    try {
                        dependencyInfo.setSha1(ChecksumUtils.calculateSHA1(artifactFile));
                    } catch (IOException e) {
                        debug(Constants.ERROR_SHA1 + " for " + artifact.getId());
                    }
                }
            }
            dependencyInfos.add(dependencyInfo);
        }

        return dependencyInfos;
    }

    /**
     * Build the dependency graph of the project in order to resolve all transitive dependencies.
     * By default resolves filters scopes test and provided, and transitive optional dependencies.
     *
     * @param project The maven project.
     *
     * @return A collection of {@link DependencyInfo} resolved with children.
     *
     * @throws DependencyResolutionException Exception thrown if dependency resolution fails.
     */
    protected Collection<DependencyInfo> collectDependencyStructure(MavenProject project) throws DependencyResolutionException {
        DependencyResolutionResult resolutionResult = projectDependenciesResolver.resolve(
                new DefaultDependencyResolutionRequest(project, repoSession));
        org.sonatype.aether.graph.DependencyNode rootNode = resolutionResult.getDependencyGraph();

        Collection<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();
        for (org.sonatype.aether.graph.DependencyNode dependencyNode : rootNode.getChildren()) {
            DependencyInfo info = getDependencyInfo(dependencyNode);
            dependencyInfos.add(info);
        }

        debug("*** printing graph result ***");
        for (DependencyInfo dependencyInfo : dependencyInfos) {
            debugPrintChildren(dependencyInfo, "");
        }

        return dependencyInfos;
    }

    private void debugPrintChildren(DependencyInfo info, String prefix) {
        debug(prefix + info.getGroupId() + ":" + info.getArtifactId() + ":" + info.getVersion() + ":" + info.getScope());
        for (DependencyInfo child : info.getChildren()) {
            debugPrintChildren(child, prefix + "   ");
        }
    }

    protected Coordinates extractCoordinates(MavenProject mavenProject) {
        return new Coordinates(mavenProject.getGroupId(),
                mavenProject.getArtifactId(),
                mavenProject.getVersion());
    }

    protected Map<Dependency, Artifact> createLookupTable(MavenProject project) {
        Map<Dependency, Artifact> lut = new HashMap<Dependency, Artifact>();

        for (Dependency dependency : project.getDependencies()) {
            for (Artifact dependencyArtifact : project.getDependencyArtifacts()) {
                if (match(dependency, dependencyArtifact)) {
                    lut.put(dependency, dependencyArtifact);
                }
            }
        }

        return lut;
    }

    protected boolean matchAny(String value, String[] patterns) {
        if (value == null) { return false; }

        boolean match = false;

        for (int i=0; i < patterns.length && !match; i++) {
            String pattern = patterns[i];
            if (pattern != null)  {
                String regex = pattern.replace(".", "\\.").replace("*", ".*");
                match = value.matches(regex);
            }
        }

        return match;
    }

    protected boolean match(Dependency dependency, Artifact artifact) {
        boolean match = dependency.getGroupId().equals(artifact.getGroupId()) &&
                dependency.getArtifactId().equals(artifact.getArtifactId()) &&
                dependency.getVersion().equals(artifact.getVersion());

        if (match) {
            String artifactClassifier = artifact.getClassifier();
            if (dependency.getClassifier() == null) {
                match = artifactClassifier == null || StringUtils.isBlank(artifactClassifier);
            } else {
                match = dependency.getClassifier().equals(artifactClassifier);
            }
        }

        if (match) {
            String type = artifact.getType();
            if (dependency.getType() == null) {
                match = type == null || "jar".equals(type);
            } else {
                match = dependency.getType().equals(type);
            }
        }

        return match;
    }

    protected Collection<AgentProjectInfo> extractProjectInfos() throws MojoExecutionException {
        Collection<AgentProjectInfo> projectInfos = new ArrayList<AgentProjectInfo>();

        for (MavenProject project : reactorProjects) {
            if (shouldProcess(project)) {
                projectInfos.add(processProject(project));
            } else {
                info("skipping " + project.getId());
            }
        }

        debugProjectInfos(projectInfos);

        return projectInfos;
    }

    protected boolean shouldProcess(MavenProject project) {
        if (project == null) { return false; }

        boolean process = true;

        if (ignorePomModules && POM.equals(project.getPackaging())) {
            process = false;
        } else if (project.equals(mavenProject)) {
            process = !ignore;
        } else if (excludes.length > 0 && matchAny(project.getArtifactId(), excludes)) {
            process = false;
        } else if (includes.length > 0 && matchAny(project.getArtifactId(), includes)) {
            process = true;
        }

        return process;
    }

    protected void generateReport(CheckPoliciesResult result) throws MojoExecutionException {
        info("Generating policy check report");
        try {
            PolicyCheckReport report = new PolicyCheckReport(result);
            report.generate(outputDirectory, false);
            report.generateJson(outputDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Error generating report: " + e.getMessage(), e);
        }
    }

}
