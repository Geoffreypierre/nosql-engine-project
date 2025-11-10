package qengine.util;

import java.util.HashMap;
import java.util.Map;

public class TermEncoder {

    private final Map<Integer, String> decodingMap = new HashMap<>();
    private int count = 0;

    public int encode(String value) {
        decodingMap.put(count++, value);
        return count;
    }

    public String decode(int code) {
        return decodingMap.get(code);
    }

}
