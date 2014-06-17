package org.whitesource.maven;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.whitesource.agent.api.ChecksumUtils;
import org.whitesource.agent.api.dispatch.CheckPoliciesResult;
import org.whitesource.agent.api.dispatch.GetInHouseRulesResult;
import org.whitesource.agent.api.model.*;
import org.whitesource.agent.client.WssServiceException;
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

    public static final String REGEX_MATCH_ALL = "*";

    /* --- Members --- */

    /**
     * Unique identifier of the organization to update.
     */
    @Parameter(alias = "orgToken",
            property = Constants.ORG_TOKEN,
            required = true)
    protected String orgToken;

    /**
     * Product to update Name or Unique identifier.
     */
    @Parameter(alias = "product",
            property = Constants.PRODUCT,
            required = false)
    protected String product;

    /**
     * Product to update version.
     */
    @Parameter(alias = "productVersion",
            property = Constants.PRODUCT_VERSION,
            required = false)
    protected String productVersion;

    /**
     * Optional. Set to false to include test scope dependencies.
     */
    @Parameter(alias = "ignoreTestScopeDependencies",
            property = Constants.IGNORE_TEST_SCOPE_DEPENDENCIES,
            required = false,
            defaultValue = "true")
    protected boolean ignoreTestScopeDependencies;

    /**
     * Output directory for checking policies results.
     */
    @Parameter( alias = "outputDirectory",
            property = Constants.OUTPUT_DIRECTORY,
            required = false,
            defaultValue = "${project.reporting.outputDirectory}")
    protected File outputDirectory;

    /**
     * Optional. Unique identifier of the White Source project to update.
     * If omitted, default naming convention will apply.
     */
    @Parameter(alias = "projectToken",
            property = Constants.PROJECT_TOKEN,
            required = false)
    protected String projectToken;

    /**
     * Optional. Map of module artifactId to White Source project token.
     */
    @Parameter(alias = "moduleTokens",
            property = Constants.MODULE_TOKENS,
            required = false)
    protected Map<String, String> moduleTokens = new HashMap<String, String>();

    /**
     * Optional. Use name value pairs for specifying the project tokens to use for modules whose artifactId
     * is not a valid XML tag.
     */
    @Parameter(alias = "specialModuleTokens",
            property = Constants.SPECIAL_MODULE_TOKENS,
            required = false)
    protected Properties specialModuleTokens = new Properties();

    /**
     * Optional. Set to true to ignore this maven project. Overrides any include patterns.
     */
    @Parameter(alias = "ignore",
            property = Constants.IGNORE,
            required = false,
            defaultValue = "false")
    protected boolean ignore;

    /**
     * Optional. Only modules with an artifactId matching one of these patterns will be processed by the plugin.
     */
    @Parameter(alias = "includes",
            property = Constants.INCLUDES,
            required = false,
            defaultValue = "")
    protected String[] includes;

    /**
     * Optional. Modules with an artifactId matching any of these patterns will not be processed by the plugin.
     */
    @Parameter(alias = "excludes",
            property = Constants.EXCLUDES,
            required = false,
            defaultValue = "")
    protected String[] excludes;

    /**
     * Optional. Set to true to ignore this maven modules of type pom.
     */
    @Parameter(alias = "ignorePomModules",
            property = Constants.IGNORE_POM_MODULES,
            required = false,
            defaultValue = "true")
    protected boolean ignorePomModules;

    @Parameter(defaultValue = "${reactorProjects}",
            required = true,
            readonly = true)
    protected Collection<MavenProject> reactorProjects;

    @Parameter(defaultValue = "false",
            required = false)
    protected boolean reportAsJson;

    /**
     * Optional. Set to true to recursively resolve and send transitive dependencies of internal dependencies to WhiteSource.
     * Should only be set to 'true' if any internal (in-house) dependencies are used in the project.
     */
    @Parameter(defaultValue = "false",
            required = false)
    protected boolean resolveInHouseDependencies;

    /**
     * The dependency tree builder to use.
     */
    @Component( hint = "default" )
    private DependencyGraphBuilder dependencyGraphBuilder;

    private Collection<InHouseRule> inHouseRules;

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
        for (Exclusion exclusion : (List<Exclusion>) dependency.getExclusions()) {
            exclusions.add(new ExclusionInfo(exclusion.getArtifactId(), exclusion.getGroupId()));
        }

        return info;
    }

    protected DependencyInfo getDependencyInfo(Artifact artifact) {
        DependencyInfo info = new DependencyInfo();

        // dependency data
        info.setGroupId(artifact.getGroupId());
        info.setArtifactId(artifact.getArtifactId());
        info.setVersion(artifact.getVersion());
        info.setScope(artifact.getScope());
        info.setClassifier(artifact.getClassifier());
        info.setOptional(artifact.isOptional());
        info.setType(artifact.getType());

        return info;
    }

    protected void debugProjectInfos(Collection<AgentProjectInfo> projectInfos) {
        debug("----------------- dumping projectInfos -----------------");
        debug("Total number of projects : " + projectInfos.size());

        for (AgentProjectInfo projectInfo : projectInfos) {
            debug("Project coordiantes: " + projectInfo.getCoordinates().toString());
            debug("Project parent coordiantes: " + (projectInfo.getParentCoordinates() == null ? "" : projectInfo.getParentCoordinates().toString()));
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

        // dependencies
        boolean collectDirectDependencies = !resolveInHouseDependencies;
        if (resolveInHouseDependencies) {
            try {
                info("Getting in-house rules...");
                GetInHouseRulesResult inHouseRulesResult = service.getInHouseRules(orgToken);
                inHouseRules = inHouseRulesResult.getInHouseRules();
                if (inHouseRules.isEmpty()) {
                    collectDirectDependencies = true;
                } else {
                   projectInfo.getDependencies().addAll(collectDependenciesResolveInHouse(project));
                }
            } catch (WssServiceException e) {
                throw new MojoExecutionException(Constants.ERROR_SERVICE_CONNECTION + e.getMessage(), e);
            } catch (DependencyGraphBuilderException e) {
                collectDirectDependencies = true;
                projectInfo.getDependencies().clear();
                debug("failed to collect dependencies recursively, fallback to direct dependencies only");
            }
        }

        if (collectDirectDependencies) {
            projectInfo.getDependencies().addAll(collectDependencies(project));
        }

        info("Total processing time is " + (System.currentTimeMillis() - startTime) + " [msec]");

        return projectInfo;
    }

    protected Collection<DependencyInfo> collectDependencies(MavenProject project) {
        Collection<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();

        Map<Dependency, Artifact> lut = createLookupTable(project);
        for (Dependency dependency : project.getDependencies()) {
            if (ignoreTestScopeDependencies && Artifact.SCOPE_TEST.equals(dependency.getScope())) {
                continue; // exclude test scope dependencies from being sent to the server
            }

            DependencyInfo dependencyInfo = getDependencyInfo(dependency);

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

    protected Collection<DependencyInfo> collectDependenciesResolveInHouse(MavenProject project) throws DependencyGraphBuilderException {
        Collection<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();

        DependencyNode hierarchyRoot = dependencyGraphBuilder.buildDependencyGraph(project, null);
        Map<Dependency, DependencyNode> lut = createLookupTable(project, hierarchyRoot);
        for (Dependency dependency : project.getDependencies()) {
            if (ignoreTestScopeDependencies && Artifact.SCOPE_TEST.equals(dependency.getScope())) {
                continue; // exclude test scope dependencies from being sent to the server
            }

            DependencyInfo dependencyInfo = getDependencyInfo(dependency);
            dependencyInfos.add(dependencyInfo);

            DependencyNode dependencyNode = lut.get(dependency);
            Artifact artifact = dependencyNode.getArtifact();
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

            // resolve in-house dependencies, send them all as flat list (direct dependencies)
            if (isInHouse(dependencyInfo)) {
                for (DependencyNode child : dependencyNode.getChildren()) {
                    dependencyInfos.addAll(getChildrenAsFlatList(child));
                }
            }
        }

        return dependencyInfos;
    }

    private boolean isInHouse(DependencyInfo dependency) {
        boolean inHouse = false;
        if (inHouseRules != null) {
            for (InHouseRule inHouseRule : inHouseRules) {
                String groupId = dependency.getGroupId();
                String artifactId = dependency.getArtifactId();

                if (StringUtils.isNotBlank(inHouseRule.getNameRegex())) {
                    if (inHouseRule.match(groupId) || inHouseRule.match(artifactId)) {
                        debug(dependency.toString() + " matches in-house rule " + inHouseRule.toString());
                        inHouse = true;
                        break;
                    }
                } else {
                    if (inHouseRule.match(groupId, artifactId)) {
                        debug(dependency.toString() + " matches in-house rule " + inHouseRule.toString());
                        inHouse = true;
                        break;
                    }
                }
            }
        }
        return inHouse;
    }

    private Collection<DependencyInfo> getChildrenAsFlatList(DependencyNode dependencyNode) {
        Collection<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();

        DependencyInfo childInfo = getDependencyInfo(dependencyNode.getArtifact());
        childInfo.setExclusions(Arrays.asList(new ExclusionInfo(REGEX_MATCH_ALL, REGEX_MATCH_ALL)));
        dependencyInfos.add(childInfo);

        for (DependencyNode childNode : dependencyNode.getChildren()) {
            dependencyInfos.addAll(getChildrenAsFlatList(childNode));
        }

        return dependencyInfos;
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

    protected Map<Dependency,DependencyNode> createLookupTable(MavenProject project, DependencyNode hierarchyRoot) {
        Map<Dependency, DependencyNode> lut = new HashMap<Dependency, DependencyNode>();

        for (Dependency dependency : project.getDependencies()) {
            for (DependencyNode dependencyNode : hierarchyRoot.getChildren()) {
                Artifact dependencyArtifact = dependencyNode.getArtifact();
                if (match(dependency, dependencyArtifact)) {
                    lut.put(dependency, dependencyNode);
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

        if (ignorePomModules && "pom".equals(project.getPackaging())) {
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
            if (reportAsJson) {
                report.generateJson(outputDirectory);
            } else {
                report.generate(outputDirectory, false);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error generating report: " + e.getMessage(), e);
        }
    }

}
