
package ch.loway.oss.ari4java.tools;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Delegate for handling asynchronous ARI responses.
 * 
 * 
 */
public class AriAsyncHandler<T> implements HttpResponseHandler {
    private final AriCallback<? super T> callback;
    private Class<T> klazz;
    private TypeReference<T> klazzType;
    private long lastResponseTime = 0;

    public AriAsyncHandler(AriCallback<? super T> callback, Class<T> klazz) {
        this.callback = callback;
        this.klazz = klazz;
    }

    public AriAsyncHandler(AriCallback<? super T> callback, TypeReference<T> klazzType) {
        this.callback = callback;
        this.klazzType = klazzType;
    }

    public AriCallback<? super T> getCallback() {
        return callback;
    }

    void handleResponse(String json) {
        try {
            T result;
            if (Void.class.equals(klazz)) {
                result = null;
            } else if (klazz != null) {
                result = BaseAriAction.deserializeJson(json, klazz);
            } else {
                result = BaseAriAction.deserializeJson(json, klazzType);
            }
            this.callback.onSuccess(result);
        } catch (RestException e) {
            this.callback.onFailure(e);
        }
    }

    @Override
    public void onChReadyToWrite() {
        lastResponseTime = System.currentTimeMillis();
        connectionEvent(AriConnectionEvent.WS_CONNECTED);
    }

    private void connectionEvent(AriConnectionEvent event) {
        if (this.callback instanceof AriWSCallback<?>) {
            ((AriWSCallback<?>) this.callback).onConnectionEvent(event);
        }
    }

    @Override
    public void onResponseReceived() {
        lastResponseTime = System.currentTimeMillis();
    }

    @Override
    public void onDisconnect() {
        connectionEvent(AriConnectionEvent.WS_DISCONNECTED);
    }

    @Override
    public void onSuccess(String response) {
        handleResponse(response);
    }

    @Override
    public void onSuccess(byte[] response) {
        this.callback.onSuccess((T) response);
    }

    @Override
    public void onFailure(Throwable e) {
        if (e instanceof RestException) {
            this.callback.onFailure((RestException) e);
        } else {
            this.callback.onFailure(new RestException(e));
        }
    }

    @Override
    public long getLastResponseTime() {
        return lastResponseTime;
    }

    @Override
    public Class getType() {
        return klazz;
    }
}

// $Log$
//
