package org.whitesource.maven.utils.dependencies.impl.eclipse;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.whitesource.maven.utils.dependencies.AetherArtifact;
import org.whitesource.maven.utils.dependencies.AetherDependency;
import org.whitesource.maven.utils.dependencies.AetherExclusion;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Author: Itai Marko
 */
public class EclipseAetherDependency implements AetherDependency {

    private final Dependency delegate;
    private AetherArtifact artifact;
    private Collection<AetherExclusion> exclusions;

    EclipseAetherDependency(Dependency delegateDependency) {
        this.delegate = delegateDependency;
        this.artifact = new EclipseAetherArtifact(delegate.getArtifact());
        Collection<Exclusion> delegateExclusions = delegate.getExclusions();
        exclusions = new ArrayList<AetherExclusion>(delegateExclusions.size());
        for (Exclusion delegateExclusion : delegateExclusions) {
            exclusions.add(new EclipseAetherExclusion(delegateExclusion));
        }
    }

    @Override
    public String getScope() {
        return delegate.getScope();
    }

    @Override
    public AetherArtifact getArtifact() {
        return artifact;
    }

    @Override
    public boolean isOptional() {
        return delegate.isOptional();
    }

    @Override
    public Collection<AetherExclusion> getExclusions() {
        return exclusions;
    }
}
