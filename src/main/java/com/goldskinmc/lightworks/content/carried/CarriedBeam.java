package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.ClientBeam;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
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
 * The beam of a lamp a player carries, on the client: a Flashlight's in first person from just ahead of the eye, beside
 * the hand holding it (or short of a wall the face is against), seen from outside from the player model's hand; a
 * headlamp's from the forehead, the same either way. Either way toward what
 * the player's crosshair is on, so the spot sits on it. Traced once a tick as any moving
 * light is, and drawn every frame from the player's interpolated eye and view, so it follows the mouse. Its own holder
 * gets no inside haze and no lens glow from it: both need the eye in front of the lens.
 */
public final class CarriedBeam implements BeamSource {
    /**
     * The player model's hand, in blocks: the arm's centre is 6 model pixels out from the body's middle and its pivot 22
     * up from the feet, 0.33 below a standing player's eye; the arm is 10 pixels long and, holding an item, hangs about
     * 18 degrees forward; the lamp's front is a little beyond the fist. Model pixels are 0.9375 / 16 of a block.
     */
    private static final double SHOULDER_OUT = 0.35;
    private static final double SHOULDER_BELOW_EYE = 0.33;
    private static final double ARM_DOWN = 0.56;
    private static final double ARM_FORWARD = 0.33;
    /** In first person: how far ahead of the eye, to the hand's side and below the eye the beam starts, in blocks. */
    private static final double AHEAD = 0.45;
    private static final double SIDE = 0.28;
    private static final double DOWN = 0.22;
    /**
     * How far along the view the beam crosses the crosshair, in blocks: where the crosshair meets a block within this,
     * else this far. Never nearer than {@link #MIN_CONVERGE}, or a beam from the hand would cross the view at a slant.
     */
    private static final double CONVERGE = 16;
    private static final double MIN_CONVERGE = 1;
    /**
     * The player model's neck, where its head turns, below the eye in blocks: standing (24 model pixels up, the model
     * drawn at 0.9375, against an eye at 1.62) and crouching (the head 4.2 pixels lower, the eye at 1.27).
     */
    private static final double NECK_BELOW_EYE = 0.214;
    private static final double NECK_BELOW_EYE_CROUCHING = 0.11;

    /**
     * Where a headlamp's lens is: up from the neck and ahead of it in the head's own frame, as {@code HeadlampLayer}
     * draws it ({@code headlamp/lens} centred 7.1 pixels up and 2.4 ahead of the head's front on goggles, 1.2 higher on
     * a helmet, times the model's 0.9375). Ahead of the eye, as a Flashlight's start is, so its wearer gets no inside
     * haze or lens glow from it.
     */
    private Vec3 headLens(Vec3 eye, Vec3 look, Vec3 up) {
        boolean goggles = Headlamps.onGoggles(player.getItemBySlot(EquipmentSlot.HEAD));
        double lensUp = (goggles ? 7.1 : 8.3) / 16 * 0.9375;
        double lensAhead = 6.4 / 16 * 0.9375;
        Vec3 neck = eye.subtract(0, player.isCrouching() ? NECK_BELOW_EYE_CROUCHING : NECK_BELOW_EYE, 0);
        return neck.add(up.scale(lensUp)).add(look.scale(lensAhead));
    }
    /** A start that would be inside a block moves back toward the eye to this share of the way to the block. */
    private static final double HAND_IN_WALL = 0.8;

    private final Player player;
    private final LightType type;
    /** A headlamp, worn on the head, rather than a Flashlight in a hand. */
    private final boolean worn;
    private final ClientBeam beam;
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    @Nullable
    private DyeColor colour;
    private BeamPose lastPose;
    private boolean gone;

    CarriedBeam(Player player, LightType type, boolean worn) {
        this.player = player;
        this.type = type;
        this.worn = worn;
        this.beam = new ClientBeam(Objects.requireNonNull(type.beam()), (float) type.lensHalf());
        // Turned with the mouse: LambDynamicLights' light follows the beam each frame
        this.beam.followSpotEveryFrame();
        // Aimed from the hand at the crosshair: its sides end near where it lands, not on into the sky
        this.beam.endSidesNearLanding();
        this.lastPose = pose(1);
    }

    Player player() {
        return player;
    }

    /** A tick of the lamp; {@code hand} is the hand holding it, or null for a headlamp. */
    void tick(@Nullable InteractionHand hand, @Nullable DyeColor colour) {
        if (hand != null) {
            this.hand = hand;
        }
        this.colour = colour;
        lastPose = pose(1);
        beam.setOwn(player == Minecraft.getInstance().player);
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
        double sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 origin = worn
                // A headlamp: its lens on the player model's head, turned with it, the same whoever looks
                ? headLens(eye, look, up)
                : seenFromOwnEyes()
                        ? eye.add(look.scale(AHEAD)).add(right.scale(sign * SIDE)).subtract(up.scale(DOWN))
                        : modelHand(eye, sign, partialTick);
        // Face to a wall, the lamp is in it: start where the way from the eye meets it, or the beam lights its inside
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
        // Its square keeps to the player's right, so it stays square to the view looking straight up or down
        return new BeamPose(origin, direction, true, right);
    }

    /**
     * True for the local player in first person, whose hand is drawn in a pass of its own with no place in the world.
     * Everyone else, and the local player in third person, has a hand on the player model.
     */
    /**
     * False for a headlamp seen from its wearer's own eyes: its beam starts just above the view and wraps round it, so
     * its sides were a faint glassy box over the whole picture. The light and the lit square stay.
     */
    public boolean showsBeam() {
        return !(worn && seenFromOwnEyes());
    }

    private boolean seenFromOwnEyes() {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    /**
     * Where the lamp is in the hand of the player model: the shoulder {@value #SHOULDER_BELOW_EYE} below the eye and
     * {@value #SHOULDER_OUT} out to the side, by the body's turn (the head turns apart from it), then down the arm,
     * which holding an item hangs a little forward, to the lamp's front.
     */
    private Vec3 modelHand(Vec3 eye, double sign, float partialTick) {
        double body = Math.toRadians(Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
        Vec3 forward = new Vec3(-Math.sin(body), 0, Math.cos(body));
        Vec3 right = new Vec3(-Math.cos(body), 0, -Math.sin(body));
        return eye.add(right.scale(sign * SHOULDER_OUT))
                .add(0, -SHOULDER_BELOW_EYE - ARM_DOWN, 0)
                .add(forward.scale(ARM_FORWARD));
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
