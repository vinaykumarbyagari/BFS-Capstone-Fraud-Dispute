package com.capstone.fraud.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a == null || !a.isAuthenticated()) ? "system" : a.getName();
    }
}
