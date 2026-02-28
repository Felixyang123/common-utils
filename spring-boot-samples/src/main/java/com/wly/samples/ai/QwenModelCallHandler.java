package com.wly.samples.ai;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QwenModelCallHandler implements ModelCallHandler {
    private String apiKey = "";
    private String size = "1024*1536";
    private String model = "qwen-image-edit-max";

    @Override
    public String editImage(EditImageDto dto) {
        if (StringUtils.isBlank(dto.getPrompt()) || CollectionUtils.isEmpty(dto.getImages())) {
            throw new RuntimeException("图片编辑的图片和提示词不能为空");
        }

        MultiModalConversationParam param = buildParam(dto);
        try {
            MultiModalConversation conv = new MultiModalConversation();
            log.info("edit image call model, input: {}", JSON.toJSONString(param));
            MultiModalConversationResult result = conv.call(param);
            log.info("edit image call model, output: {}", JSON.toJSONString(result));
            return processResult(result);
        } catch (Exception e) {
            log.error("edit image call model error", e);
            throw new RuntimeException("图片编辑失败");
        }
    }

    private static String processResult(MultiModalConversationResult result) {
        if (result == null || result.getOutput() == null || CollectionUtils.isEmpty(result.getOutput().getChoices())) {
            return null;
        }
        MultiModalConversationOutput.Choice choice = result.getOutput().getChoices().getFirst();
        if (choice == null || choice.getMessage() == null || CollectionUtils.isEmpty(choice.getMessage().getContent())) {
            return null;
        }

        return Optional.ofNullable(choice.getMessage().getContent().getFirst()).map(content ->
                String.valueOf(content.get("image"))).orElse(null);
    }

    private MultiModalConversationParam buildParam(EditImageDto dto) {
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Collections.singletonMap("text", dto.getPrompt()));
        for (String image : dto.getImages()) {
            contents.add(Collections.singletonMap("image", image));
        }

        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue()).content(contents).build();
        // qwen-image-edit-max、qwen-image-edit-plus系列支持输出1-6张图片，此处以2张为例
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("watermark", false);
        parameters.put("negative_prompt", " ");
        parameters.put("n", 1);
        parameters.put("prompt_extend", true);
        String size = StringUtils.isBlank(dto.getSize()) ? this.size : dto.getSize();
        parameters.put("size", size);

        return MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(Collections.singletonList(userMessage))
                .parameters(parameters)
                .build();
    }
}
