package me.trinopoty.protobufRpc.client;

import java.net.InetSocketAddress;

public interface ProtobufRpcClientChannel extends AutoCloseable {

    /**
     * Get an instance of service class that can be used to communicate with Rpc server.
     *
     * @param classOfService The class of the interface defining the service.
     * @param <T> The type of the interface defining the service.
     * @return An object of the service instance that can be used to communicate with Rpc server.
     */
    <T> T getService(Class<T> classOfService);

    /**
     * Add a OOB message handler.
     *
     * @param classOfOob The class of the OOB interface.
     * @param objectOfOob The instance of the OOB instance.
     * @param <T>
     *
     * @throws IllegalArgumentException If provided OOB interface class is not registered.
     */
    <T> void addOobHandler(Class<T> classOfOob, T objectOfOob);

    /**
     * Retrieves boolean value indicating if the connection is active.
     *
     * @return boolean value indicating whether the connection is active.
     */
    boolean isActive();

    /**
     * Adds a listener for channel disconnect events.
     *
     * @param channelDisconnectListener The channel disconnect listener.
     */
    void setChannelDisconnectListener(ProtobufRpcClientChannelDisconnectListener channelDisconnectListener);

    /**
     * Close the connection with the server.
     */
    void close();

    /**
     * Gets the remote address of this connection.
     *
     * @return The remote address of this connection.
     */
    InetSocketAddress getRemoteAddress();
}