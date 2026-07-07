package com.mattoid.scheduled.datasource;

import lombok.Data;

@Data
public class SshConfig {

    private String host;

    private Integer port;

    private String username;

    private String password;

    private String privateKey;

    private String passphrase;

    private Integer localPort;

    private String remoteHost;

    private Integer remotePort;

    /**
     * PASSWORD / KEY
     */
    private String authType;
}
