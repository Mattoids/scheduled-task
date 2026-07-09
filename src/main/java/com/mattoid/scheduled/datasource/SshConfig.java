package com.mattoid.scheduled.datasource;

import lombok.Data;

import java.util.List;

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

    /**
     * SSH 多跳链路中的中转节点，按从服务侧到请求侧的顺序排列。
     * <p>
     * 第一个节点最靠近服务所在机器，最后一个节点最靠近请求方（最外层代理）。
     * 为空时表示直连服务所在机器。
     */
    private List<SshHopConfig> hops;
}
