package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.pojo.bean.ConfigSnapshot;

import java.util.List;

/**
 * 绾跨▼姹犻厤缃�鐗堟湰蹇�鐓у瓨鍌ㄦ帴鍙?
 * <p>
 * 涓庡�¤�℃棩蹇楋紙{@code operate_log}锛夎亴璐ｅ垎绂伙細
 * 蹇�鐓у彧瀛�"鏌愭椂闂寸偣閰嶇疆鏄�浠�涔堟牱"锛堝崟鍊?+ version锛夛紝涓嶅瓨鎿嶄綔绫诲瀷銆?
 * version 鎸?{@code (appId, poolName)} 缁村害閫掑�炪�?
 */
public interface ConfigSnapshotStorage {

    /**
     * 璁板綍涓�鏉￠厤缃�蹇�鐓?
     *
     * @param appId    搴旂敤ID
     * @param poolName 绾跨▼姹犲悕绉?
     * @param value    蹇�鐓у�硷紙鍒犻櫎鏃跺彲涓?null锛?
     * @param operator 鎿嶄綔浜?
     * @return 鏂板啓鍏ョ殑蹇�鐓�
     */
    ConfigSnapshot recordSnapshot(String appId, String poolName, ThreadPoolConfig value, String operator);

    /**
     * 鑾峰彇鎸囧畾绾跨▼姹犵殑鎵�鏈夊揩鐓э紙鎸?version 闄嶅簭锛?
     */
    List<ConfigSnapshot> getSnapshots(String appId, String poolName);

    /**
     * 鑾峰彇鎸囧畾绾跨▼姹犵殑蹇�鐓э紙闄愬埗鏉℃暟锛屾�?version 闄嶅簭锛?
     */
    List<ConfigSnapshot> getSnapshots(String appId, String poolName, int limit);

    /**
     * 鎸?version 绮剧‘鑾峰彇鏌愭潯蹇�鐓э紙鍥炴粴鐢�锛?
     */
    ConfigSnapshot getByVersion(String appId, String poolName, long version);
}


