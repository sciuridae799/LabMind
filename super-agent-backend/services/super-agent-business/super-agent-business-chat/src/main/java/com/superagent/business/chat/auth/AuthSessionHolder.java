package com.superagent.business.chat.auth;

public final class AuthSessionHolder {

    private static final ThreadLocal<AuthSessionContext> HOLDER = new ThreadLocal<>();

    private AuthSessionHolder() {
    }

    public static void set(AuthSessionContext session) {
        HOLDER.set(session);
    }

    public static AuthSessionContext required() {
        AuthSessionContext session = HOLDER.get();
        if (session == null) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED, "请先登录后再访问实验室资料服务");
        }
        return session;
    }

    public static AuthSessionContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
