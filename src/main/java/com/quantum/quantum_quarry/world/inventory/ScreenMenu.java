package com.quantum.quantum_quarry.world.inventory;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

import com.quantum.quantum_quarry.init.Menus;
import com.quantum.quantum_quarry.block.entity.QuarryBlockEntity;
import com.quantum.quantum_quarry.init.ModItems;
import com.quantum.quantum_quarry.procedures.FindCore;

public class ScreenMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>> {
    public final static HashMap<String, Object> guistate = new HashMap<>();
    public final Level world;
    public final Player entity;
    public int x, y, z;
    private ContainerLevelAccess access = ContainerLevelAccess.NULL;
    private IItemHandler internal;
    private final Map<Integer, Slot> customSlots = new HashMap<>();
    private boolean bound = false;
    private Supplier<Boolean> boundItemMatcher = null;
    private Entity boundEntity = null;
    private BlockEntity boundBlockEntity = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenMenu.class);

    public ScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(Menus.QUANTUM_MINER_SCREEN.get(), id);
        this.entity = inv.player;
        this.world = inv.player.level();
        this.internal = new ItemStackHandler(2);
        BlockPos pos = null;
        if (extraData != null) {
            pos = extraData.readBlockPos();
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getY();
            access = ContainerLevelAccess.create(world, pos);
        }
        if (pos != null) {
            if (extraData.readableBytes() == 1) {
                byte hand = extraData.readByte();
                ItemStack itemstack = hand == 0 ? this.entity.getMainHandItem() : this.entity.getOffhandItem();
                this.boundItemMatcher = () -> itemstack == (hand == 0 ? this.entity.getMainHandItem() : this.entity.getOffhandItem());
                IItemHandler cap = itemstack.getCapability(Capabilities.ItemHandler.ITEM);
                if (cap != null) {
                    this.internal = cap;
                    this.bound = true;
                }
            } else if (extraData.readableBytes() > 1) {
                extraData.readByte();
                boundEntity = world.getEntity(extraData.readVarInt());
                if (boundEntity != null) {
                    IItemHandler cap = boundEntity.getCapability(Capabilities.ItemHandler.ENTITY);
                    if (cap != null) {
                        this.internal = cap;
                        this.bound = true;
                    }
                }
            } else {
                boundBlockEntity = this.world.getBlockEntity(pos);
                if (boundBlockEntity instanceof BaseContainerBlockEntity baseContainerBlockEntity) {
                    this.internal = new InvWrapper(baseContainerBlockEntity);
                    this.bound = true;
                }
            }
        }
        this.customSlots.put(0, this.addSlot(new SlotItemHandler(internal, 0, 6, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == Items.ENCHANTED_BOOK;
            }

            @Override
            public void set(ItemStack stack) {
                if (stack != null && stack.getItem() != Items.AIR) {
                    LOGGER.info("Set Slot 0 to {} x {}", stack.getCount(), stack.getItem());
                    if (boundBlockEntity != null && boundBlockEntity instanceof QuarryBlockEntity quarryEntity) {
                        quarryEntity.setEnchants(stack);
                        LOGGER.info("Quarry Set!");
                    }
                } else {
                    LOGGER.info("Set Slot 0 to Empty!");
                    if (boundBlockEntity != null && boundBlockEntity instanceof QuarryBlockEntity quarryEntity) {
                        quarryEntity.setEnchants(null);
                        LOGGER.info("Quarry Set!");
                    }
                }
                super.set(stack);
            }
        }));
        this.customSlots.put(1, this.addSlot(new SlotItemHandler(internal, 1, 6, 38) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == ModItems.BIOME_MARKER.get();
            }

            @Override
            public void set(ItemStack stack) {
                if (stack != null && stack.getItem() != Items.AIR) {
                    LOGGER.info("Set Slot 1 to {} x {}", stack.getCount(), stack.getItem());
                    if (boundBlockEntity != null && boundBlockEntity instanceof QuarryBlockEntity quarryEntity) {
                        quarryEntity.setBiome(stack);
                        LOGGER.info("Quarry Set!");
                    }
                } else {
                    LOGGER.info("Set Slot 1 to Empty!");
                    if (boundBlockEntity != null && boundBlockEntity instanceof QuarryBlockEntity quarryEntity) {
                        quarryEntity.setBiome(null);
                        LOGGER.info("Quarry Set!");
                    }
                }
                super.set(stack);
            }
        }));
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + (row + 1) * 9, 8 + col * 18, 15 + 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 15 + 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.bound) {
            if (this.boundItemMatcher != null) {
                return this.boundItemMatcher.get();
            } else if (this.boundEntity != null) {
                return this.boundEntity.isAlive();
            } else if (this.boundBlockEntity != null) {
                if (FindCore.validateStructure(this.world, this.boundBlockEntity.getBlockPos())) {
                    return AbstractContainerMenu.stillValid(this.access, player, this.boundBlockEntity.getBlockState().getBlock());
                }
            } 
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot)this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index < 2) {
                if (!this.moveItemStackTo(itemstack1, 2, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(itemstack1, itemstack);
            } else if (!this.moveItemStackTo(itemstack1, 0, 2, false)) {
                if (index < 2 + 27) {
                    if (!this.moveItemStackTo(itemstack1, 2 + 27, this.slots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!this.moveItemStackTo(itemstack1, 2, 2 + 27, false)) {
                        return ItemStack.EMPTY;
                    }
                }
                return ItemStack.EMPTY;
            }
            if (itemstack1.getCount() == 0) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
                if (slot.getItem() == null) {
                    LOGGER.info("Slot {} is now empty.", index);
                } else {
                    LOGGER.info("Slot {} now contains {} x {}", index, slot.getItem().getCount(), slot.getItem().getHoverName().toString());
                }
            }
            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(playerIn, itemstack1);
        }
        return itemstack;
    }

    @Override
    protected boolean moveItemStackTo(ItemStack p_38904, int p_38905, int p_38906, boolean p_38907) {
        boolean flag = false;
        int i = p_38905;
        if (p_38907) {
            i = p_38906 - 1;
        }
        if (p_38904.isStackable()) {
            while (!p_38904.isEmpty()) {
                if (p_38907) {
                    if (i < p_38905) {
                        break;
                    }
                } else if (i >= p_38906) {
                    break;
                }
                Slot slot = this.slots.get(i);
                ItemStack itemstack = slot.getItem();
                if (slot.mayPlace(itemstack) && !itemstack.isEmpty() && ItemStack.isSameItemSameComponents(p_38904, itemstack)) {
                    int j = itemstack.getCount() + p_38904.getCount();
                    int maxSize = Math.min(slot.getMaxStackSize(), p_38904.getMaxStackSize());
                    if (j <= maxSize) {
                        p_38904.setCount(0);
                        itemstack.setCount(j);
                        slot.set(itemstack);
                        flag = true;
                    } else if (itemstack.getCount() < maxSize) {
                        p_38904.shrink(maxSize - itemstack.getCount());
                        itemstack.setCount(maxSize);
                        slot.set(itemstack);
                        flag = true;
                    }
                }
                if (p_38907) {
                    --i;
                } else {
                    ++i;
                }
            }
        }
        if (!p_38904.isEmpty()) {
            if (p_38907) {
                i = p_38906 - 1;
            } else {
                i = p_38905;
            }
            while (true) {
                if (p_38907) {
                    if (i < p_38905) {
                        break;
                    }
                } else if (i >= p_38906) {
                    break;
                }
                Slot slot1 = this.slots.get(i);
                ItemStack itemstack1 = slot1.getItem();
                if (itemstack1.isEmpty() && slot1.mayPlace(p_38904)) {
                    if (p_38904.getCount() > slot1.getMaxStackSize()) {
                        slot1.setByPlayer(p_38904.split(slot1.getMaxStackSize()));
                    } else {
                        slot1.setByPlayer(p_38904.split(p_38904.getCount()));
                    }
                    slot1.setChanged();
                    if (slot1.getItem() == null) {
                        LOGGER.info("Slot {} is now empty.", i);
                    } else {
                        LOGGER.info("Slot {} now contains {} x {}", i, slot1.getItem().getCount(), slot1.getItem().getHoverName().toString());
                    }
                    flag = true;
                    break;
                }
                if (p_38907) {
                    --i;
                } else {
                    ++i;
                }
            }
        }
        return flag;
    }

    @Override
    public void removed(Player playerIn) {
        super.removed(playerIn);
        if (!bound && playerIn instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.isAlive() || serverPlayer.hasDisconnected()) {
                for (int j = 0; j < internal.getSlots(); ++j) {
                    playerIn.drop(internal.getStackInSlot(j), false);
                    if (internal instanceof IItemHandlerModifiable ihm) {
                        ihm.setStackInSlot(j, ItemStack.EMPTY);
                    }
                }
            } else {
                for (int i = 0; i < internal.getSlots(); ++i) {
                    playerIn.getInventory().placeItemBackInInventory(internal.getStackInSlot(i));
                    if (internal instanceof IItemHandlerModifiable ihm) {
                        ihm.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        }
    }

    public Map<Integer, Slot> get() {
        return customSlots;
    }

    public BlockEntity getBoundEntity() {
        return this.boundBlockEntity;
    }
}