package com.lezai.token;

import lombok.Data;

@Data
public class DefaultTokenContext implements TokenContext {
    private String token;
}
