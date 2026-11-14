package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.ClientBeam;
import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The beam of a lamp a player carries, on the client: from just ahead of the eye, beside the hand holding it (or short of
 * a wall the face is against), toward what the crosshair is on, so the spot sits on it. Traced once a tick as any moving
 * light is, and drawn every frame from the player's interpolated eye and view, so it follows the mouse. Its own holder
 * gets no inside haze and no lens glow from it: both need the eye in front of the lens.
 */
public final class CarriedBeam implements BeamSource {
    /** How far ahead of the eye, to the hand's side and below the eye the beam starts, in blocks. */
    private static final double AHEAD = 0.45;
    private static final double SIDE = 0.28;
    private static final double DOWN = 0.22;
    /**
     * How far along the view the beam crosses the crosshair, in blocks: where the crosshair meets a block within this,
     * else this far. Never nearer than {@link #MIN_CONVERGE}, or a beam from the hand would cross the view at a slant.
     */
    private static final double CONVERGE = 16;
    private static final double MIN_CONVERGE = 1;
    /** A start that would be inside a block moves back toward the eye to this share of the way to the block. */
    private static final double HAND_IN_WALL = 0.8;

    private final Player player;
    private final LightType type;
    private final ClientBeam beam;
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    @Nullable
    private DyeColor colour;
    private BeamPose lastPose;
    private boolean gone;

    CarriedBeam(Player player, LightType type) {
        this.player = player;
        this.type = type;
        this.beam = new ClientBeam(Objects.requireNonNull(type.beam()), (float) type.lensHalf());
        this.lastPose = pose(1);
    }

    Player player() {
        return player;
    }

    void tick(InteractionHand hand, @Nullable DyeColor colour) {
        this.hand = hand;
        this.colour = colour;
        lastPose = pose(1);
        beam.update(player.level(), lastPose, lastPose, 15, colour == null ? -1 : rgb());
    }

    void markGone() {
        gone = true;
    }

    public int rgb() {
        return colour == null ? type.colour() : colour.getTextureDiffuseColor() & 0xFFFFFF;
    }

    public Level level() {
        return player.level();
    }

    private BeamPose pose(float partialTick) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        double yaw = Math.toRadians(player.getViewYRot(partialTick));
        // The player's right, level whatever the pitch: (-cos yaw, 0, -sin yaw) faces west when looking south
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(look).normalize();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        double side = arm == HumanoidArm.RIGHT ? SIDE : -SIDE;
        Vec3 origin = eye.add(look.scale(AHEAD)).add(right.scale(side)).subtract(up.scale(DOWN));
        // Face to a wall, the hand is in it: start where the way from the eye meets it, or the beam lights its inside
        BlockHitResult toHand = level.clip(new ClipContext(eye, origin, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                player));
        if (toHand.getType() != HitResult.Type.MISS) {
            origin = eye.add(toHand.getLocation().subtract(eye).scale(HAND_IN_WALL));
        }
        // Aim at what the crosshair is on, so the spot sits on it near and far
        double reach = CONVERGE;
        BlockHitResult ahead = level.clip(new ClipContext(eye, eye.add(look.scale(CONVERGE)), ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, player));
        if (ahead.getType() != HitResult.Type.MISS) {
            reach = Math.max(MIN_CONVERGE, eye.distanceTo(ahead.getLocation()));
        }
        Vec3 direction = eye.add(look.scale(reach)).subtract(origin).normalize();
        return new BeamPose(origin, direction, true);
    }

    @Override
    public BeamPose tickBeam() {
        return lastPose;
    }

    @Override
    public BeamPose renderBeam(float partialTick) {
        return pose(partialTick);
    }

    @Override
    public ClientBeam beam() {
        return beam;
    }

    @Override
    public boolean isGone() {
        return gone || player.isRemoved();
    }
}
