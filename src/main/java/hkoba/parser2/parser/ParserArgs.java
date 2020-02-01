package hkoba.parser2.parser;

import hkoba.parser2.TokenData;

import java.util.*;
import java.util.function.Function;

public class ParserArgs extends ArrayList<TokenData> {
    private Map<Class, Integer> classIndexMap = new HashMap<>();
    private Map<String, Integer> nameIndexMap = new HashMap<>();

    public ParserArgs(List<TokenData> args) {
        super(args);
    }

    public <T> Optional<T> getValue(Class<T> clazz) {
        List<T> res = getValues(clazz);
        int ix;
        if (classIndexMap.containsKey(clazz)) {
            ix = classIndexMap.get(clazz) + 1;
        } else {
            ix = 0;
        }
        if (ix < res.size()) {
            classIndexMap.put(clazz, ix);
            return Optional.of(res.get(ix));
        }
        return Optional.empty();
    }

    public <T> Optional<T> getValue(String name) {
        List<T> res = getValues(name);
        int ix;
        if (nameIndexMap.containsKey(name)) {
            ix = classIndexMap.get(name) + 1;
        } else {
            ix = 0;
        }
        if (ix < res.size()) {
            nameIndexMap.put(name, ix);
            return Optional.of(res.get(ix));
        }
        return Optional.empty();
    }

    public <T> List<T> getValues(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (TokenData data : this) {
            Object value = data.getValue();
            if (clazz.isInstance(value)) {
                result.add((T) value);
            }
        }
        return result;
    }

    public <T> List<T> getValues(String name) {
        return new ArrayList<>();
    }


}
