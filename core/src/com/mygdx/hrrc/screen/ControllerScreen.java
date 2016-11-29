package com.mygdx.hrrc.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
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
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.mygdx.hrrc.HRRC;
import com.mygdx.hrrc.screen.util.HatSwitch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA;

class ControllerScreen extends AbstractScreen implements InputProcessor {

    private Stage stage;
    private Touchpad arrowJoystick;
    private Image deactivatedArrowJoystick;
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
    private PrintWriter out;
    private BufferedReader in;
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
        Joint newSelectedJoint = getJoint(screenX, screenY);
        if (newSelectedJoint != null) {
            if (selectedJoint != null) {
                selectedJoint.getSphere().materials.first().set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0f));
            }
            if (newSelectedJoint != selectedJoint) {
                newSelectedJoint.getSphere().materials.first().set(new BlendingAttribute(false, 0.5f));
                selectedJoint = newSelectedJoint;
            }
            else {
                selectedJoint = null;
            }
        }
        return true;
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

    private class AddJointsValuesThread implements Runnable {
        private String jointID;
        private float yaw, pitch, roll;

        AddJointsValuesThread(String jointID, float yaw, float pitch, float roll) {
            this.jointID = jointID;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        @Override
        public void run() {
            out.println("addJointValue," + jointID + "," + Float.toString(yaw) + "," + Float.toString(pitch) + "," + Float.toString(roll));
        }
    }

    private class GetJointsValuesThread implements Runnable {
        @Override
        public void run() {
            if (lock.tryLock()) {
                // Got the lock
                try {
                    // Send the command "getUpdatedJoints"
                    out.println("getUpdatedJoints");
                    // Wait for the connection response
                    try {
                        String response = in.readLine();
                        if (response != null) {
                            // Convert the response string into a float array
                            String[] tabOfFloats = response.split(",");
                            float[] nodes = new float[tabOfFloats.length];
                            for (int i = 0; i < tabOfFloats.length; i++) {
                                nodes[i] = Float.parseFloat(tabOfFloats[i]);
                            }
                            // Head update
                            setEulerAngles("Head", nodes[0], nodes[1], 0f);
                            // Left leg update
                            setEulerAngles("Hip.L", -nodes[2], -nodes[3], -nodes[4]);
                            setEulerAngles("Knee.L", 0f, nodes[5], 0f);
                            setEulerAngles("Ankle.L", 0f, nodes[6], -nodes[7]);
                            // Right leg update
                            setEulerAngles("Hip.R", nodes[8], -nodes[9], -nodes[10]);
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
                            calculateTransforms = true;
                        } else {
                            connectionEnded = true;
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

    @Override
    public void show() {
        // 2D stuff
        stage = new Stage();
        TextureAtlas textureAtlas = humanoidRobotRemoteController.assetManager.get("data/controller.pack", TextureAtlas.class);
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
        deactivatedArrowJoystick = new Image(textureAtlas.findRegion("joystick-deactivated"));
        stage.addActor(deactivatedArrowJoystick);
        // Hat Switch
        Skin hatSwitchSkin = new Skin();
        hatSwitchSkin.addRegions(textureAtlas);
        Drawable hatSwitchBackground = hatSwitchSkin.getDrawable("hat-switch-background");
        Drawable hatSwitchKnob = hatSwitchSkin.getDrawable("hat-switch-knob");
        hatSwitch = new HatSwitch(hatSwitchKnob, 64f, hatSwitchBackground, 192f);
        hatSwitch.setWidth(hatSwitchBackground.getMinWidth());
        hatSwitch.setHeight(hatSwitchBackground.getMinHeight());
        deactivatedHatSwitch = new Image(textureAtlas.findRegion("hat-switch-deactivated"));
        stage.addActor(deactivatedHatSwitch);
        // 3D Stuff
        // A ModelBatch is like a SpriteBatch, just for models. Use it to batch up geometry for OpenGL
        modelBatch = new ModelBatch();
        // Model instance
        // Load the model by name
        Model robotModel = humanoidRobotRemoteController.assetManager.get("data/nao.g3db", Model.class);
        // Set its blending attribute
        robot = new ModelInstance(robotModel);
        for (Material robotMaterial : robot.materials) {
            robotMaterial.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 0.3f));
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
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        // Set Input processor
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, this));
        // Communication thread
        // Creates thread pool with one thread
        exService = Executors.newSingleThreadExecutor();
        lock = new ReentrantLock();
    }

    @Override
    public void resize(int width, int height) {
        arrowJoystick.setPosition(-width / 2 + height / 8, -height / 2 + height / 8);
        deactivatedArrowJoystick.setPosition(-width / 2 + height / 8, -height / 2 + height / 8);
        hatSwitch.setPosition(width / 2 - hatSwitch.getWidth() - height / 8, -height / 2 + height / 8);
        deactivatedHatSwitch.setPosition(width / 2 - hatSwitch.getWidth() - height / 8, -height / 2 + height / 8);
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
        socketHints.socketTimeout = 5000;
        if (!socket.isConnected()) {
            try {
                // Create the socket and try to connect to the server entered in the text box ( x.x.x.x format ) on port 20000
                socket = Gdx.net.newClientSocket(Net.Protocol.TCP, ip, 20000, socketHints);
                // Get the output and input TCP interfaces
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                // Send the command "connect"
                String command = "connect";
                out.println(command);
                try {
                    // Wait for the connection response
                    String response = in.readLine();
                    if (Boolean.valueOf(response)) {
                        // Dismiss progress dialog
                        humanoidRobotRemoteController.progressDialogRequestHandler.dismiss();
                        humanoidRobotRemoteController.requestHandler.toast("Successfully reconnected");
                    }
                    else {
                        connectionEnded = true;
                    }
                } catch (IOException e ) {
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
                    } finally {
                        // Make sure to unlock so that we don't cause a deadlock
                        lock.unlock();
                        exService.submit(new GetJointsValuesThread());
                    }
                }
                count++;
                if (count >= 2) {
                    if (selectedJoint != null) {
                        if (arrowJoystick.isTouched()) {
                            exService.submit(new AddJointsValuesThread(selectedJoint.getName(), arrowJoystick.getKnobPercentX(), arrowJoystick.getKnobPercentY(), 0f));
                        }
                        if (hatSwitch.isTouched()) {
                            exService.submit(new AddJointsValuesThread(selectedJoint.getName(), 0f, 0f, hatSwitch.getKnobPercent()));
                        }
                        if (selectedJoint.getName().equals("Head") || selectedJoint.getName().equals("Knee.L") || selectedJoint.getName().equals("Knee.R") || selectedJoint.getName().equals("Shoulder.L") || selectedJoint.getName().equals("Shoulder.R")) {
                            if (!hasArrowJoystick || hasHatSwitch) {
                                stage.clear();
                                stage.addActor(deactivatedHatSwitch);
                                hasHatSwitch = false;
                                stage.addActor(arrowJoystick);
                                hasArrowJoystick = true;
                            }
                        }
                        else if(selectedJoint.getName().equals("Wrist.L") || selectedJoint.getName().equals("Wrist.R")) {
                            if (hasArrowJoystick || !hasHatSwitch) {
                                stage.clear();
                                stage.addActor(deactivatedArrowJoystick);
                                hasArrowJoystick = false;
                                stage.addActor(hatSwitch);
                                hasHatSwitch = true;
                            }
                        }
                        else {
                            if (!hasArrowJoystick || !hasHatSwitch) {
                                stage.clear();
                                stage.addActor(arrowJoystick);
                                hasArrowJoystick = true;
                                stage.addActor(hatSwitch);
                                hasHatSwitch = true;
                            }
                        }
                    }
                    else {
                        if (hasArrowJoystick || hasHatSwitch) {
                            stage.clear();
                            stage.addActor(deactivatedArrowJoystick);
                            hasArrowJoystick = false;
                            stage.addActor(deactivatedHatSwitch);
                            hasHatSwitch = false;
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
            for (Joint joint : joints) {
                modelBatch.render(joint.getJoint(), environment);
                modelBatch.render(joint.getSphere(), environment);
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