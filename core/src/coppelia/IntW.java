// PUT_VREP_REMOTEAPI_COPYRIGHT_NOTICE_HERE

package coppelia;

import java.io.Serializable;

public class IntW implements Serializable
{
    int w;

    public IntW(int i)
    {
        w = i;
    }

    public void setValue(int i)
    {
        w = i;
    }

    public int getValue()
    {
        return w;
    }
}
