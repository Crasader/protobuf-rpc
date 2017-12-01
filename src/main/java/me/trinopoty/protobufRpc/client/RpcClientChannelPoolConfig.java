package me.trinopoty.protobufRpc.client;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class RpcClientChannelPoolConfig extends GenericObjectPoolConfig {

    private boolean mLoggingEnabled = false;
    private String mLogTag = null;
    private boolean mLogCallingMethod = false;

    public boolean isLoggingEnabled() {
        return mLoggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        mLoggingEnabled = loggingEnabled;
    }

    public String getLogTag() {
        return mLogTag;
    }

    public void setLogTag(String logTag) {
        mLogTag = logTag;
    }

    public boolean isLogCallingMethod() {
        return mLogCallingMethod;
    }

    public void setLogCallingMethod(boolean logCallingMethod) {
        mLogCallingMethod = logCallingMethod;
    }
}