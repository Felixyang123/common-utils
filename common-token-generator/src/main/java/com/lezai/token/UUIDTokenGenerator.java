package com.lezai.token;

import java.util.UUID;

public class UUIDTokenGenerator implements TokenGenerator<DefaultTokenContext>{
    @Override
    public String generateToken(DefaultTokenContext ctx) {
        return UUID.randomUUID().toString();
    }
}
