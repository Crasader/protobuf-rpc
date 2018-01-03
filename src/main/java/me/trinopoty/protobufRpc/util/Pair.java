package me.trinopoty.protobufRpc.util;

public final class Pair<K, V> {

    private final K mFirst;
    private final V mSecond;

    public Pair(K first, V second) {
        mFirst = first;
        mSecond = second;
    }

    public K getFirst() {
        return mFirst;
    }

    public V getSecond() {
        return mSecond;
    }
}