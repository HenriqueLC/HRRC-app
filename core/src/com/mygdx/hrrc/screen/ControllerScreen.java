package com.mygdx.hrrc.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.mygdx.hrrc.HRRC;
import com.mygdx.hrrc.screen.util.HatSwitch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA;

class ControllerScreen extends AbstractScreen implements InputProcessor {

    private Stage stage;
    private ImageButton locker;
    private boolean unlocked;
    private Image display;
    private Pixmap pixmap;
    private ImageButton button1, button2, button3;
    private Touchpad arrowJoystick, arrowJoystickJoints;
    private Image deactivatedArrowJoystickJoints;
    private HatSwitch hatSwitch;
    private Image deactivatedHatSwitch;
    private boolean hasArrowJoystick, hasHatSwitch;
    private ModelBatch modelBatch; // disposable
    private ModelInstance robot; // disposable
    private class YawPitchRoll {
        private final float yaw, pitch, roll;

        YawPitchRoll(float yaw, float pitch, float roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        float getYaw() {
            return yaw;
        }

        float getPitch() {
            return pitch;
        }

        float getRoll() {
            return roll;
        }
    }
    private HashMap<String, YawPitchRoll> defaultRotationValues;
    private class Joint {

        private final String name;
        private final ModelInstance joint, sphere;

        Joint(String name, ModelInstance joint, ModelInstance sphere) {
            this.name = name;
            this.joint = joint;
            this.sphere = sphere;
        }

        String getName() { return name; }
        ModelInstance getJoint() { return joint; }
        ModelInstance getSphere() { return sphere; }

