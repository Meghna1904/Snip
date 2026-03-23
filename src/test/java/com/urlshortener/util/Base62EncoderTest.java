package com.urlshortener.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void testEncodeDecode() {
        long originalId = 100000L;
        String encoded = encoder.encode(originalId);
        long decoded = encoder.decode(encoded);

        assertNotNull(encoded);
        assertEquals(originalId, decoded);
    }

    @Test
    void testZero() {
        assertEquals("a", encoder.encode(0)); // 'a' is the first char in ALLOWED_STRING
        assertEquals(0, encoder.decode("a"));
    }

    @Test
    void testLargeNumber() {
        long largeNumber = 999999999999L;
        String encoded = encoder.encode(largeNumber);
        long decoded = encoder.decode(encoded);

        assertEquals(largeNumber, decoded);
        assertTrue(encoded.length() < String.valueOf(largeNumber).length());
    }
}
