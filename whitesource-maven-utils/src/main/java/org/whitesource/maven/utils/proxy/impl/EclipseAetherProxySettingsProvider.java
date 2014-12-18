package org.whitesource.maven.utils.proxy.impl;

import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.whitesource.maven.utils.Invoker;
import org.whitesource.maven.utils.proxy.ProxySettings;
import org.whitesource.maven.utils.proxy.ProxySettingsProvider;

/**
 * Author: Itai Marko
 */
public class EclipseAetherProxySettingsProvider implements ProxySettingsProvider {


    /* --- Private Members --- */

    private Proxy proxy;
    RemoteRepository.Builder remoteRepositoryBuilder;
    RepositorySystemSession repositorySystemSession;


    /* --- Constructors --- */

    public EclipseAetherProxySettingsProvider(String url, MavenSession session) {
        validateArgs(url, session);
        remoteRepositoryBuilder = new RemoteRepository.Builder(null, null, url);
        RemoteRepository dummyRepo = remoteRepositoryBuilder.build();
        repositorySystemSession = (RepositorySystemSession) Invoker.invoke(session, "getRepositorySession");
        proxy = repositorySystemSession.getProxySelector().getProxy(dummyRepo);
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
            RemoteRepository dummyRepo = remoteRepositoryBuilder.setAuthentication(auth).build();
            AuthenticationContext authenticationContext = AuthenticationContext.forRepository( repositorySystemSession, dummyRepo);
            try {
                auth.fill(authenticationContext, null, null);
                username = authenticationContext.get(AuthenticationContext.USERNAME, String.class);
                password = authenticationContext.get(AuthenticationContext.PASSWORD, String.class);
            } finally {
                AuthenticationContext.close(authenticationContext);
            }
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
            throw  new IllegalArgumentException("MavenSession can't be null");
        }
    }
}
