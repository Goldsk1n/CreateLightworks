package com.goldskinmc.lightworks.debug.selftest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The world a self-test runs in: the joining player's level, positions relative to where the player
 * stood, and everything needed to put the world back afterwards (blocks, time of day, player position,
 * config).
 */
final class Stage {
    final ServerLevel level;
    final ServerPlayer player;
    /** Where the player stood on joining; scenarios build relative to it. */
    final BlockPos origin;

    private final Map<BlockPos, BlockState> savedBlocks = new LinkedHashMap<>();
    private final Vec3 savedPlayerPos;
    private final float savedYaw;
    private final float savedPitch;
    private final long savedDayTime;
    private final boolean savedDaylightCycle;
    private final boolean savedServerLightBlocks;

    Stage(ServerPlayer player) {
        this.player = player;
        this.level = player.serverLevel();
        this.origin = player.blockPosition();
        savedPlayerPos = player.position();
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();
        savedDayTime = level.getDayTime();
        savedDaylightCycle = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        savedServerLightBlocks = LightworksConfig.serverLightBlocks();
    }

    BlockPos at(int dx, int dy, int dz) {
        return origin.offset(dx, dy, dz);
    }

    void set(BlockPos pos, BlockState state) {
        savedBlocks.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    /**
     * Remembers every block in the box (inclusive) without changing it, so blocks a contraption leaves
     * behind there are cleared on restore.
     */
    void reserve(BlockPos from, BlockPos to) {
        BlockPos.betweenClosed(from, to).forEach(pos -> savedBlocks.putIfAbsent(pos.immutable(), level.getBlockState(pos)));
    }

    void run(String command) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** Puts the player's feet at the centre of the block at origin + offset; the camera focus aims the view. */
    void stand(int dx, int dz) {
        BlockPos pos = at(dx, 0, dz);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
    }

    /** Midnight, with the daylight cycle stopped until the stage is restored. */
    void night() {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setDayTime(18000);
    }

    void log(String format, Object... args) {
        Lightworks.LOGGER.info("[selftest] " + String.format(format, args));
    }

    /**
     * Removes contraptions and glue near {@code pos} without placing their blocks. Call before
     * {@link #restoreBlocks()}: a contraption still alive when its piston or bearing is removed would put
     * its blocks back into the world after the restore.
     */
    void discardContraptions(BlockPos pos, int radius) {
        AABB area = new AABB(pos).inflate(radius);
        level.getEntitiesOfClass(AbstractContraptionEntity.class, area).forEach(Entity::discard);
        level.getEntitiesOfClass(SuperGlueEntity.class, area).forEach(Entity::discard);
    }

    /** Puts back every block this stage changed so far. */
    void restoreBlocks() {
        savedBlocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
        if (!savedBlocks.isEmpty()) {
            log("restored %d blocks", savedBlocks.size());
        }
        savedBlocks.clear();
    }

    void restoreAll() {
        restoreBlocks();
        LightworksConfig.setServerLightBlocks(savedServerLightBlocks);
        level.setDayTime(savedDayTime);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(savedDaylightCycle, level.getServer());
        player.teleportTo(level, savedPlayerPos.x, savedPlayerPos.y, savedPlayerPos.z, savedYaw, savedPitch);
    }
}
