package org.whitesource.maven.utils.dependencies.impl.sonatype;

import org.apache.maven.project.DependencyResolutionResult;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.whitesource.maven.utils.Invoker;
import org.whitesource.maven.utils.dependencies.AetherDependency;
import org.whitesource.maven.utils.dependencies.AetherDependencyNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: Itai Marko
 */
public class SonatypeAetherDependencyNode implements AetherDependencyNode {

    private final DependencyNode delegate;
    private SonatypeAetherDependency dependency;
    private List<AetherDependencyNode> children;

    public SonatypeAetherDependencyNode(DependencyResolutionResult dependencyResolutionResult) {
        this((DependencyNode) Invoker.invoke(DependencyResolutionResult.class, dependencyResolutionResult, "getDependencyGraph"));
    }

    private SonatypeAetherDependencyNode(DependencyNode delegateDependencyNode) {
        this.delegate = delegateDependencyNode;
        Dependency delegateDependency = delegate.getDependency();
        this.dependency = delegateDependency == null ? null : new SonatypeAetherDependency(delegateDependency);
        List<DependencyNode> delegateChildren = delegate.getChildren();
        this.children = new ArrayList<AetherDependencyNode>(delegateChildren.size());
        for (DependencyNode delegateChild : delegateChildren) {
            children.add(new SonatypeAetherDependencyNode(delegateChild));
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
