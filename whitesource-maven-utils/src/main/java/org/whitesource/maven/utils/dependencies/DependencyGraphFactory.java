package org.whitesource.maven.utils.dependencies;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.whitesource.maven.utils.dependencies.impl.eclipse.EclipseAetherDependencyGraphBuilder;
import org.whitesource.maven.utils.dependencies.impl.sonatype.SonatypeAetherDependencyGraphBuilder;

/**
 * Author: Itai Marko
 */
public class DependencyGraphFactory {

    private final static boolean isEclipseAetherLoaded;

    static {
        isEclipseAetherLoaded = isExistsInClasspath("org.eclipse.aether.repository.Proxy");
    }

    private static boolean isExistsInClasspath(String className) {
        try {
            Thread.currentThread().getContextClassLoader().loadClass(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static AetherDependencyNode getAetherDependencyGraphRootNode(MavenProject project, ProjectDependenciesResolver projectDependenciesResolver, MavenSession session) throws DependencyResolutionException {
        if (isEclipseAetherLoaded) {
            return new EclipseAetherDependencyGraphBuilder(project, projectDependenciesResolver, session).build();
        } else {
            return new SonatypeAetherDependencyGraphBuilder(project, projectDependenciesResolver, session).build();
        }
    }

    // prevent instantiation
    private DependencyGraphFactory() {}
}
