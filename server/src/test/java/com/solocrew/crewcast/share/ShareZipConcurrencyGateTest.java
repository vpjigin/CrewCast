package com.solocrew.crewcast.share;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareZipConcurrencyGateTest {

    @Test
    void allowsUpToMaxConcurrent() {
        ShareZipConcurrencyGate gate = new ShareZipConcurrencyGate(2);
        assertTrue(gate.tryEnter());
        assertTrue(gate.tryEnter());
        assertFalse(gate.tryEnter());
        gate.exit();
        assertTrue(gate.tryEnter());
    }
}
