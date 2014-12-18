package org.whitesource.maven.utils.dependencies.impl.eclipse;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.*;
import org.eclipse.aether.RepositorySystemSession;
import org.whitesource.maven.utils.Invoker;
import org.whitesource.maven.utils.dependencies.AetherDependencyNode;

/**
 * Author: Itai Marko
 */
public class EclipseAetherDependencyGraphBuilder {

    private MavenProject project;
    private ProjectDependenciesResolver projectDependenciesResolver;
    private MavenSession session;

    public EclipseAetherDependencyGraphBuilder(MavenProject project, ProjectDependenciesResolver projectDependenciesResolver, MavenSession session) {
        this.project = project;
        this.projectDependenciesResolver = projectDependenciesResolver;
        this.session = session;
    }

    public AetherDependencyNode build() throws DependencyResolutionException {
        DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject(project);
        RepositorySystemSession repositorySystemSession = (RepositorySystemSession) Invoker.invoke(session, "getRepositorySession");
        Invoker.invoke(request, "setRepositorySession", RepositorySystemSession.class, repositorySystemSession);
        DependencyResolutionResult resolutionResult = projectDependenciesResolver.resolve(request);
        return new EclipseAetherDependencyNode(resolutionResult);
    }
}
