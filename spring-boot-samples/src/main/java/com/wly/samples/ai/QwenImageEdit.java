package com.wly.samples.ai;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;

import java.io.IOException;
import java.util.*;

public class QwenImageEdit {

    static {
        // 以下为中国（北京）地域url，若使用新加坡地域的模型，需将url替换为：https://dashscope-intl.aliyuncs.com/api/v1
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
    }

    // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    // 若没有配置环境变量，请用百炼 API Key 将下行替换为：apiKey="sk-xxx"
    static String apiKey = "sk-xxx";

    public static void call() throws ApiException, NoApiKeyException, UploadFileException, IOException {

        MultiModalConversation conv = new MultiModalConversation();

        // 模型支持输入1-3张图片
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        Collections.singletonMap("image", "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250925/thtclx/input1.png"),
                        Collections.singletonMap("image", "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250925/iclsnx/input2.png"),
                        Collections.singletonMap("image", "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250925/gborgw/input3.png"),
                        Collections.singletonMap("text", "图1中的女生穿着图2中的黑色裙子按图3的姿势坐下")
                )).build();
        // qwen-image-edit-max、qwen-image-edit-plus系列支持输出1-6张图片，此处以2张为例
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("watermark", false);
        parameters.put("negative_prompt", " ");
        parameters.put("n", 1);
        parameters.put("prompt_extend", true);
        parameters.put("size", "1024*1536");

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model("qwen-image-edit-max")
                .messages(Collections.singletonList(userMessage))
                .parameters(parameters)
                .build();

        MultiModalConversationResult result = conv.call(param);
        // 如需查看完整响应，请取消下行注释
        // System.out.println(JsonUtils.toJson(result));
        List<Map<String, Object>> contentList = result.getOutput().getChoices().get(0).getMessage().getContent();
        int imageIndex = 1;
        for (Map<String, Object> content : contentList) {
            if (content.containsKey("image")) {
                System.out.println("输出图像" + imageIndex + "的URL：" + content.get("image"));
                imageIndex++;
            }
        }
    }

    public static void main(String[] args) {
        try {
            call();
        } catch (ApiException | NoApiKeyException | UploadFileException | IOException e) {
            System.out.println(e.getMessage());
        }
    }
}