package dev.stormward.block;

import dev.stormward.Stormward;
import dev.stormward.server.StormSavedData;
import dev.stormward.sim.StormStage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A scheduled block, with no ticking block entity or region scanning. */
public final class StormSirenBlock extends Block {
    public static final BooleanProperty POWERED = Properties.POWERED;
    public StormSirenBlock(Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(POWERED, false)); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(POWERED); }
    @Override protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old, boolean notify) {
        if (!world.isClient) world.scheduleBlockTick(pos, this, 20);
    }
    @Override protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        boolean dangerous = StormSavedData.get(world).storms.stream().anyMatch(s -> s.active()
            && (s.stage() == StormStage.WARNING || s.stage() == StormStage.TORNADO)
            && Math.hypot(s.x() - pos.getX(), s.z() - pos.getZ()) <= 256
            && dev.stormward.server.StormManager.loaded(world, (int)Math.floor(s.x()), (int)Math.floor(s.z())));
        if (state.get(POWERED) != dangerous) {
            world.setBlockState(pos, state.with(POWERED, dangerous), Block.NOTIFY_ALL);
            world.updateNeighborsAlways(pos, this);
        }
        if (dangerous && Stormward.CONFIG.sirenSounds && world.getTime() % 100 < 20)
            world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.3f, 0.55f);
        world.scheduleBlockTick(pos, this, 20);
    }
    @Override protected boolean emitsRedstonePower(BlockState state) { return true; }
    @Override protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) { return state.get(POWERED) ? 15 : 0; }
}
