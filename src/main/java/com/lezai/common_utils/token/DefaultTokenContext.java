package com.lezai.common_utils.token;

import lombok.Data;

@Data
public class DefaultTokenContext implements TokenContext {
    private String token;
}
