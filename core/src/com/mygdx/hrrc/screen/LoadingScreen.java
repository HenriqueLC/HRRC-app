package com.mygdx.hrrc.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.mygdx.hrrc.HRRC;

public class LoadingScreen extends AbstractScreen {

    private Stage stage;
    private Image loadingFrame;
    private Image loadingBarHidden;
    private Image screenBg;
    private Image loadingBg;
    private float startX, endX;
    private float percent;
    private Actor loadingBar;

    public LoadingScreen(HRRC humanoidRobotRemoteController) {
        super(humanoidRobotRemoteController);
    }

    @Override
    public void show() {
        // Tell the manager to load assets for the loading screen
        humanoidRobotRemoteController.assetManager.load("data/loading.pack", TextureAtlas.class);
        // Wait until they are finished loading
        humanoidRobotRemoteController.assetManager.finishLoading();
        // Initialize the stage where we will place everything
        stage = new Stage();
        // Get our texture atlas from the manager
        TextureAtlas atlas = humanoidRobotRemoteController.assetManager.get("data/loading.pack", TextureAtlas.class);
        // Grab the regions from the atlas and create some images
        loadingFrame = new Image(atlas.findRegion("loading-frame"));
        loadingBarHidden = new Image(atlas.findRegion("loading-bar-hidden"));
        screenBg = new Image(atlas.findRegion("screen-bg"));
        loadingBg = new Image(atlas.findRegion("loading-frame-bg"));
        // Or if you only need a static bar, you can do
        loadingBar = new Image(atlas.findRegion("loading-bar1"));
        // Add all the actors to the stage
        stage.addActor(screenBg);
        stage.addActor(loadingBar);
        stage.addActor(loadingBg);
        stage.addActor(loadingBarHidden);
        stage.addActor(loadingFrame);
        // Add everything to be loaded, for instance:
        humanoidRobotRemoteController.assetManager.load("data/connection.pack", TextureAtlas.class);
        humanoidRobotRemoteController.assetManager.load("data/controller.pack", TextureAtlas.class);
        // set the loaders for the generator and the fonts themselves
        FileHandleResolver resolver = new InternalFileHandleResolver();
        humanoidRobotRemoteController.assetManager.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        humanoidRobotRemoteController.assetManager.setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));
        // load the font via the generator
        FreetypeFontLoader.FreeTypeFontLoaderParameter sizeParams = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        sizeParams.fontFileName = "data/arial.ttf";
        sizeParams.fontParameters.size = 42;
        humanoidRobotRemoteController.assetManager.load("size42.ttf", BitmapFont.class, sizeParams);
        // load the 3D Models
        humanoidRobotRemoteController.assetManager.load("data/nao.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/sphere.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/yaw.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/pitch.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/yawPitch.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/yawRoll.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/pitchRoll.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/yawPitchPitchRollLeft.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/yawPitchPitchRollRight.g3db", Model.class);
        humanoidRobotRemoteController.assetManager.load("data/rollPitch.g3db", Model.class);
    }

    @Override
    public void resize(int width, int height) {
        // Make the background fill the screen
        screenBg.setSize(width, height);
        // Place the loading frame in the middle of the screen
        loadingFrame.setX((stage.getWidth() - loadingFrame.getWidth()) / 2);
        loadingFrame.setY((stage.getHeight() - loadingFrame.getHeight()) / 2);
        // Place the loading bar at the same spot as the frame, adjusted a few px
        loadingBar.setX(loadingFrame.getX() + 15);
        loadingBar.setY(loadingFrame.getY() + 5);
        // Place the image that will hide the bar on top of the bar, adjusted a few px
        loadingBarHidden.setX(loadingBar.getX() + 35);
        loadingBarHidden.setY(loadingBar.getY() - 3);
        // The start position and how far to move the hidden loading bar
        startX = loadingBarHidden.getX();
        endX = 440;
        // The rest of the hidden bar
        loadingBg.setSize(450, 50);
        loadingBg.setX(loadingBarHidden.getX() + 30);
        loadingBg.setY(loadingBarHidden.getY() + 3);
    }

    @Override
    public void render(float delta) {
        // Clear the screen
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (humanoidRobotRemoteController.assetManager.update()) { // Load some, will return true if done loading
            humanoidRobotRemoteController.setScreen(new ConnectionScreen(humanoidRobotRemoteController));
            this.dispose();
        }
        // Interpolate the percentage to make it more smooth
        percent = Interpolation.linear.apply(percent, humanoidRobotRemoteController.assetManager.getProgress(), 0.1f);
        // Update positions (and size) to match the percentage
        loadingBarHidden.setX(startX + endX * percent);
        loadingBg.setX(loadingBarHidden.getX() + 30);
        loadingBg.setWidth(450 - 450 * percent);
        loadingBg.invalidate();
        // Show the loading screen
        stage.draw();
    }

    @Override
    public void hide() {
        // Dispose the loading assets as we no longer need them
        humanoidRobotRemoteController.assetManager.unload("data/loading.pack");
    }
}