        @Override
        public int hashCode() { return name.hashCode() ^ joint.hashCode() ^ sphere.hashCode(); }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Joint)) return false;
            Joint tupleObject = (Joint) o;
            return this.name.equals(tupleObject.getName()) && this.joint.equals(tupleObject.getJoint()) && this.sphere.equals(tupleObject.getSphere());
        }
    }
    private Joint selectedJoint;
    private Joint[] joints;
    private Environment environment;
    private PerspectiveCamera camera3D;
    private Socket socket; // disposable
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private ExecutorService exService;
    private boolean connectionEnded;
    private boolean calculateTransforms;
    private Lock lock;
    private float elapsedTime;
    private int count;

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    private Joint getJoint(int screenX, int screenY) {
        Joint newSelectedJoint = null;
        Ray ray = camera3D.getPickRay(screenX, screenY);
        Vector3 position = new Vector3();
        float distance = -1;
        for (Joint joint : joints) {
            robot.getNode(joint.getName()).globalTransform.getTranslation(position);
            float dist2 = ray.origin.dst2(position);
            if (distance >= 0f && dist2 > distance) {
                continue;
            }
            if (Intersector.intersectRaySphere(ray, position, 5f, null)) {
                newSelectedJoint = joint;
                distance = dist2;
            }
        }
        return newSelectedJoint;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (unlocked) {
            Joint newSelectedJoint = getJoint(screenX, screenY);
            if (newSelectedJoint != null) {
                if (selectedJoint != null) {
                    selectedJoint.getSphere().materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
                }
                if (newSelectedJoint != selectedJoint) {
                    newSelectedJoint.getSphere().materials.first().set(new BlendingAttribute(false, 0.5f));
                    selectedJoint = newSelectedJoint;
                } else {
                    selectedJoint = null;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        return false;
    }

    ControllerScreen(HRRC humanoidRobotRemoteController, Socket socket) {
        super(humanoidRobotRemoteController);
        this.socket = socket;
    }

    private void setEulerAngles(String nodeId, float yaw, float pitch, float roll) {
        if (defaultRotationValues.get(nodeId) == null) {
            defaultRotationValues.put(nodeId, new YawPitchRoll(
                            robot.getNode(nodeId).rotation.getYaw(),
                            robot.getNode(nodeId).rotation.getPitch(),
                            robot.getNode(nodeId).rotation.getRoll()
                    )
            );
        }
        robot.getNode(nodeId).rotation.setEulerAngles(defaultRotationValues.get(nodeId).getYaw() + yaw, defaultRotationValues.get(nodeId).getPitch() + pitch, defaultRotationValues.get(nodeId).getRoll() + roll);
    }

    private class GetJointAnglesImagePixelsThread implements Runnable {
        @Override
        public void run() {
            if (lock.tryLock()) {
                // Got the lock
                try {
                    try {
                        // Send the command "get joints and vision sensor"
                        dataOutputStream.writeInt(1);
                        // Wait for the connection response
                        byte[] bytes = new byte[112 + 38400];
                        dataInputStream.readFully(bytes);
                        // Convert the bytes stream into a float array
                        float[] nodes = new float[26];
                        for (int i = 0; i < 26; i++) {
                            nodes[i] = ByteBuffer.wrap(bytes, 4 * i, 4).getFloat();
                        }
                        float zLeft = ByteBuffer.wrap(bytes, 104, 4).getFloat();
                        float zRight = ByteBuffer.wrap(bytes, 108, 4).getFloat();
                        float z = (zLeft + zRight) / 2 - 14.2f;
                        robot.transform.setTranslation(0, z, 0);
                        // Head update
                        setEulerAngles("Head", nodes[0], nodes[1], 0f);
                        // Left leg update
                        setEulerAngles("Hip.L", -nodes[2], -nodes[4], -nodes[3]);
                        setEulerAngles("Knee.L", 0f, nodes[5], 0f);
                        setEulerAngles("Ankle.L", 0f, nodes[6], -nodes[7]);
                        // Right leg update
                        setEulerAngles("Hip.R", nodes[8], -nodes[10], -nodes[9]);
                        setEulerAngles("Knee.R", 0f, nodes[11], 0f);
                        setEulerAngles("Ankle.R", 0f, nodes[12], -nodes[13]);
                        // Left arm update
                        setEulerAngles("Shoulder.L", nodes[14], -nodes[15], 0f);
                        setEulerAngles("Elbow.L", nodes[16], -nodes[17], 0f);
                        setEulerAngles("Wrist.L", nodes[18], 0f, 0f);
                        // Right arm update
                        setEulerAngles("Shoulder.R", -nodes[19], nodes[20], 0f);
                        setEulerAngles("Elbow.R", nodes[21], nodes[22], 0f);
                        setEulerAngles("Wrist.R", nodes[23], 0f, 0f);
                        setEulerAngles("spine", -nodes[24], -nodes[25], 0f);
                        calculateTransforms = true;
                        // Pixels
                        pixmap = new Pixmap(160, 120, Pixmap.Format.Intensity);
                        for (int i = 0; i < 120; i++) {
                            for (int j = 0; j < 160; j++) {
                                int color = ByteBuffer.wrap(bytes, 112 + 2 * (160 * i + j), 2).getChar();
                                pixmap.drawPixel(j, 120 - i, color);
                            }
                        }
                    } catch (IOException e) {
                        connectionEnded = true;
                    }
                } finally {
                    // Make sure to unlock so that we don't cause a deadlock
                    lock.unlock();
                }
            }
        }
    }

    private class WalkTurnThread implements Runnable {
        private float x, y, theta;

        WalkTurnThread(float x, float y, float theta) {
            this.x = x;
            this.y = y;
            this.theta = theta;
        }

        @Override
        public void run() {
            try {
                dataOutputStream.writeInt(2);
                dataOutputStream.writeFloat(x);
                dataOutputStream.writeFloat(y);
                dataOutputStream.writeFloat(theta);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class GoToPosture implements Runnable {
        private int posture;

        GoToPosture(int posture) {
            this.posture = posture;
        }

        @Override
        public void run() {
            try {
                dataOutputStream.writeInt(3);
                dataOutputStream.writeInt(posture);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class AddJointsValuesThread implements Runnable {
        private int command;
        private float yaw, pitch, roll;

        AddJointsValuesThread(String jointID, float yaw, float pitch, float roll) {
            int command = -1;
            if (jointID.equals("Head")) {
                command = 0;
            } else if (jointID.equals("Hip.L")) {
                command = 1;
            } else if (jointID.equals("Knee.L")) {
                command = 2;
            } else if (jointID.equals("Ankle.L")) {
                command = 3;
            } else if (jointID.equals("Hip.R")) {
                command = 4;
            } else if (jointID.equals("Knee.R")) {
                command = 5;
            } else if (jointID.equals("Ankle.R")) {
                command = 6;
            } else if (jointID.equals("Shoulder.L")) {
                command = 7;
            } else if (jointID.equals("Elbow.L")) {
                command = 8;
            } else if (jointID.equals("Wrist.L")) {
                command = 9;
            } else if (jointID.equals("Shoulder.R")) {
                command = 10;
            } else if (jointID.equals("Elbow.R")) {
                command = 11;
            } else if (jointID.equals("Wrist.R")) {
                command = 12;
            }
            this.command = command;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        @Override
        public void run() {
            try {
                // command
                dataOutputStream.writeInt(4);
                // joint id
                dataOutputStream.writeInt(command);
                // yaw, pitch, roll
                dataOutputStream.writeFloat(yaw);
                dataOutputStream.writeFloat(pitch);
                dataOutputStream.writeFloat(roll);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void show() {
        // 2D stuff
        stage = new Stage();
        TextureAtlas textureAtlas = humanoidRobotRemoteController.assetManager.get("data/controller.pack", TextureAtlas.class);
        // Locker
        final Skin skin = new Skin();
        skin.addRegions(textureAtlas);
        // Locker
        final ImageButton.ImageButtonStyle lockerButtonStyle = new ImageButton.ImageButtonStyle();
        if (unlocked) {
            lockerButtonStyle.up = skin.getDrawable("opened");
            lockerButtonStyle.down = skin.getDrawable("opened");
        }
        else {
            lockerButtonStyle.up = skin.getDrawable("closed");
            lockerButtonStyle.down = skin.getDrawable("closed");
        }
        locker = new ImageButton(lockerButtonStyle);
        stage.addActor(locker);
        InputListener lockerListener = new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                unlocked = !unlocked;
                if (unlocked) {
                    // joints mode
                    lockerButtonStyle.up = skin.getDrawable("opened");
                    lockerButtonStyle.down = skin.getDrawable("opened");
                    stage.clear();
                    stage.addActor(locker);
                    stage.addActor(display);
                    stage.addActor(deactivatedArrowJoystickJoints);
                    hasArrowJoystick = false;
                    stage.addActor(deactivatedHatSwitch);
                    hasArrowJoystick = false;
                    for (Material robotMaterial : robot.materials) {
                        robotMaterial.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 0.3f));
                    }
                }
                else {
                    lockerButtonStyle.up = skin.getDrawable("closed");
                    lockerButtonStyle.down = skin.getDrawable("closed");
                    stage.clear();
                    stage.addActor(locker);
                    stage.addActor(display);
                    stage.addActor(button1);
                    stage.addActor(button2);
                    stage.addActor(button3);
                    stage.addActor(arrowJoystick);
                    for (Material robotMaterial : robot.materials) {
                        robotMaterial.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1.0f));
                    }
                    if (selectedJoint != null) {
                        selectedJoint.getSphere().materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
                        selectedJoint = null;
                    }
                }
            }
        };
        locker.addListener(lockerListener);
        // display
        pixmap = new Pixmap(160, 120, Pixmap.Format.Intensity);
        for (int i = 0; i < 160; i++) {
            for (int j = 0; j < 120; j++) {
                pixmap.drawPixel(j, i, 128);
            }
        }
        display = new Image(new Texture(pixmap));
        stage.addActor(display);
        if (pixmap != null) {
            pixmap.dispose();
            pixmap = null;
        }
        // button1
        final ImageButton.ImageButtonStyle button1Style = new ImageButton.ImageButtonStyle();
        button1Style.up = skin.getDrawable("button1");
        button1Style.down = skin.getDrawable("button1");
        button1Style.disabled = skin.getDrawable("button1-deactivated");
        button1 = new ImageButton(button1Style);
        InputListener button1Listener = new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                exService.submit(new GoToPosture(1));
            }
        };
        button1.addListener(button1Listener);
        stage.addActor(button1);
        // button2
        final ImageButton.ImageButtonStyle button2Style = new ImageButton.ImageButtonStyle();
        button2Style.up = skin.getDrawable("button2");
        button2Style.down = skin.getDrawable("button2");
        button2Style.disabled = skin.getDrawable("button2-deactivated");
        button2 = new ImageButton(button2Style);
        InputListener button2Listener = new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                exService.submit(new GoToPosture(4));
            }
        };
        button2.addListener(button2Listener);
        stage.addActor(button2);
        // button3
        final ImageButton.ImageButtonStyle button3Style = new ImageButton.ImageButtonStyle();
        button3Style.up = skin.getDrawable("button3");
        button3Style.down = skin.getDrawable("button3");
        button3Style.disabled = skin.getDrawable("button3-deactivated");
        button3 = new ImageButton(button1Style);
        InputListener button3Listener = new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                exService.submit(new GoToPosture(7));
            }
        };
        button3.addListener(button3Listener);
        stage.addActor(button3);
        // Arrow Joystick
        Skin arrowJoystickSkin = new Skin();
        Sprite arrowJoystickBackground = new Sprite(textureAtlas.findRegion("joystick-background"));
        Sprite arrowJoystickKnob = new Sprite(textureAtlas.findRegion("joystick-knob"));
        arrowJoystickSkin.add("joystick-background", arrowJoystickBackground);
        arrowJoystickSkin.add("joystick-knob", arrowJoystickKnob);
        Touchpad.TouchpadStyle arrowJoystickStyle = new Touchpad.TouchpadStyle();
        arrowJoystickStyle.background = arrowJoystickSkin.getDrawable("joystick-background");
        arrowJoystickStyle.knob = arrowJoystickSkin.getDrawable("joystick-knob");
        arrowJoystick = new Touchpad(10, arrowJoystickStyle);
        arrowJoystick.setWidth(arrowJoystickBackground.getWidth());
        arrowJoystick.setHeight(arrowJoystickBackground.getHeight());
        stage.addActor(arrowJoystick);
        // Arrow Joystick Joints
        arrowJoystickJoints = new Touchpad(10, arrowJoystickStyle);
        arrowJoystickJoints.setWidth(arrowJoystickBackground.getWidth());
        arrowJoystickJoints.setHeight(arrowJoystickBackground.getHeight());
        deactivatedArrowJoystickJoints = new Image(textureAtlas.findRegion("joystick-deactivated"));
        //stage.addActor(deactivatedArrowJoystick);
        // Hat Switch
        Skin hatSwitchSkin = new Skin();
        hatSwitchSkin.addRegions(textureAtlas);
        Drawable hatSwitchBackground = hatSwitchSkin.getDrawable("hat-switch-background");
        Drawable hatSwitchKnob = hatSwitchSkin.getDrawable("hat-switch-knob");
        hatSwitch = new HatSwitch(hatSwitchKnob, 64f, hatSwitchBackground, 192f);
        hatSwitch.setWidth(hatSwitchBackground.getMinWidth());
        hatSwitch.setHeight(hatSwitchBackground.getMinHeight());
        deactivatedHatSwitch = new Image(textureAtlas.findRegion("hat-switch-deactivated"));
        //stage.addActor(deactivatedHatSwitch);
        // 3D Stuff
        // A ModelBatch is like a SpriteBatch, just for models. Use it to batch up geometry for OpenGL
        modelBatch = new ModelBatch();
        // Model instance
        // Load the model by name
        Model robotModel = humanoidRobotRemoteController.assetManager.get("data/nao.g3db", Model.class);
        // Set its blending attribute
        robot = new ModelInstance(robotModel);
        for (Material robotMaterial : robot.materials) {
            robotMaterial.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1.0f));
        }
        // Head
        Model yawPitch = humanoidRobotRemoteController.assetManager.get("data/yawPitch.g3db", Model.class);
        ModelInstance head = new ModelInstance(yawPitch);
        head.getNode("root").attachTo(robot.getNode("Head"));
        // Left Hip
        Model yawPitchPitchRollLeft = humanoidRobotRemoteController.assetManager.get("data/yawPitchPitchRollLeft.g3db", Model.class);
        ModelInstance hipLeft = new ModelInstance(yawPitchPitchRollLeft);
        hipLeft.getNode("root").attachTo(robot.getNode("Hip.L"));
        // Right Hip
        Model yawPitchPitchRollRight = humanoidRobotRemoteController.assetManager.get("data/yawPitchPitchRollRight.g3db", Model.class);
        ModelInstance hipRight = new ModelInstance(yawPitchPitchRollRight);
        hipRight.getNode("root").attachTo(robot.getNode("Hip.R"));
        // Left Knee
        Model pitch = humanoidRobotRemoteController.assetManager.get("data/pitch.g3db", Model.class);
        ModelInstance kneeLeft = new ModelInstance(pitch);
        kneeLeft.getNode("root").attachTo(robot.getNode("Knee.L"));
        // Right Knee
        ModelInstance kneeRight = new ModelInstance(pitch);
        kneeRight.getNode("root").attachTo(robot.getNode("Knee.R"));
        // Left Ankle
        Model rollPitch = humanoidRobotRemoteController.assetManager.get("data/rollPitch.g3db", Model.class);
        ModelInstance ankleLeft = new ModelInstance(rollPitch);
        ankleLeft.getNode("root").attachTo(robot.getNode("Ankle.L"));
        // Right Ankle
        ModelInstance ankleRight = new ModelInstance(rollPitch);
        ankleRight.getNode("root").attachTo(robot.getNode("Ankle.R"));
        // Left Shoulder
        Model pitchRoll = humanoidRobotRemoteController.assetManager.get("data/pitchRoll.g3db", Model.class);
        ModelInstance shoulderLeft = new ModelInstance(pitchRoll);
        shoulderLeft.getNode("root").attachTo(robot.getNode("Shoulder.L"));
        // Right Shoulder
        ModelInstance shoulderRight = new ModelInstance(pitchRoll);
        shoulderRight.getNode("root").attachTo(robot.getNode("Shoulder.R"));
        // Left Elbow
        Model yawRoll = humanoidRobotRemoteController.assetManager.get("data/yawRoll.g3db", Model.class);
        ModelInstance elbowLeft = new ModelInstance(yawRoll);
        elbowLeft.getNode("root").attachTo(robot.getNode("Elbow.L"));
        // Right Elbow
        ModelInstance elbowRight = new ModelInstance(yawRoll);
        elbowRight.getNode("root").attachTo(robot.getNode("Elbow.R"));
        // Left Wrist
        Model yaw = humanoidRobotRemoteController.assetManager.get("data/yaw.g3db", Model.class);
        ModelInstance wristLeft = new ModelInstance(yaw);
        wristLeft.getNode("root").attachTo(robot.getNode("Wrist.L"));
        // Right Wrist
        ModelInstance wristRight = new ModelInstance(yaw);
        wristRight.getNode("root").attachTo(robot.getNode("Wrist.R"));
        // Selection sphere
        Model sphere = humanoidRobotRemoteController.assetManager.get("data/sphere.g3db", Model.class);
        // Head sphere
        ModelInstance headSphere = new ModelInstance(sphere);
        headSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        headSphere.getNode("root").attachTo(robot.getNode("Head"));
        // Left hip sphere
        ModelInstance hipLeftSphere = new ModelInstance(sphere);
        hipLeftSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        hipLeftSphere.getNode("root").attachTo(robot.getNode("Hip.L"));
        // Right hip sphere
        ModelInstance hipRightSphere = new ModelInstance(sphere);
        hipRightSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        hipRightSphere.getNode("root").attachTo(robot.getNode("Hip.R"));
        // Left knee sphere
        ModelInstance kneeLeftSphere = new ModelInstance(sphere);
        kneeLeftSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        kneeLeftSphere.getNode("root").attachTo(robot.getNode("Knee.L"));
        // Right knee sphere
        ModelInstance kneeRightSphere = new ModelInstance(sphere);
        kneeRightSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        kneeRightSphere.getNode("root").attachTo(robot.getNode("Knee.R"));
        // Left ankle sphere
        ModelInstance ankleLeftSphere = new ModelInstance(sphere);
        ankleLeftSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        ankleLeftSphere.getNode("root").attachTo(robot.getNode("Ankle.L"));
        // Right ankle sphere
        ModelInstance ankleRightSphere = new ModelInstance(sphere);
        ankleRightSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        ankleRightSphere.getNode("root").attachTo(robot.getNode("Ankle.R"));
        // Left shoulder sphere
        ModelInstance shoulderLeftSphere = new ModelInstance(sphere);
        shoulderLeftSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        shoulderLeftSphere.getNode("root").attachTo(robot.getNode("Shoulder.L"));
        // Right shoulder sphere
        ModelInstance shoulderRightSphere = new ModelInstance(sphere);
        shoulderRightSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        shoulderRightSphere.getNode("root").attachTo(robot.getNode("Shoulder.R"));
        // Left elbow sphere
        ModelInstance elbowLeftSphere = new ModelInstance(sphere);
        elbowLeftSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        elbowLeftSphere.getNode("root").attachTo(robot.getNode("Elbow.L"));
        // Right elbow sphere
        ModelInstance elbowRightSphere = new ModelInstance(sphere);
        elbowRightSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        elbowRightSphere.getNode("root").attachTo(robot.getNode("Elbow.R"));
        // Left wrist sphere
        ModelInstance wristLeftSphere = new ModelInstance(sphere);
        wristLeftSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        wristLeftSphere.getNode("root").attachTo(robot.getNode("Wrist.L"));
        // Right wrist sphere
        ModelInstance wristRightSphere = new ModelInstance(sphere);
        wristRightSphere.materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
        wristRightSphere.getNode("root").attachTo(robot.getNode("Wrist.R"));
        // Default rotation values object stores the default's pose rotation values of the robot's joints
        defaultRotationValues = new HashMap<String, YawPitchRoll>();
        // Joints
        joints = new Joint[]{
                new Joint("Head", head, headSphere),
                new Joint("Hip.L", hipLeft, hipLeftSphere),
                new Joint("Hip.R", hipRight, hipRightSphere),
                new Joint("Knee.L", kneeLeft, kneeLeftSphere),
                new Joint("Knee.R", kneeRight, kneeRightSphere),
                new Joint("Ankle.L", ankleLeft, ankleLeftSphere),
                new Joint("Ankle.R", ankleRight, ankleRightSphere),
                new Joint("Shoulder.L", shoulderLeft, shoulderLeftSphere),
                new Joint("Shoulder.R", shoulderRight, shoulderRightSphere),
                new Joint("Elbow.L", elbowLeft, elbowLeftSphere),
                new Joint("Elbow.R", elbowRight, elbowRightSphere),
                new Joint("Wrist.L", wristLeft, wristLeftSphere),
                new Joint("Wrist.R", wristRight, wristRightSphere),
        };
        // We want some light, or we won't see our color.
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1.0f));
        // 3D Camera stuff
        camera3D = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        // Near and Far (plane) represent the minimum and maximum ranges of the camera in, um, units
        camera3D.near = 1f;
        camera3D.far = 300.0f;
        camera3D.position.set(30f, 40f, 50f);
        camera3D.lookAt(0f, 0f, 0f);
        camera3D.update();
        // 2D Camera stuff
        OrthographicCamera camera2D = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        stage.getViewport().setCamera(camera2D);
        // Connection stuff
        dataOutputStream = new DataOutputStream(socket.getOutputStream());
        dataInputStream = new DataInputStream(socket.getInputStream());
        // Set Input processor
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, this));
        // Communication thread
        // Creates thread pool with one thread
        exService = Executors.newSingleThreadExecutor();
        lock = new ReentrantLock();
    }

    @Override
    public void resize(int width, int height) {
        locker.setPosition(width / 2 - locker.getWidth() / 2 - hatSwitch.getWidth() / 2 - height / 8, 3 * height / 8 - locker.getHeight());
        display.setPosition(-width / 2 - display.getWidth() / 2 + arrowJoystickJoints.getWidth() / 2 + height / 8, 3 * height / 8 - display.getHeight());
        button1.setPosition(width / 2 - hatSwitch.getWidth() - height / 8, - height / 2 + height / 8);
        button2.setPosition(width / 2 - button2.getWidth() - height / 8, - height / 2 + height / 8);
        button3.setPosition(width / 2 - button3.getWidth() - height / 8, - height / 2 - button3.getHeight() + hatSwitch.getHeight() + height / 8);
        arrowJoystick.setPosition(-width / 2 + height / 8, -height / 2 + height / 8);
        arrowJoystickJoints.setPosition(-width / 2 + height / 8, -height / 2 + height / 8);
        deactivatedArrowJoystickJoints.setPosition(-width / 2 + height / 8, - height / 2 + height / 8);
        hatSwitch.setPosition(width / 2 - hatSwitch.getWidth() - height / 8, - height / 2 + height / 8);
        deactivatedHatSwitch.setPosition(width / 2 - hatSwitch.getWidth() - height / 8, -3 * height / 8);
    }

    @Override
    public void resume() {
        // Preferences
        Preferences preferences = Gdx.app.getPreferences("My Preferences");
        String ip = preferences.getString("ip");
        // Show progress dialog
        humanoidRobotRemoteController.progressDialogRequestHandler.show("Progress dialog", "Trying to reconnect to " + ip + " ...", false, false);
        SocketHints socketHints = new SocketHints();
        // Socket will time our in 5 seconds
        socketHints.connectTimeout = 5000;
        socketHints.socketTimeout = 5000;
        if (!socket.isConnected()) {
            try {
                // Create the socket and try to connect to the server entered in the text box ( x.x.x.x format ) on port 20000
                socket = Gdx.net.newClientSocket(Net.Protocol.TCP, ip, 20000, socketHints);
                // Get the output and input TCP interfaces
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());
                try {
                    // Send the command "connect"
                    dataOutputStream.writeInt(0);
                    // Wait for the connection response
                    boolean connection = dataInputStream.readBoolean();
                    if (connection) {
                        // Dismiss progress dialog
                        humanoidRobotRemoteController.progressDialogRequestHandler.dismiss();
                        humanoidRobotRemoteController.requestHandler.toast("Successfully reconnected");
                    }
                    else {
                        connectionEnded = true;
                    }
                } catch (IOException e) {
                    connectionEnded = true;
                }
            } catch (GdxRuntimeException e) {
                connectionEnded = true;
            }
        }
        // Dismiss progress dialog
        humanoidRobotRemoteController.progressDialogRequestHandler.dismiss();
    }

    @Override
    public void render(float delta) {
        if (connectionEnded) {
            humanoidRobotRemoteController.requestHandler.toast("Disconnected from server");
            // Go back to the connection screen
            humanoidRobotRemoteController.setScreen(new ConnectionScreen(humanoidRobotRemoteController));
            this.dispose();
        }
        else {
            // Update the model every 0.1s
            elapsedTime += delta;
            if (elapsedTime > 0.1) {
                if (lock.tryLock()) {// Got the lock
                    try {
                        // Process record
                        if (calculateTransforms) {
                            robot.calculateTransforms();
                            // Joints
                            for (Joint joint : joints) {
                                joint.getJoint().calculateTransforms();
                                joint.getSphere().calculateTransforms();
                            }
                            calculateTransforms = false;
                        }
                        if (pixmap != null) {
                            display.setDrawable(new SpriteDrawable(new Sprite(new Texture(pixmap))));
                            pixmap.dispose();
                            pixmap = null;
                        }
                    } finally {
                        // Make sure to unlock so that we don't cause a deadlock
                        lock.unlock();
                        exService.submit(new GetJointAnglesImagePixelsThread());
                    }
                }
                count++;
                if (count >= 2) {
                    if (unlocked) {
                        if (selectedJoint != null) {
                            if (arrowJoystickJoints.isTouched()) {
                                exService.submit(new AddJointsValuesThread(selectedJoint.getName(), arrowJoystickJoints.getKnobPercentX(), arrowJoystickJoints.getKnobPercentY(), 0f));
                            }
                            if (hatSwitch.isTouched()) {
                                exService.submit(new AddJointsValuesThread(selectedJoint.getName(), 0f, 0f, hatSwitch.getKnobPercent()));
                            }
                            if (selectedJoint.getName().equals("Head") || selectedJoint.getName().equals("Knee.L") || selectedJoint.getName().equals("Knee.R") || selectedJoint.getName().equals("Shoulder.L") || selectedJoint.getName().equals("Shoulder.R")) {
                                if (!hasArrowJoystick || hasHatSwitch) {
                                    stage.clear();
                                    stage.addActor(locker);
                                    stage.addActor(display);
                                    stage.addActor(deactivatedHatSwitch);
                                    hasHatSwitch = false;
                                    stage.addActor(arrowJoystickJoints);
                                    hasArrowJoystick = true;
                                }
                            } else if (selectedJoint.getName().equals("Wrist.L") || selectedJoint.getName().equals("Wrist.R")) {
                                if (hasArrowJoystick || !hasHatSwitch) {
                                    stage.clear();
                                    stage.addActor(locker);
                                    stage.addActor(display);
                                    stage.addActor(deactivatedArrowJoystickJoints);
                                    hasArrowJoystick = false;
                                    stage.addActor(hatSwitch);
                                    hasHatSwitch = true;
                                }
                            } else {
                                if (!hasArrowJoystick || !hasHatSwitch) {
                                    stage.clear();
                                    stage.addActor(locker);
                                    stage.addActor(display);
                                    stage.addActor(arrowJoystickJoints);
                                    hasArrowJoystick = true;
                                    stage.addActor(hatSwitch);
                                    hasHatSwitch = true;
                                }
                            }
                        } else {
                            if (hasArrowJoystick || hasHatSwitch) {
                                stage.clear();
                                stage.addActor(locker);
                                stage.addActor(display);
                                stage.addActor(deactivatedArrowJoystickJoints);
                                hasArrowJoystick = false;
                                stage.addActor(deactivatedHatSwitch);
                                hasHatSwitch = false;
                            }
                        }
                    } else {
                        if (arrowJoystick.isTouched()) {
                            float x = arrowJoystick.getKnobPercentX();
                            float y = arrowJoystick.getKnobPercentY();
                            if (y >= 0) {
                                exService.submit(new WalkTurnThread(y, x, 0f));
                            }
                            else {
                                exService.submit(new WalkTurnThread(0f, 0f, -x));
                            }
                        }
                    }
                    count = 0;
                }
                elapsedTime = 0f;
            }
            // Graphics
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            Gdx.gl.glClearColor(0.8f, 0.8f, 0.8f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            // Like spriteBatch, just with models!  pass in the box Instance and the environment
            modelBatch.begin(camera3D);
            modelBatch.render(robot, environment);
            if (unlocked) {
                for (Joint joint : joints) {
                    modelBatch.render(joint.getJoint(), environment);
                    modelBatch.render(joint.getSphere(), environment);
                }
            }
            modelBatch.end();
            stage.act(Gdx.graphics.getDeltaTime());
            stage.draw();
        }
    }

    @Override
    public void pause() {
        // Preferences
        Preferences preferences = Gdx.app.getPreferences("My Preferences");
        preferences.putString("ip", socket.getRemoteAddress().split(":")[0].substring(1));
        preferences.flush();
        socket.dispose();
        if (pixmap != null) {
            pixmap.dispose();
            pixmap = null;
        }
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        socket.dispose();
        exService.shutdown();
    }
}