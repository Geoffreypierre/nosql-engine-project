package qengine.util;

import java.util.HashMap;

public class TermEncoder {

    private HashMap<Integer, String> decodingMap;
    private int count = 0;

    public int encode(String value) {
        decodingMap.put(count++,value);
        return count;
    }

    public String decode(int code) {
        return decodingMap.get(code);
    }

}
