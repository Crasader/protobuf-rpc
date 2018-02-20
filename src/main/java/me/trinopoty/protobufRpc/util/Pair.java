package me.trinopoty.protobufRpc.util;

public final class Pair<T1, T2> {

    private final T1 mFirst;
    private final T2 mSecond;

    public Pair(T1 first, T2 second) {
        mFirst = first;
        mSecond = second;
    }

    public T1 getFirst() {
        return mFirst;
    }

    public T2 getSecond() {
        return mSecond;
    }
}