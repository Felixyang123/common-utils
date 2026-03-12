package com.lezai.idempotent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LockStrategy {
    REDIS,
    LOCAL
}
