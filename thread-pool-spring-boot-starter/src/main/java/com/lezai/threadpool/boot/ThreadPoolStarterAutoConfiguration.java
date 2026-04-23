package com.lezai.threadpool.starter;

import com.lezai.threadpool.config.ThreadPoolAutoConfiguration;
import com.lezai.threadpool.properties.ThreadPoolProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * 线程池 Spring Boot Starter 自动配置类
 *
 * <p>支持三种模式：</p>
 * <ul>
 *     <li>LOCAL - 本地静态模式：使用配置文件中的静态配置，不监听变更</li>
 *     <li>FILE - 文件动态模式：监听配置文件变更，动态更新线程池</li>
 *     <li>CS - CS 动态模式：通过客户端 - 服务端模式，从服务端拉取配置</li>
 *     <li>NACOS - Nacos 动态模式：监听 Nacos 配置中心，动态更新线程池</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * # LOCAL 模式（默认）
 * thread:
 *   pool:
 *     mode: LOCAL
 *     default-pool:
 *       core-pool-size: 10
 *       maximum-pool-size: 20
 *
 * # FILE 模式
 * thread:
 *   pool:
 *     mode: FILE
 *     file-watch:
 *       config-path: config/thread-pool.yaml
 *       watch-interval-ms: 3000
 *
 * # CS 模式（客户端）
 * thread:
 *   pool:
 *     mode: CS
 *     cs:
 *       server:
 *         enabled: false
 *       client:
 *         server-url: http://localhost:8088
 *         app-id: my-app
 *
 * # CS 模式（服务端）
 * thread:
 *   pool:
 *     mode: CS
 *     cs:
 *       server:
 *         enabled: true
 *         config-path: config/thread-pool-config.json
 *
 * # NACOS 模式
 * thread:
 *   pool:
 *     mode: NACOS
 *     nacos:
 *       server-addr: localhost:8848
 *       data-id: thread-pool-config
 *       group: DEFAULT_GROUP
 * }</pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@Import(ThreadPoolAutoConfiguration.class)
public class ThreadPoolStarterAutoConfiguration {
}
