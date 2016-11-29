package com.mygdx.hrrc.dialog;

public interface ProgressDialogRequestHandler {

    void show(String title, String message, boolean indeterminate, boolean cancelable);

    void dismiss();
}
