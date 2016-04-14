package com.cmenguy.monitor.hashtags.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Some helper functions related to JSON manipulation.
 */
public class JsonUtils {

    // traverse a map and remove anything that is empty (otherwise refused by protobuf)
    public static void removeEmptyLists(Map<String, Object> map) {
        for (Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Object> entry = it.next();

            if (entry.getValue() == null) {
                it.remove();
            } else if (entry.getKey().equals("coordinates")) { // hack because schema for coordinates is inconsistent
                it.remove();
            } else if (entry.getValue() instanceof List) {
                if (((ArrayList<?>) entry.getValue()).size() == 0) {
                    it.remove();
                }
            } else if (entry.getValue() instanceof Map) {
                if (((Map)entry.getValue()).size() == 0) {
                    it.remove();
                } else {
                    removeEmptyLists((Map) entry.getValue());
                    if (((Map)entry.getValue()).size() == 0) {
                        it.remove();
                    }
                }
            }
        }
    }

}
