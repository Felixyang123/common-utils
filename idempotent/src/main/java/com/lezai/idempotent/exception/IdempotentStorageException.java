package com.lezai.idempotent.exception;

/**
 * 幂等性存储异常
 *
 * <p>当幂等记录存储层不可用时抛出(连接失败 / 反序列化失败 / IO 异常等)。
 * 此类异常意味着"我们不知道幂等记录是否存在",与返回 {@code null}
 * (表示"记录确定不存在")语义完全不同。</p>
 *
 * <p>调用方收到此异常后应当 fail-fast,映射为 HTTP 503 + Retry-After 头,
 * 告知客户端本次服务端无法给出幂等判定,客户端可在稍后安全重试。参见
 * ADR-0002。</p>
 *
 * @see com.lezai.idempotent.storage.IdempotentStorage
 */
public class IdempotentStorageException extends IdempotentException {

    public IdempotentStorageException(String message) {
        super(message);
    }

    public IdempotentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
