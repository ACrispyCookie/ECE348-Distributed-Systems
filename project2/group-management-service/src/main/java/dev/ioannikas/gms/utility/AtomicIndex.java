package dev.ioannikas.gms.utility;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class AtomicIndex<K, V> {

    private final Map<K, V> index = new ConcurrentHashMap<>();
    private final Function<V, K> keyGetter;

    public AtomicIndex(Function<V, K> keyGetter) {
        this.keyGetter = keyGetter;
    }

    public V get(K key) {
        return index.get(key);
    }

    public V add(V value) {
        return index.put(keyGetter.apply(value), value);
    }
}
