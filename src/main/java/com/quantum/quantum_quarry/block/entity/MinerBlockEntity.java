package com.quantum.quantum_quarry.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.energy.EnergyStorage;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantum.quantum_quarry.init.BlockEntities;

public class MinerBlockEntity extends BlockEntity {
    public static final Logger LOGGER = LoggerFactory.getLogger(MinerBlockEntity.class);
    private BlockPos linkedQuarryPos;

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.MINER_BLOCK_ENTITY.get(), pos, state);
    }

    public void setLinkedQuarryPos(@Nullable BlockPos pos) {
        // Only update if the position actually changed
        if ((this.linkedQuarryPos == null && pos != null) || 
            (this.linkedQuarryPos != null && !this.linkedQuarryPos.equals(pos))) {
            this.linkedQuarryPos = pos;
            if (this.level != null && !this.level.isClientSide) {
                this.level.invalidateCapabilities(this.worldPosition);
                LOGGER.info("Linked Quarry updated to: {}", this.linkedQuarryPos);
            }
        }
    }

    @Nullable
    public BlockPos getLinkedQuarryPos() {
        return this.linkedQuarryPos;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (this.linkedQuarryPos != null) {
            tag.putInt("LinkedQuarryX", this.linkedQuarryPos.getX());
            tag.putInt("LinkedQuarryY", this.linkedQuarryPos.getY());
            tag.putInt("LinkedQuarryZ", this.linkedQuarryPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("LinkedQuarryX") && tag.contains("LinkedQuarryY") && tag.contains("LinkedQuarryZ")) {
            this.linkedQuarryPos = new BlockPos(
                tag.getInt("LinkedQuarryX"),
                tag.getInt("LinkedQuarryY"),
                tag.getInt("LinkedQuarryZ")
            );
        }
    }
}