package com.mygdx.hrrc;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.widget.Toast;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.mygdx.hrrc.dialog.ConfirmInterface;
import com.mygdx.hrrc.dialog.ProgressDialogRequestHandler;
import com.mygdx.hrrc.dialog.RequestHandler;
import com.mygdx.hrrc.network.BooleanResultInterface;
import com.mygdx.hrrc.network.ConnectionTest;

public class AndroidLauncher extends AndroidApplication implements RequestHandler, ProgressDialogRequestHandler, ConnectionTest {
	private ProgressDialog progressDialog;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Remote Controller configuration
		AndroidApplicationConfiguration androidApplicationConfiguration = new AndroidApplicationConfiguration();
		androidApplicationConfiguration.useAccelerometer = true;
		// Initializes by the loading screen
		initialize(new HRRC(this, this, this), androidApplicationConfiguration);
	}

	@Override
	public void isWifiOn(final BooleanResultInterface booleanResultInterface) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if (networkInfo != null) {
                    booleanResultInterface.yes();
                } else {
                    booleanResultInterface.no();
                }
            }
        });
	}

	@Override
	public void isBluetoothOn(final BooleanResultInterface booleanResultInterface) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null) {
                    if (bluetoothAdapter.isEnabled()) {
                        booleanResultInterface.yes();
                        return;
                    }
                }
                booleanResultInterface.no();
            }
	    });
    }

	// Dialogs
	@Override
	public void toast(final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(AndroidLauncher.this, message, Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void show(final String title, final String message, final boolean indeterminate, final boolean cancelable) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				progressDialog = new ProgressDialog(AndroidLauncher.this);
				progressDialog.setTitle(title);
				progressDialog.setMessage(message);
				progressDialog.setIndeterminate(indeterminate);
				progressDialog.setCancelable(cancelable);
				progressDialog.show();
			}
		});
	}

	@Override
	public void dismiss() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (progressDialog != null) {
					progressDialog.dismiss();
				}
			}
		});
	}

	@Override
	public void confirm(final String title, final String message, final String yes, final String no, final ConfirmInterface confirmInterface) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				new AlertDialog.Builder(AndroidLauncher.this)
						.setTitle(title)
						.setMessage(message)
						.setPositiveButton(yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								confirmInterface.yes();
								dialog.cancel();
							}
						})
						.setNegativeButton(no, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								confirmInterface.no();
								dialog.cancel();
							}
						})
						.create()
						.show();
			}
		});
	}
}