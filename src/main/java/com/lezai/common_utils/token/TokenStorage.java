package com.lezai.common_utils.token;

public interface TokenStorage<KEY, VAL> {
    VAL getToken(KEY key);

    boolean setToken(KEY key, VAL value);

    VAL deleteToken(KEY key);
}
