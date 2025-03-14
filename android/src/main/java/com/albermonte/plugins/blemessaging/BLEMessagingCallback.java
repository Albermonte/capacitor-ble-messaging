package com.albermonte.plugins.blemessaging;

import com.getcapacitor.JSObject;

public interface BLEMessagingCallback {
    void notifyEvent(String eventName, JSObject data);
}