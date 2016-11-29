package com.mygdx.hrrc;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;

import com.mygdx.hrrc.dialog.ProgressDialogRequestHandler;
import com.mygdx.hrrc.dialog.RequestHandler;
import com.mygdx.hrrc.network.ConnectionTest;
import com.mygdx.hrrc.screen.LoadingScreen;

public class HRRC extends Game {

	public AssetManager assetManager = new AssetManager();
	public ConnectionTest connectionTest;
	public RequestHandler requestHandler;
	public ProgressDialogRequestHandler progressDialogRequestHandler;

	HRRC(ConnectionTest connectionTest, RequestHandler requestHandler, ProgressDialogRequestHandler progressDialogRequestHandler) {
		this.assetManager = new AssetManager();
		this.connectionTest = connectionTest;
		this.requestHandler = requestHandler;
		this.progressDialogRequestHandler = progressDialogRequestHandler;
	}

	@Override
	public void create() {
		Texture.setAssetManager(assetManager);
		setScreen(new LoadingScreen(this));
	}

	@Override
	public void dispose() {
		assetManager.dispose();
		assetManager = null;
	}
}
