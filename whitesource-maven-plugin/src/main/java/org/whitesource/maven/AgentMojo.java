package org.whitesource.maven;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.MavenProject;
import org.whitesource.agent.api.ChecksumUtils;
import org.whitesource.agent.api.dispatch.CheckPoliciesResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.ExclusionInfo;
import org.whitesource.agent.report.PolicyCheckReport;
import org.whitesource.maven.utils.dependencies.*;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
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
    public static final String SCOPE_TEST = "test";
    public static final String SCOPE_PROVIDED = "provided";

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
     * Optional. Scopes to be ignored (default "test" and "provided").
     */
    @Parameter(alias = "ignoredScopes", property = Constants.SCOPE, required = false)
    protected String[] ignoredScopes;

    /**
     * Optional. Set to true to ignore this maven modules of type pom.
     */
    @Parameter(alias = "ignorePomModules", property = Constants.IGNORE_POM_MODULES, required = false, defaultValue = "true")
    protected boolean ignorePomModules;

    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    protected Collection<MavenProject> reactorProjects;

    /* --- Aggregate Modules Parameters --- */

    /**
     * Optional. Set to true to combine all pom modules into a single WhiteSource project with an aggregated dependency flat list (no hierarchy).
     */
    @Parameter(alias = "aggregateModules", property = Constants.AGGREGATE_MODULES, required = false, defaultValue = "false")
    protected boolean aggregateModules;

    /**
     * Optional. The aggregated project name that will appear in WhiteSource.
     * If omitted and no project token defined, defaults to pom artifactId.
     */
    @Parameter(alias = "aggregateProjectName", property = Constants.AGGREGATE_MODULES_PROJECT_NAME, required = false)
    protected String aggregateProjectName;

    /**
     * Optional. Unique identifier of the aggregated White Source project to update.
     * If omitted, default naming convention will apply.
     */
    @Parameter(alias = "aggregateProjectToken", property = Constants.AGGREGATE_MODULES_PROJECT_TOKEN, required = false)
    protected String aggregateProjectToken;

    /**
     * Optional. Email of the requester as appears in WhiteSource.
     */
    @Parameter(alias = "requesterEmail", property = Constants.REQUESTER_EMAIL, required = false)
    protected String requesterEmail;

    /* --- Constructors --- */

    protected AgentMojo() {
        // set default values
        if (ignoredScopes == null) {
            ignoredScopes = new String[2];
            ignoredScopes[0] = SCOPE_TEST;
            ignoredScopes[1] = SCOPE_PROVIDED;
        }
    }

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

    private DependencyInfo getDependencyInfo(AetherDependencyNode dependencyNode) {
        DependencyInfo info = new DependencyInfo();

        // dependency data
        AetherDependency dependency = dependencyNode.getDependency();
        AetherArtifact artifact = dependency.getArtifact();
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
        for (AetherExclusion exclusion : dependency.getExclusions()) {
            info.getExclusions().add(new ExclusionInfo(exclusion.getArtifactId(), exclusion.getGroupId()));
        }

        // recursively collect children
        for (AetherDependencyNode child : dependencyNode.getChildren()) {
            info.getChildren().add(getDependencyInfo(child));
        }

        return info;
    }

    protected void debugProjectInfos(Collection<AgentProjectInfo> projectInfos) {
        debug("----------------- dumping projectInfos -----------------");
        debug("Total Number of Projects : " + projectInfos.size());

        for (AgentProjectInfo projectInfo : projectInfos) {
            debug("Project Coordinates: " + projectInfo.getCoordinates().toString());
            debug("Project Parent Coordinates: " + (projectInfo.getParentCoordinates() == null ? "" : projectInfo.getParentCoordinates().toString()));
            debug("Project Token: " + projectInfo.getProjectToken());
            debug("Total Number of Dependencies: " + projectInfo.getDependencies().size());
            for (DependencyInfo info : projectInfo.getDependencies()) {
                debug(info.toString() + " SHA-1: " + info.getSha1());
            }
        }

        debug("----------------- dump finished -----------------");
    }

    protected AgentProjectInfo processProject(MavenProject project) throws MojoExecutionException {
        long startTime = System.currentTimeMillis();

        info("Processing " + project.getId());

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
            debug("Error resolving project dependencies, fallback to direct dependencies only", e);
            projectInfo.getDependencies().clear();
            projectInfo.getDependencies().addAll(collectDirectDependencies(project));
        }

        debug("Total Processing Time = " + (System.currentTimeMillis() - startTime) + " [msec]");
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
        AetherDependencyNode rootNode = DependencyGraphFactory.getAetherDependencyGraphRootNode(project, projectDependenciesResolver, session);
        Collection<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();
        for (AetherDependencyNode dependencyNode : rootNode.getChildren()) {
            // don't add ignored scope
            String scope = dependencyNode.getDependency().getScope();
            if (StringUtils.isBlank(scope) || !shouldIgnore(scope)) {
                DependencyInfo info = getDependencyInfo(dependencyNode);
                dependencyInfos.add(info);
            }
        }

        debug(MessageFormat.format("*** Printing Graph Result for {0} ***", project.getName()));
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
            }
        }
        debugProjectInfos(projectInfos);

        // combine all pom modules into a single project
        if (aggregateModules) {
            // collect dependencies as flat list
            Set<DependencyInfo> flatDependencies = new HashSet<DependencyInfo>();
            for (AgentProjectInfo projectInfo : projectInfos) {
                for (DependencyInfo dependency : projectInfo.getDependencies()) {
                    flatDependencies.add(dependency);
                    flatDependencies.addAll(extractChildren(dependency));
                }
            }

            // clear all projects
            projectInfos.clear();

            // create combined project
            AgentProjectInfo aggregatingProject = new AgentProjectInfo();
            aggregatingProject.setCoordinates(extractCoordinates(mavenProject));
            aggregatingProject.setProjectToken(aggregateProjectToken);
            aggregatingProject.getDependencies().addAll(flatDependencies);
            // override artifact id with project name
            if (StringUtils.isNotBlank(aggregateProjectName)) {
                aggregatingProject.getCoordinates().setArtifactId(aggregateProjectName);
            }
            projectInfos.add(aggregatingProject);
        }
        return projectInfos;
    }

    private Collection<DependencyInfo> extractChildren(DependencyInfo dependency) {
        Collection<DependencyInfo> children = new ArrayList<DependencyInfo>();
        Iterator<DependencyInfo> iterator = dependency.getChildren().iterator();
        while (iterator.hasNext()) {
            DependencyInfo child = iterator.next();
            children.add(child);
            children.addAll(extractChildren(child));
            // flatten dependencies
            iterator.remove();
        }
        return children;
    }

    protected boolean shouldProcess(MavenProject project) {
        if (project == null) { return false; }

        boolean process = true;
        if (ignorePomModules && POM.equals(project.getPackaging())) {
            process = false;
            info("Skipping " + project.getId() + " (ignorePomModules=" + String.valueOf(ignorePomModules) + ")");
        } else if (project.equals(mavenProject)) {
            process = !ignore;
            if (!process) {
                info("Skipping " + project.getId() + " (marked as ignored)");
            }
        } else if (excludes.length > 0 && matchAny(project.getArtifactId(), excludes)) {
            process = false;
            info("Skipping " + project.getId() + " (marked as excluded)");
        } else if (includes.length > 0 && matchAny(project.getArtifactId(), includes)) {
            process = true;
        }
        return process;
    }

    protected void generateReport(CheckPoliciesResult result) throws MojoExecutionException {
        info("Generating Policy Check Report");
        try {
            PolicyCheckReport report = new PolicyCheckReport(result);
            report.generate(outputDirectory, false);
            report.generateJson(outputDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Error generating report: " + e.getMessage(), e);
        }
    }

    private boolean shouldIgnore(String scope) {
        boolean ignore = false;
        for (String ignoredScope : ignoredScopes) {
            if (ignoredScope.equals(scope)) {
                ignore = true;
                break;
            }
        }
        return ignore;
    }

}