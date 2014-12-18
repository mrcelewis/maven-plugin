package org.whitesource.maven.utils.dependencies.impl.sonatype;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.*;
import org.sonatype.aether.RepositorySystemSession;
import org.whitesource.maven.utils.Invoker;
import org.whitesource.maven.utils.dependencies.AetherDependencyNode;

/**
 * Author: Itai Marko
 */
public class SonatypeAetherDependencyGraphBuilder {

    private MavenProject project;
    private ProjectDependenciesResolver projectDependenciesResolver;
    private MavenSession session;

    public SonatypeAetherDependencyGraphBuilder(MavenProject project, ProjectDependenciesResolver projectDependenciesResolver, MavenSession session) {
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
        return new SonatypeAetherDependencyNode(resolutionResult);
    }
}
