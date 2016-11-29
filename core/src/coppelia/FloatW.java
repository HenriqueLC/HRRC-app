// PUT_VREP_REMOTEAPI_COPYRIGHT_NOTICE_HERE

package coppelia;
import java.io.Serializable;

public class FloatW implements Serializable
{
    float w;

    public FloatW(float f)
    {
        w = f;
    }

    public void setValue(float i)
    {
        w = i;
    }

    public float getValue()
    {
        return w;
    }

    public void toDegrees() {
        w = (float)Math.toDegrees(w);
    }
}