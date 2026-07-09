package com.mattoid.scheduled.datasource;

import lombok.Data;

/**
 * SSH 多跳链路中的单个中转节点配置。
 * <p>
 * 节点按从服务侧到请求侧的顺序排列（越靠近请求方越靠后），
 * 建立隧道时会反向逐跳连接。
 */
@Data
public class SshHopConfig {

    private String host;

    private Integer port;

    private String username;

    private String password;

    private String privateKey;

    private String passphrase;

    /**
     * PASSWORD / KEY
     */
    private String authType;
}
