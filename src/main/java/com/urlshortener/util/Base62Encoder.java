package com.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    private static final String ALLOWED_STRING = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final char[] ALLOWED_CHARACTERS = ALLOWED_STRING.toCharArray();
    private static final int BASE = ALLOWED_CHARACTERS.length;

    public String encode(long input) {
        if (input == 0) {
            return String.valueOf(ALLOWED_CHARACTERS[0]);
        }
        StringBuilder encodedString = new StringBuilder();
        while (input > 0) {
            encodedString.append(ALLOWED_CHARACTERS[(int) (input % BASE)]);
            input = input / BASE;
        }
        return encodedString.reverse().toString();
    }

    public long decode(String input) {
        long decodedString = 0;
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            int position = ALLOWED_STRING.indexOf(character);
            decodedString = (decodedString * BASE) + position;
        }
        return decodedString;
    }
}
