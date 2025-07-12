package com.quantum.quantum_quarry.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;

import com.quantum.quantum_quarry.block.entity.QuarryBlockEntity;
import com.quantum.quantum_quarry.init.BlockEntities;
import com.quantum.quantum_quarry.procedures.FindCore;
import com.quantum.quantum_quarry.world.inventory.ScreenMenu;
import com.quantum.quantum_quarry.block.entity.MinerBlockEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;

public class MinerBlock extends Block implements EntityBlock {
    public static final Logger LOGGER = LoggerFactory.getLogger(MinerBlock.class);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    //public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public MinerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.UP));
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MinerBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MinerBlockEntity minerEntity) {
                for (Direction direction : Direction.values()) {
                    BlockPos adjacentPos = pos.relative(direction);
                    BlockEntity adjacentEntity = level.getBlockEntity(adjacentPos);
                    if (adjacentEntity instanceof QuarryBlockEntity) {
                        minerEntity.setLinkedQuarryPos(adjacentPos);
                        LOGGER.info("Set Linked Quarry to {}", minerEntity.getLinkedQuarryPos());
                        break;
                    }
                }
            }
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos quarry = FindCore.execute(world, pos.getX(), pos.getY(), pos.getZ());
        if (quarry != null && world.getBlockState(quarry).getBlock() instanceof QuarryBlock quarryBlock) {
            return quarryBlock.useWithoutItem(world.getBlockState(quarry), world, quarry, player, hit);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        builder.add(POWERED);
        //builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            boolean isPowered = level.hasNeighborSignal(pos);
            boolean currentPowered = state.getValue(POWERED);
            
            // Only update if the powered state actually changed
            if (isPowered != currentPowered) {
                LOGGER.info("Miner Block Redstone State Changed at {}: Powered = {}", pos, isPowered);
                level.setBlock(pos, state.setValue(POWERED, isPowered), 3);
                
                // Find and notify the quarry
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof MinerBlockEntity minerEntity) {
                    BlockPos quarryPos = minerEntity.getLinkedQuarryPos();
                    if (quarryPos != null) {
                        BlockEntity quarryEntity = level.getBlockEntity(quarryPos);
                        if (quarryEntity instanceof QuarryBlockEntity quarryBlockEntity) {
                            quarryBlockEntity.updateMinerState(pos, isPowered);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        level.invalidateCapabilities(pos);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }
}