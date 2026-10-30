package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.beam.PanTilt;
import com.goldskinmc.lightworks.content.directional.AimBoard;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlockEntity;
import com.goldskinmc.lightworks.light.BeamLightBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Light state: redstone dims a light (and the {@code invertRedstone} config flips that), lights are placed
 * level, and aim (and the Pan and Tilt boards' values) is saved, synced and turned with its block.
 */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightStateGameTests {
    private static final String EMPTY = "empty";
    private static final String INVERTED = "invert_redstone";
    /** Light at x=1 shining east onto a wall at x=4: the spot at x=3 is 2 blocks from the lens, at full strength. */
    private static final BlockPos LIGHT = new BlockPos(1, 2, 2);
    private static final BlockPos SPOT = new BlockPos(3, 2, 2);

    private LightStateGameTests() {
    }

    private static void eastLightAtWall(GameTestHelper helper) {
        helper.setBlock(SPOT.east(), Blocks.STONE);
        helper.setBlock(LIGHT, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.EAST));
    }

    /** A pressure plate holding an analog signal, north of the light. Nothing steps on it, so it keeps it. */
    private static void signal(GameTestHelper helper, int power) {
        BlockPos plate = LIGHT.north();
        helper.setBlock(plate.below(), Blocks.STONE);
        helper.setBlock(plate, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState()
                .setValue(WeightedPressurePlateBlock.POWER, power));
    }

    private static int spotLevel(GameTestHelper helper) {
        BlockState state = helper.getBlockState(SPOT);
        return state.is(LightworksBlocks.BEAM_LIGHT.get()) ? state.getValue(BeamLightBlock.LEVEL) : 0;
    }

    /** Signal 7 leaves brightness 8, so the spot gets 8 of the beam's 15. */
    @GameTest(template = EMPTY)
    public static void redstoneDimsTheLight(GameTestHelper helper) {
        signal(helper, 7);
        eastLightAtWall(helper);
        helper.succeedWhen(() -> {
            DirectionalLightBlockEntity light = helper.getBlockEntity(LIGHT);
            helper.assertTrue(light.lightState().signal() == 7, "light read signal " + light.lightState().signal());
            helper.assertTrue(spotLevel(helper) == 8, "expected light 8 at the spot, got " + spotLevel(helper));
        });
    }

    /** A full signal switches the light off and takes its light block away. */
    @GameTest(template = EMPTY)
    public static void fullSignalSwitchesTheLightOff(GameTestHelper helper) {
        eastLightAtWall(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(spotLevel(helper) == 15, "no full light at the spot"))
                .thenExecute(() -> helper.setBlock(LIGHT.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(spotLevel(helper) == 0, "light still on with signal 15"))
                .thenExecute(() -> helper.setBlock(LIGHT.north(), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(spotLevel(helper) == 15, "light did not come back"))
                .thenSucceed();
    }

    @BeforeBatch(batch = INVERTED)
    public static void invertRedstone(ServerLevel level) {
        LightworksConfig.setInvertRedstone(true);
    }

    @AfterBatch(batch = INVERTED)
    public static void restoreRedstone(ServerLevel level) {
        LightworksConfig.setInvertRedstone(false);
    }

    /** Inverted: dark without a signal, and signal 7 gives brightness 7. */
    @GameTest(template = EMPTY, batch = INVERTED)
    public static void invertedRedstoneLightsOnlyWhenPowered(GameTestHelper helper) {
        eastLightAtWall(helper);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spotLevel(helper) == 0, "inverted light is on without a signal"))
                .thenExecute(() -> signal(helper, 7))
                .thenWaitUntil(() -> helper.assertTrue(spotLevel(helper) == 7,
                        "expected light 7 at the spot, got " + spotLevel(helper)))
                .thenSucceed();
    }

    /** Aimed sideways from the floor, the beam lights the wall it now points at. */
    @GameTest(template = EMPTY)
    public static void aimedLightLightsTheAimedSpot(GameTestHelper helper) {
        BlockPos light = new BlockPos(1, 1, 2);
        BlockPos spot = new BlockPos(3, 1, 2);
        helper.setBlock(spot.east(), Blocks.STONE);
        helper.setBlock(light, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        DirectionalLightBlockEntity searchlight = helper.getBlockEntity(light);
        searchlight.setAim(Aim.fromDirection(0, 1, 0, 1, 0, 0));
        helper.assertTrue(searchlight.beamDirection().distanceTo(new Vec3(1, 0, 0)) < 1e-9,
                "aim should point east, points " + searchlight.beamDirection());
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(spot).is(LightworksBlocks.BEAM_LIGHT.get()),
                "no beam light where the light is aimed"));
    }

    /** Aim survives saving and loading, and reaches clients in the update packet. */
    @GameTest(template = EMPTY)
    public static void aimPersistsAndSyncs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        DirectionalLightBlockEntity light = helper.getBlockEntity(pos);
        Aim aim = new Aim(40, 30);
        light.setAim(aim);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        CompoundTag saved = light.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(light.getBlockPos(), light.getBlockState(), saved, registries);
        helper.assertTrue(loaded instanceof DirectionalLightBlockEntity copy && copy.aim().equals(aim),
                "aim lost in save and load");

        DirectionalLightBlockEntity client = new DirectionalLightBlockEntity(light.getType(), light.getBlockPos(),
                light.getBlockState());
        client.handleUpdateTag(light.getUpdateTag(registries), registries);
        helper.assertTrue(client.aim().equals(aim), "aim missing from the client update, got " + client.aim());
        helper.succeed();
    }

    /** Placed by a player facing east: level and east on the floor, straight out on a wall. */
    @GameTest(template = EMPTY)
    public static void placedLightsPointLevel(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setYRot(-90);
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos wall = new BlockPos(3, 2, 3);
        for (BlockPos pos : new BlockPos[] {floor, wall}) {
            BlockState state = LightworksBlocks.SEARCHLIGHT.getDefaultState()
                    .setValue(DirectionalLightBlock.FACING, pos == floor ? Direction.UP : Direction.SOUTH);
            helper.setBlock(pos, state);
            state.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(pos), state, player, ItemStack.EMPTY);
        }
        DirectionalLightBlockEntity onFloor = helper.getBlockEntity(floor);
        DirectionalLightBlockEntity onWall = helper.getBlockEntity(wall);
        helper.assertTrue(onFloor.beamDirection().distanceTo(new Vec3(1, 0, 0)) < 1e-9,
                "floor light placed facing east points " + onFloor.beamDirection());
        helper.assertTrue(onWall.aim().equals(Aim.STRAIGHT), "wall light placed with aim " + onWall.aim());
        helper.assertTrue(onFloor.panTilt().equals(new PanTilt(90, 0)), "floor boards read " + onFloor.panTilt());
        helper.succeed();
    }

    /**
     * Board values on a wall light come back exactly as set, also after saving and loading and on the
     * client, though the aim they turn into is rounded.
     */
    @GameTest(template = EMPTY)
    public static void boardValuesPersistAndSync(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.NORTH));
        DirectionalLightBlockEntity light = helper.getBlockEntity(pos);
        PanTilt set = new PanTilt(37, -23);
        light.setPanTilt(set);
        double down = light.beamDirection().y;
        helper.assertTrue(Math.abs(down + Math.sin(Math.toRadians(23))) < 0.02, "tilted down 23 degrees points " + light.beamDirection());
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        CompoundTag saved = light.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(light.getBlockPos(), light.getBlockState(), saved, registries);
        helper.assertTrue(loaded instanceof DirectionalLightBlockEntity copy && copy.panTilt().equals(set),
                "board values lost in save and load");
        DirectionalLightBlockEntity client = new DirectionalLightBlockEntity(light.getType(), light.getBlockPos(),
                light.getBlockState());
        client.handleUpdateTag(light.getUpdateTag(registries), registries);
        helper.assertTrue(client.panTilt().equals(set), "board values missing from the client update, got " + client.panTilt());
        helper.succeed();
    }

    /** Create's packet, run without a connection: its own loop picks the behaviour the value goes to. */
    private static final class BoardPacket extends ValueSettingsPacket {
        BoardPacket(BlockPos pos, int row, int value, int netId) {
            super(pos, row, value, null, null, Direction.UP, false, netId);
        }

        void applyTo(DirectionalLightBlockEntity light) {
            applySettings(null, light);
        }
    }

    /** Create's packet reaches the board it was sent for: tilt changes tilt and pan changes pan. */
    @GameTest(template = EMPTY)
    public static void eachBoardSetsItsOwnValue(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        DirectionalLightBlockEntity light = helper.getBlockEntity(pos);
        light.setPanTilt(new PanTilt(90, 0));
        new BoardPacket(light.getBlockPos(), 0, 30, light.getBehaviour(AimBoard.TILT).netId()).applyTo(light);
        helper.assertTrue(light.panTilt().equals(new PanTilt(90, 30)), "the tilt board gave " + light.panTilt());
        new BoardPacket(light.getBlockPos(), 1, 45, light.getBehaviour(AimBoard.PAN).netId()).applyTo(light);
        helper.assertTrue(light.panTilt().equals(new PanTilt(45, 30)), "the pan board gave " + light.panTilt());
        helper.succeed();
    }

    /** A click on the light's outline, straight in front of a box, reaches it: every light, mount, side and box. */
    @GameTest(template = EMPTY)
    public static void everyAimBoxCanBeClicked(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos world = helper.absolutePos(pos);
        for (BlockState light : new BlockState[] {LightworksBlocks.SEARCHLIGHT.getDefaultState(),
                LightworksBlocks.FLOODLIGHT.getDefaultState(), LightworksBlocks.HEADLIGHT.getDefaultState()}) {
            for (Direction mount : Direction.values()) {
                BlockState state = light.setValue(DirectionalLightBlock.FACING, mount);
                helper.setBlock(pos, state);
                DirectionalLightBlockEntity be = helper.getBlockEntity(pos);
                for (Direction side : Direction.values()) {
                    if (side.getAxis() == mount.getAxis()) {
                        continue;
                    }
                    Vec3 pan = clickBox(helper, be.getBehaviour(AimBoard.PAN), state, world, side);
                    Vec3 tilt = clickBox(helper, be.getBehaviour(AimBoard.TILT), state, world, side);
                    helper.assertTrue(pan.distanceTo(tilt) >= be.getBehaviour(AimBoard.PAN).getSlotPositioning().getScale() - 1e-6,
                            state + " boxes overlap on side " + side);
                }
            }
        }
        helper.succeed();
    }

    /** The Warning Light's pattern box takes a click straight in front of it, on every mount and side. */
    @GameTest(template = EMPTY)
    public static void warningLightBoxCanBeClicked(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos world = helper.absolutePos(pos);
        for (Direction mount : Direction.values()) {
            BlockState state = LightworksBlocks.WARNING_LIGHT.getDefaultState().setValue(SignalLampBlock.FACING, mount);
            helper.setBlock(pos, state);
            SignalLampBlockEntity be = helper.getBlockEntity(pos);
            for (Direction side : Direction.values()) {
                if (side.getAxis() != mount.getAxis()) {
                    clickBox(helper, be.getBehaviour(ScrollValueBehaviour.TYPE), state, world, side);
                }
            }
        }
        helper.succeed();
    }

    /** Clicks the outline straight behind a box's centre and checks the box takes it; gives the centre. */
    private static Vec3 clickBox(GameTestHelper helper, ScrollValueBehaviour board, BlockState state, BlockPos world, Direction side) {
        ValueBoxTransform.Sided slot = (ValueBoxTransform.Sided) board.getSlotPositioning();
        slot.fromSide(side);
        Vec3 centre = slot.getLocalOffset(helper.getLevel(), world, state);
        Vec3 from = Vec3.atLowerCornerOf(world).add(centre);
        Vec3 to = from.subtract(Vec3.atLowerCornerOf(side.getNormal()));
        BlockHitResult hit = state.getShape(helper.getLevel(), world).clip(from, to, world);
        helper.assertTrue(hit != null, state + " has no outline behind its box on side " + side);
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(world));
        helper.assertTrue(slot.testHit(helper.getLevel(), world, state, local), state + " box on side " + side + " cannot be clicked");
        return centre;
    }

    /** A contraption placing the light back turned a quarter keeps the beam pointing the same way on the block. */
    @GameTest(template = EMPTY)
    public static void aimTurnsWithTheBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockState before = LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.EAST);
        helper.setBlock(pos, before);
        DirectionalLightBlockEntity light = helper.getBlockEntity(pos);
        light.setAim(new Aim(30, 50));
        Vec3 direction = light.beamDirection();

        StructureTransform transform = new StructureTransform(BlockPos.ZERO, Direction.Axis.Y, Rotation.CLOCKWISE_90, Mirror.NONE);
        // Same block, so the block entity stays while its state turns, as when a contraption places it
        helper.setBlock(pos, transform.apply(before));
        light.transform(light, transform);

        Vec3 expected = transform.applyWithoutOffsetUncentered(direction);
        helper.assertTrue(light.beamDirection().distanceTo(expected) < 0.03,
                "turned beam points " + light.beamDirection() + ", expected " + expected);
        helper.succeed();
    }
}
