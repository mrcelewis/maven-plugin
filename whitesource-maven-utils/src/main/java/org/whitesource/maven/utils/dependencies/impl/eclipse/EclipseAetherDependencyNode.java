package org.whitesource.maven.utils.dependencies.impl.eclipse;

import org.apache.maven.project.DependencyResolutionResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.whitesource.maven.utils.Invoker;
import org.whitesource.maven.utils.dependencies.AetherDependency;
import org.whitesource.maven.utils.dependencies.AetherDependencyNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: Itai Marko
 */
public class EclipseAetherDependencyNode implements AetherDependencyNode {

    private DependencyNode delegate;
    private AetherDependency dependency;
    private List<AetherDependencyNode> children;

    public EclipseAetherDependencyNode(DependencyResolutionResult dependencyResolutionResult) {
        this((DependencyNode)Invoker.invoke(DependencyResolutionResult.class, dependencyResolutionResult, "getDependencyGraph"));
    }

    private EclipseAetherDependencyNode(DependencyNode delegateDependencyNode) {
        this.delegate = delegateDependencyNode;
        Dependency delegateDependency = delegate.getDependency();
        this.dependency = delegateDependency == null ? null : new EclipseAetherDependency(delegateDependency);
        List<DependencyNode> delegateChildren = delegate.getChildren();
        this.children = new ArrayList<AetherDependencyNode>(delegateChildren.size());
        for (DependencyNode delegateChild : delegateChildren) {
            children.add(new EclipseAetherDependencyNode(delegateChild));
        }
    }

    @Override
    public List<AetherDependencyNode> getChildren() {
        return children;
    }

    @Override
    public AetherDependency getDependency() {
        return dependency;
    }
}
