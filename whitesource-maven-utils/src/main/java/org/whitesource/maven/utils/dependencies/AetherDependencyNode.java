package org.whitesource.maven.utils.dependencies;

import java.util.List;

/**
 * Author: Itai Marko
 */
public interface AetherDependencyNode {

    List<AetherDependencyNode> getChildren();

    AetherDependency getDependency();
}
