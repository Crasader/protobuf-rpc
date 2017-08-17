package me.trinopoty.protobufRpc.client;

public interface RpcClientChannel extends AutoCloseable {

    /**
     * Close the connection with the server.
     */
    void close();

    /**
     * Retrieves boolean value indicating if the connection is active.
     *
     * @return boolean value indicating whether the connection is active.
     */
    boolean isActive();

    /**
     * Get an instance of service class that can be used to communicate with Rpc server.
     *
     * @param classOfService The class of the interface defining the service.
     * @param <T> The type of the interface defining the service.
     * @return An object of the service instance that can be used to communicate with Rpc server.
     */
    <T> T getService(Class<T> classOfService);
}