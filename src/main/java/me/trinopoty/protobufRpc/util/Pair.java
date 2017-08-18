package me.trinopoty.protobufRpc.util;

public final class Pair<K, V> {

    private final K mKey;
    private final V mValue;

    public Pair(K key, V value) {
        mKey = key;
        mValue = value;
    }

    public K getKey() {
        return mKey;
    }

    public V getValue() {
        return mValue;
    }
}