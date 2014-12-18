package org.whitesource.maven.utils.proxy;

import org.apache.maven.execution.MavenSession;
import org.whitesource.maven.utils.proxy.impl.EclipseAetherProxySettingsProvider;
import org.whitesource.maven.utils.proxy.impl.SonatypeAetherProxySettingsProvider;

/**
 * Author: Itai Marko
 */
public class ProxySettingsProviderFactory {

    private final static boolean isEclipseAetherLoaded;

    static {
        isEclipseAetherLoaded = isExistsInClasspath("org.eclipse.aether.repository.Proxy");
    }

    private static boolean isExistsInClasspath(String className) {
        try {
            Thread.currentThread().getContextClassLoader().loadClass(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static ProxySettingsProvider getProxySettingsProviderForUrl(String url, MavenSession session) {
        if (isEclipseAetherLoaded) {
            return new EclipseAetherProxySettingsProvider(url, session);
        } else {
            return new SonatypeAetherProxySettingsProvider(url, session);
        }
    }

    // prevent instantiation
    private ProxySettingsProviderFactory() {}
}
