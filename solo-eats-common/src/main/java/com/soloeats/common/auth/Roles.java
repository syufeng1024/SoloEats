package com.soloeats.common.auth;

import java.util.Set;

public final class Roles {

    public static final String C_USER = "C_USER";
    public static final String MERCHANT = "MERCHANT";
    public static final String RIDER = "RIDER";
    public static final String ADMIN = "ADMIN";

    public static final Set<String> ALL = Set.of(C_USER, MERCHANT, RIDER, ADMIN);

    private Roles() {
    }
}
