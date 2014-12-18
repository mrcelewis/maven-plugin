package org.whitesource.maven.utils.proxy;

/**
 * Author: Itai Marko
 */
public interface ProxySettingsProvider {

    boolean isProxyConfigured();

    ProxySettings getProxySettings();
}
