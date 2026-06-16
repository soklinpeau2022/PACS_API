package com.ut.emrPacs.authentication.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PasswordPolicyTest {

    @Test
    void passwordChangeShouldAllowSimplePasswords() {
        assertNull(PasswordPolicy.validatePasswordChange("a"));
        assertNull(PasswordPolicy.validatePasswordChange("abc123"));
    }

    @Test
    void passwordChangeShouldAllowSpaces() {
        assertNull(PasswordPolicy.validatePasswordChange("my simple password"));
    }

    @Test
    void passwordChangeShouldRejectEmptyPassword() {
        assertEquals("Password is required.", PasswordPolicy.validatePasswordChange(""));
        assertEquals("Password is required.", PasswordPolicy.validatePasswordChange(null));
    }
}
