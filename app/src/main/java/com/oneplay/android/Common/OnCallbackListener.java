package com.oneplay.android.Common;


import java.io.UnsupportedEncodingException;

public interface OnCallbackListener {

    void onSuccess(String data) throws UnsupportedEncodingException;

    void onError(String msg);
}
