package com.wly.samples.feishurobot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public class FeishuBotWithSign {
    private static final String WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/ad683ec8-6bb8-4658-bae9-0c68da473888";
    private static final String SECRET = "mqEeO0nPssgD8nUb4nxzV"; // 从配置或环境变量读取！

    public static void main(String[] args) throws Exception {
        sendTextMessage("图生图\n总数：4\n成功：0\n失败：0\n运行中：4\n");
    }

    public static void sendTextMessage(String text) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String sign = generateSign(timestamp);

        ObjectMapper mapper = new ObjectMapper();
        var payload = Map.of(
            "timestamp", timestamp,
            "sign", sign,
            "msg_type", "text",
            "content", Map.of("text", text)
        );

        String json = mapper.writeValueAsString(payload);
        sendPostRequest(json);
    }

    private static String generateSign(long timestamp) throws Exception {
        String stringToSign = timestamp + "\n" + SECRET;
        //使用HmacSHA256算法计算签名
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(new byte[]{});
        return new String(Base64.encodeBase64(signData));
    }

    private static void sendPostRequest(String json) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(WEBHOOK_URL);
            httpPost.setHeader("Content-Type", "application/json; charset=utf-8");
            httpPost.setEntity(new StringEntity(json, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                int statusCode = response.getStatusLine().getStatusCode();

                System.out.println("Status: " + statusCode);
                System.out.println("Response: " + responseBody);

                // 可选：检查返回 code 是否为 0（成功）
                // 飞书成功返回: {"code":0,"msg":"ok","data":{...}}
            }
        }
    }
}