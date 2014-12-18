package org.whitesource.maven.utils.proxy;

/**
 * Author: Itai Marko
 */
public interface ProxySettings {

    String getHostname();

    int getPort();

    String getUsername();

    String getPassword();
}
