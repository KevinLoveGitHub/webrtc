package org.appspot.apprtc;

import org.webrtc.VideoRenderer;

/**
 * @author GP
 * @date 2017/4/26.
 */

public class ProxyRenderer implements VideoRenderer.Callbacks {
    private VideoRenderer.Callbacks target;

    synchronized public void renderFrame(VideoRenderer.I420Frame frame) {
        if (target == null) {
            VideoRenderer.renderFrameDone(frame);
            return;
        }

        target.renderFrame(frame);
    }

    synchronized public void setTarget(VideoRenderer.Callbacks target) {
        this.target = target;
    }
}
