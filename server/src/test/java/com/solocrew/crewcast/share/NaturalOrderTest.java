package com.solocrew.crewcast.share;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalOrderTest {

    @Test
    void file2BeforeFile10() {
        assertTrue(NaturalOrder.compareIgnoreCase("file2.txt", "file10.txt") < 0);
        assertTrue(NaturalOrder.compareIgnoreCase("File2", "file10") < 0);
    }
}
