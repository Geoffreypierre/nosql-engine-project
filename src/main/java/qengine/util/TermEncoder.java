package qengine.util;

import java.util.HashMap;
import java.util.Map;

import fr.boreal.model.logicalElements.api.Term;

public class TermEncoder {

    private final Map<Integer, Term> decodingMap = new HashMap<>();
    private final Map<String, Integer> encodedTerms = new HashMap<>();
    private int count = 0;

    public int encode(Term value) {
        if (encodedTerms.containsKey(value.label())) {
            return encodedTerms.get(value.label());
        }
        encodedTerms.put(value.label(), count);
        decodingMap.put(count++, value);
        return count;
    }

    public Term decode(int code) {
        return decodingMap.get(code);
    }

}
