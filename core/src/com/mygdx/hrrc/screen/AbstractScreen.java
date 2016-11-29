package com.mygdx.hrrc.screen;

import com.badlogic.gdx.Screen;
import com.mygdx.hrrc.HRRC;

abstract class AbstractScreen implements Screen {

    HRRC humanoidRobotRemoteController;

    AbstractScreen(HRRC humanoidRobotRemoteController) {
        this.humanoidRobotRemoteController = humanoidRobotRemoteController;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
    }
}
