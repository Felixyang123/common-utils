package com.lezai.taskflow.idgen;

import java.util.UUID;

public class UUIDGenerator implements IdGenerator {
    @Override
    public Long getIdNum() {
        throw new UnsupportedOperationException("not support");
    }

    @Override
    public String getIdStr() {
        return UUID.randomUUID().toString();
    }
}
