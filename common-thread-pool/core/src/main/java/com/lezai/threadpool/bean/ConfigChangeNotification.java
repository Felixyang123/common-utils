package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 配置变更通知：长轮询 subscribe 接口的响应体。
 * <p>
 * 仅携带 {appId, version}，不携带全量配置——服务端高频变更时直接推送全量配置会导致
 * 脏写客户端缓存（见 CONTEXT.md「订阅通知协议」）。客户端收到通知后应主动调用
 * pull 接口拉取最新的全量配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigChangeNotification implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用 ID
     */
    private String appId;

    /**
     * 变更后的配置版本号
     */
    private long version;
}
