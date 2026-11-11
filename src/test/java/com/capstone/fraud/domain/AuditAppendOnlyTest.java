package com.capstone.fraud.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditAppendOnlyTest {

    @Test
    void updateIsBlocked() {
        AuditEvent e = new AuditEvent();
        assertThrows(IllegalStateException.class, e::blockUpdate);
    }

    @Test
    void deleteIsBlocked() {
        AuditEvent e = new AuditEvent();
        assertThrows(IllegalStateException.class, e::blockDelete);
    }
}
