package org.whitesource.maven.utils.dependencies;

import java.util.Collection;

/**
 * Author: Itai Marko
 */
public interface AetherDependency {

    String getScope();

    AetherArtifact getArtifact();

    boolean isOptional();

    Collection<AetherExclusion> getExclusions();
}
