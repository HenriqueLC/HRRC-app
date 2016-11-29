package com.mygdx.hrrc.dialog;

public interface RequestHandler {

    void toast(String message);

    void confirm(String title, String message, String yes, String no, ConfirmInterface confirmInterface);
}
