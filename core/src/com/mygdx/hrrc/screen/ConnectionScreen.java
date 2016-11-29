package com.mygdx.hrrc.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.mygdx.hrrc.HRRC;
import com.mygdx.hrrc.dialog.ConfirmInterface;
import com.mygdx.hrrc.network.BooleanResultInterface;
import com.mygdx.hrrc.screen.util.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

class ConnectionScreen extends AbstractScreen {

    private ConnectionScreen connectionScreen;
    private Stage stage; // disposable
    private ImageButton wifiButton, bluetoothButton; // disposable
    private InputListener wifiButtonListener, bluetoothButtonListener;
    private Text instructions; // disposable
    private BooleanResultInterface wifiResultInterface, bluetoothResultInterface;
    private boolean isWifiOn, wifiStateHasChanged, isBluetoothOn, bluetoothStateHasChanged, hideMessage;
    private float elapsedTime;

    ConnectionScreen(HRRC humanoidRobotRemoteController) {
        super(humanoidRobotRemoteController);
        connectionScreen = this;
    }

    @Override
    public void show() {
        // Initialize the stage where we will place everything
        stage = new Stage();
        // Get our texture atlas from the manager
        TextureAtlas textureAtlas = humanoidRobotRemoteController.assetManager.get("data/connection.pack", TextureAtlas.class);
        Skin skin = new Skin();
        skin.addRegions(textureAtlas);
        // Wifi button
        final ImageButton.ImageButtonStyle wifiButtonStyle = new ImageButton.ImageButtonStyle();
        wifiButtonStyle.up = skin.getDrawable("connection-wifi-button");
        wifiButtonStyle.down = skin.getDrawable("connection-wifi-button");
        wifiButtonStyle.imageDisabled = skin.getDrawable("connection-wifi-button-deactivated");
        wifiButton = new ImageButton(wifiButtonStyle);
        stage.addActor(wifiButton);
        // Wifi button event listener
        wifiButtonListener = new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                // Hide the choose message
                hideMessage = true;
                // Input text dialog
                Gdx.input.getTextInput(
                        new Input.TextInputListener() {
                            @Override
                            public void input(String text) {
                                // Show progress dialog
                                humanoidRobotRemoteController.progressDialogRequestHandler.show("Progress dialog", "Trying to connect to " + text + " ...", false, false);
                                SocketHints socketHints = new SocketHints();
                                // Socket will time our in 5 seconds
                                socketHints.connectTimeout = 5000;
                                socketHints.socketTimeout = 5000;
                                try {
                                    // Create the socket and try to connect to the server entered in the text box ( x.x.x.x format ) on port 20000
                                    Socket socket = Gdx.net.newClientSocket(Net.Protocol.TCP, text, 20000, socketHints);
                                    // Get the output and input TCP interfaces
                                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                    // Send the command "connect"
                                    String command = "connect";
                                    out.println(command);
                                    try {
                                        // Wait for the connection response
                                        String response = in.readLine();
                                        if (Boolean.valueOf(response)) {
                                            // Dismiss progress dialog
                                            humanoidRobotRemoteController.progressDialogRequestHandler.dismiss();
                                            humanoidRobotRemoteController.requestHandler.toast("Successfully connected");
                                            // Set screen
                                            humanoidRobotRemoteController.setScreen(new ControllerScreen(humanoidRobotRemoteController, socket));
                                            connectionScreen.dispose();
                                        }
                                        else {
                                            // Dismiss progress dialog
                                            humanoidRobotRemoteController.progressDialogRequestHandler.dismiss();
                                            humanoidRobotRemoteController.requestHandler.toast("Server wasn't able to connect to the simulation");
                                        }
                                    } catch (IOException e) {
                                        // Dismiss progress dialog
                                        humanoidRobotRemoteController.progressDialogRequestHandler.dismiss();
                                        humanoidRobotRemoteController.requestHandler.toast("Server couldn't respond in time");
                                    }
                                } catch (GdxRuntimeException e) {
                                    // Dismiss progress dialog
                                    humanoidRobotRemoteController.progressDialogRequestHandler.dismiss();
                                    humanoidRobotRemoteController.requestHandler.toast("Couldn't connect to the Server. Please try again");
                                }
                            }


                            @Override
                            public void canceled() {
                                // Show the choose message
                                hideMessage = false;
                            }
                        },
                        "IP address",
                        "",
                        "xxx.xxx.x.xxx"
                );
            }
        };
        // Add Wifi button event listener
        wifiButton.addListener(wifiButtonListener);
        // Bluetooth button
        ImageButton.ImageButtonStyle bluetoothButtonStyle = new ImageButton.ImageButtonStyle();
        bluetoothButtonStyle.up = skin.getDrawable("connection-bluetooth-button");
        bluetoothButtonStyle.down = skin.getDrawable("connection-bluetooth-button");
        bluetoothButtonStyle.imageDisabled = skin.getDrawable("connection-bluetooth-button-deactivated");
        bluetoothButton = new ImageButton(bluetoothButtonStyle);
        stage.addActor(bluetoothButton);
        // Bluetooth button event listener
        bluetoothButtonListener = new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                // Confirm dialog
                ConfirmInterface confirmInterface = new ConfirmInterface() {
                    @Override
                    public void yes() {
                    }

                    @Override
                    public void no() {
                    }
                };
                humanoidRobotRemoteController.requestHandler.confirm("Alert dialog", "Bluetooth communication not yet implemented", "Ok", "Cancel", confirmInterface);
            }
        };
        // Add Bluetooth button event listener
        bluetoothButton.addListener(bluetoothButtonListener);
        wifiResultInterface = new BooleanResultInterface() {
            @Override
            public void yes() {
                if (!isWifiOn) {
                    wifiStateHasChanged = true;
                }
                isWifiOn = true;
            }

            @Override
            public void no() {
                if (isWifiOn) {
                    wifiStateHasChanged = true;
                }
                isWifiOn = false;
            }
        };
        // Bluetooth result interface
        bluetoothResultInterface = new BooleanResultInterface() {
            @Override
            public void yes() {
                if (!isBluetoothOn) {
                    bluetoothStateHasChanged = true;
                }
                isBluetoothOn = true;
            }

            @Override
            public void no() {
                if (isBluetoothOn) {
                    bluetoothStateHasChanged = true;
                }
                isBluetoothOn = false;
            }
        };
        // Get our fonts from the manager and create some text
        BitmapFontCache bitmapFontCache = humanoidRobotRemoteController.assetManager.get("size42.ttf", BitmapFont.class).getCache();
        bitmapFontCache.setColor(Color.ORANGE);
        instructions = new Text(bitmapFontCache, "");
        stage.addActor(instructions);
        // Set the input processor
        Gdx.input.setInputProcessor(stage);
        // Get connection state
        humanoidRobotRemoteController.connectionTest.isWifiOn(wifiResultInterface);
        humanoidRobotRemoteController.connectionTest.isBluetoothOn(bluetoothResultInterface);
        // disable wifi
        wifiButton.setDisabled(true);
        wifiButton.setTouchable(Touchable.disabled);
        // disable bluetooth
        bluetoothButton.setDisabled(true);
        bluetoothButton.setTouchable(Touchable.disabled);
        // Set the appropriated instructions
        instructions.setMessage("Enable Wifi and/or Bluetooth");
    }

    @Override
    public void resize(int width, int height) {
        // Place the wifi button in the left-middle of the screen
        wifiButton.setX((width - wifiButton.getWidth()) / 2 - 11 * width / 50);
        wifiButton.setY((height - wifiButton.getHeight()) / 2);
        // Place the bluetooth button in the right-middle of the screen
        bluetoothButton.setX((width - bluetoothButton.getWidth()) / 2 + 11 * width / 50);
        bluetoothButton.setY((height - bluetoothButton.getHeight()) / 2);
        // Place the instructions in the bottom of the screen
        instructions.setX((Gdx.graphics.getWidth() - instructions.getWidth()) / 2);
        instructions.setY((Gdx.graphics.getHeight() - instructions.getHeight()) / 2 - 7 * Gdx.graphics.getHeight() / 20);
    }

    @Override
    public void resume() {
    }

    @Override
    public void render(float delta) {
        // Graphics
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.8f, 0.8f, 0.8f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        // Check if wifi and/or bluetooth are/is on
        humanoidRobotRemoteController.connectionTest.isWifiOn(wifiResultInterface);
        humanoidRobotRemoteController.connectionTest.isBluetoothOn(bluetoothResultInterface);
        // if wifi state has changed
        if (wifiStateHasChanged) {
            // Enable/disable wifi
            wifiButton.setDisabled(!isWifiOn);
            if (isWifiOn) {
                wifiButton.setTouchable(Touchable.enabled);
            }
            else {
                wifiButton.setTouchable(Touchable.disabled);
            }
        }
        // if bluetooth state has changed
        if (bluetoothStateHasChanged) {
            // Enable/disable bluetooth
            bluetoothButton.setDisabled(!isBluetoothOn);
            if (isBluetoothOn) {
                bluetoothButton.setTouchable(Touchable.enabled);
            }
            else {
                bluetoothButton.setTouchable(Touchable.disabled);
            }
        }
        // if any state has changed
        if (wifiStateHasChanged || bluetoothStateHasChanged) {
            // Set the appropriated instructions
            if (isWifiOn && isBluetoothOn) {
                instructions.setMessage("Choose the connection type");
            }
            else {
                if (isWifiOn) {
                    instructions.setMessage("Choose Wifi or enable Bluetooth");
                }
                else if (isBluetoothOn) {
                    instructions.setMessage("Choose Bluetooth or enable Wifi");
                }
                else {
                    instructions.setMessage("Enable Wifi and/or Bluetooth");
                }
            }
            // Place the new instructions in the bottom of the screen
            instructions.setX((Gdx.graphics.getWidth() - instructions.getWidth()) / 2);
            instructions.setY((Gdx.graphics.getHeight() - instructions.getHeight()) / 2 - 7 * Gdx.graphics.getHeight() / 20);
        }
        wifiStateHasChanged = false;
        bluetoothStateHasChanged = false;
        // Blink the instruction message
        if (!hideMessage) {
            elapsedTime += delta;
            elapsedTime %= 2;
            // Clear the screen
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            if (elapsedTime < 1) {
                instructions.setAlphas(elapsedTime);
            } else {
                instructions.setAlphas(2 - elapsedTime);
            }
        }
        else {
            instructions.setAlphas(0f);
            elapsedTime = 0;
        }
        // Show the connection screen
        stage.draw();
    }

    @Override
    public void pause() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}