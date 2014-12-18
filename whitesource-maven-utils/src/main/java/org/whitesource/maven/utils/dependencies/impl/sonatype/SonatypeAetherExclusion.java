package org.whitesource.maven.utils.dependencies.impl.sonatype;

import org.sonatype.aether.graph.Exclusion;
import org.whitesource.maven.utils.dependencies.AetherExclusion;

/**
 * Author: Itai Marko
 */
class SonatypeAetherExclusion implements AetherExclusion {

    private final Exclusion delegate;

    SonatypeAetherExclusion(Exclusion delegateExclusion) {
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
