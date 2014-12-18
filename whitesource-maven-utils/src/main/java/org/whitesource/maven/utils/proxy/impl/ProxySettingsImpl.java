package org.whitesource.maven.utils.proxy.impl;

import org.whitesource.maven.utils.proxy.ProxySettings;

/**
 * Author: Itai Marko
 */
public class ProxySettingsImpl implements ProxySettings {

    /* --- Private Members --- */

    private final String hostname;
    private final int port;
    private final String username;
    private final String password;


    /* --- Constructors --- */

    ProxySettingsImpl(String hostname, int port, String username, String password) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
    }


    /* ---  ProxySettings implementation --- */

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }
}
