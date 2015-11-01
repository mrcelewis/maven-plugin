package org.whitesource.maven.params;

import org.apache.maven.plugins.annotations.Parameter;
import org.whitesource.maven.Constants;

/**
 * POJO for holding parameters regarding aggregating modules.
 *
 * @author tom.shapira
 */
public class AggregateModules {

    /* --- Members --- */

    /**
     * Set to true to combine all pom modules into a single project with an aggregated dependency flat list (no hierarchy).
     */
    @Parameter(alias = "enabled", property = Constants.ENABLED, required = true, defaultValue = "false")
    private boolean enabled;

    /**
     * Optional. The project name that will appear in WhiteSource.
     * If omitted and no project token defined, defaults to pom artifactId.
     */
    @Parameter(alias = "projectName", property = Constants.PROJECT_NAME, required = false)
    private String projectName;

    /**
     * Optional. Unique identifier of the White Source project to update.
     * If omitted, default naming convention will apply.
     */
    @Parameter(alias = "projectToken", property = Constants.PROJECT_TOKEN, required = false)
    private String projectToken;

    /* --- Getters / Setters --- */

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectToken() {
        return projectToken;
    }

    public void setProjectToken(String projectToken) {
        this.projectToken = projectToken;
    }
}