package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.light.BeamLightBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Light state: redstone dims a light (and the {@code invertRedstone} config flips that), and aim is
 * saved, synced and turned with its block.
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
