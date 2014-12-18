package org.whitesource.maven.utils.dependencies.impl.eclipse;

import org.eclipse.aether.graph.Exclusion;
import org.whitesource.maven.utils.dependencies.AetherExclusion;

/**
 * Author: Itai Marko
 */
class EclipseAetherExclusion implements AetherExclusion {

    private final Exclusion delegate;

    EclipseAetherExclusion(Exclusion delegateExclusion) {
        this.delegate = delegateExclusion;
    }

    @Override
    public String getArtifactId() {
        return delegate.getArtifactId();
    }

    @Override
    public String getGroupId() {
        return delegate.getGroupId();
    }
}
