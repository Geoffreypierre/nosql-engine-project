package qengine.util;

import java.util.HashMap;
import java.util.List;

public class SearchTree<T> extends HashMap<T, HashMap<T, List<T>>> {
    public SearchTree() {
        super();
    }
}
