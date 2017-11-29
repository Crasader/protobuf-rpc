package me.trinopoty.protobufRpc.client;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class RpcClientChannelPoolConfig extends GenericObjectPoolConfig {

    private boolean mLoggingEnabled = false;

    public boolean isLoggingEnabled() {
        return mLoggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        mLoggingEnabled = loggingEnabled;
    }
}