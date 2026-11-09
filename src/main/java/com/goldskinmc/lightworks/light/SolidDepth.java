package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * A copy of the depth buffer as it is once block entities are drawn and before translucent blocks are, for the late pass
 * ({@link LateBeams}). Some shader packs (Complementary, Bliss) leave glass and water in the final depth buffer, so beams
 * drawn after them were cut off behind every pane and below every water surface, where the usual beam, drawn with block
 * entities, shows through. Tested against this copy instead, they do again. The copy has the main depth texture's own
 * format (Iris picks it per pack), so the depth can be copied either way with a plain blit.
 */
final class SolidDepth {
    private static int framebuffer = -1;
    private static int texture = -1;
    private static int width;
    private static int height;
    private static int format;
    /** Whether a copy was taken this frame. */
    private static boolean taken;
    /** Set when the copy cannot be made here: the beams then test against the final depth. */
    private static boolean broken;

    private SolidDepth() {
    }

    /** Copies the main depth buffer now, in the middle of drawing the level; whatever was bound stays bound. */
    static void take(RenderTarget main) {
        taken = false;
        if (broken || main.getDepthTextureId() <= 0) {
            return;
        }
        int drawBefore = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readBefore = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            if (!ready(main)) {
                return;
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            taken = true;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readBefore);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawBefore);
        }
    }

    /**
     * Puts this frame's copy into the main depth buffer, which Minecraft clears before the hand anyway; false when there
     * is none. Leaves the main target bound for drawing.
     */
    static boolean putBack(RenderTarget main) {
        boolean put = taken && !broken && main.width == width && main.height == height;
        taken = false;
        if (put) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        }
        main.bindWrite(false);
        return put;
    }

    /** Makes the copy's framebuffer match the main target's size and depth format. */
    private static boolean ready(RenderTarget main) {
        int textureBefore = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, main.getDepthTextureId());
            int mainFormat = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            if (framebuffer >= 0 && main.width == width && main.height == height && mainFormat == format) {
                return true;
            }
            release();
            width = main.width;
            height = main.height;
            format = mainFormat;
            boolean stencil = format == GL30.GL_DEPTH24_STENCIL8 || format == GL30.GL_DEPTH32F_STENCIL8;
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            int type = format == GL30.GL_DEPTH24_STENCIL8 ? GL30.GL_UNSIGNED_INT_24_8
                    : format == GL30.GL_DEPTH32F_STENCIL8 ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : GL11.GL_FLOAT;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0,
                    stencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT, type, (ByteBuffer) null);
            framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, stencil ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, texture, 0);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                Lightworks.LOGGER.warn("Lightworks: no copy of the depth buffer (format {}, status {}); beams drawn after "
                        + "the shader pack stop at glass and water", Integer.toHexString(format), Integer.toHexString(status));
                release();
                broken = true;
                return false;
            }
            return true;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBefore);
        }
    }

    private static void release() {
        if (framebuffer >= 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = -1;
        }
        if (texture >= 0) {
            GL11.glDeleteTextures(texture);
            texture = -1;
        }
    }
}
