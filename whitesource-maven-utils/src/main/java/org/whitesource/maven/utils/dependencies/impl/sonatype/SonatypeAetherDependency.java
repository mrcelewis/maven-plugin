package org.whitesource.maven.utils.dependencies.impl.sonatype;

import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.Exclusion;
import org.whitesource.maven.utils.dependencies.AetherArtifact;
import org.whitesource.maven.utils.dependencies.AetherDependency;
import org.whitesource.maven.utils.dependencies.AetherExclusion;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Author: Itai Marko
 */
class SonatypeAetherDependency implements AetherDependency {

    private final Dependency delegate;
    private final SonatypeAetherArtifact artifact;
    private final Collection<AetherExclusion> exclusions;

    SonatypeAetherDependency(Dependency delegateDependency) {
        this.delegate = delegateDependency;
        this.artifact = new SonatypeAetherArtifact(delegate.getArtifact());
        Collection<Exclusion> delegateExclusions = delegate.getExclusions();
        this.exclusions = new ArrayList<AetherExclusion>(delegateExclusions.size());
        for (Exclusion delegateExclusion : delegateExclusions) {
            exclusions.add(new SonatypeAetherExclusion(delegateExclusion));
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
