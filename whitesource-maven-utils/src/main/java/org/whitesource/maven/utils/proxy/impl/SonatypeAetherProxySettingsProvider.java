package org.whitesource.maven.utils.proxy.impl;

import org.apache.maven.execution.MavenSession;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.RemoteRepository;
import org.whitesource.maven.utils.proxy.ProxySettings;
import org.whitesource.maven.utils.proxy.ProxySettingsProvider;

/**
 * Author: Itai Marko
 */
public class SonatypeAetherProxySettingsProvider implements ProxySettingsProvider {


    /* --- Private Members --- */

    private Proxy proxy;


    /* --- Constructors --- */

    public SonatypeAetherProxySettingsProvider(String url, MavenSession session) {
        validateArgs(url, session);
        RemoteRepository dummyRepo = new RemoteRepository().setUrl(url);
        proxy = session.getRepositorySession().getProxySelector().getProxy(dummyRepo);
    }


    /* --- ProxySettingsProvider implementation --- */

    @Override
    public boolean isProxyConfigured() {
        return proxy != null;
    }

    @Override
    public ProxySettings getProxySettings() {
        if (!isProxyConfigured()) {
            return null;
        }

        String username = null;
        String password = null;
        final Authentication auth = proxy.getAuthentication();
        if (auth != null) {
            username = auth.getUsername();
            password = auth.getPassword();
        }
        String host = proxy.getHost();
        int port = proxy.getPort();

        return new ProxySettingsImpl(host, port, username, password);
    }


    /* --- Private Methods --- */

    private void validateArgs(String url, MavenSession session) {
        if (url == null) {
            throw new IllegalArgumentException("URL can't be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("MavenSession can't be null");
        }
    }
}
