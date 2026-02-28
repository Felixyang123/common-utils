package com.wly.samples.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditImageDto {

    private String prompt;

    private List<String> images;

    /**
     * 返回图片的规格，eg："1024*1536"
     */
    private String size;
}
