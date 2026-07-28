package com.lezai.samples.cache.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * 自动探测本机非回环 IPv4 地址。
 * 探测优先级：站点本地地址（局域网） > 其他非回环地址。
 * 探测失败打印 warn 日志并返回 null，调用方应跳过后续操作。
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocalNodeAddressDetector {

    /**
     * 探测本机非回环 IPv4 地址。
     *
     * @return 本机 IPv4，探测失败返回 null
     */
    public static String detectIp() {
        try {
            String siteLocal = null;
            String otherNonLoopback = null;

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                        continue;
                    }
                    if (addr.isSiteLocalAddress()) {
                        // 站点本地地址（10.x / 172.16-31.x / 192.168.x）最优，直接返回
                        return addr.getHostAddress();
                    }
                    if (otherNonLoopback == null) {
                        otherNonLoopback = addr.getHostAddress();
                    }
                }
            }

            if (siteLocal != null) return siteLocal;
            if (otherNonLoopback != null) return otherNonLoopback;

        } catch (SocketException e) {
            log.warn("Failed to enumerate network interfaces, skip node address registration", e);
            return null;
        }

        log.warn("No non-loopback IPv4 address found on any active network interface, skip node address registration");
        return null;
    }

    /**
     * 拼接 IP:Port 节点地址。
     *
     * @param port 节点端口
     * @return 形如 "192.168.1.10:8080" 的地址，探测失败返回 null
     */
    public static String detectAddress(int port) {
        String ip = detectIp();
        return ip != null ? ip + ":" + port : null;
    }
}
