package com.quantum.quantum_quarry.helpers.AlternateDimension;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Container;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.common.collect.ImmutableList;

import com.quantum.quantum_quarry.init.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup; 

public class ChunkMiner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkMiner.class);

    private final ServerLevel level;
    public final Queue<ItemStack> itemsToGive = new LinkedList<>();
    public final Queue<FluidStack> fluidsToGive = new LinkedList<>();
    public ResourceKey<Biome> currentBiome = null;
    public Holder<Biome> currentHolder = null;
    public DimensionType dimension = null;
    public int minedBlocks = 0;
    private ChunkPos currentPos = null;
    private LevelChunk currentChunk = null;
    public BlockPos.MutableBlockPos nextBlockToMine = null;

    private static final List<Item> potentialTools = ImmutableList.of(
        Items.DIAMOND_PICKAXE,
        Items.DIAMOND_AXE,
        Items.DIAMOND_SHOVEL,
        Items.DIAMOND_HOE,
        Items.DIAMOND_SWORD
    );
    

    public ChunkMiner(ServerLevel level) {
        this.level = level;
    }

    public void startMining(ItemStack enchants, ItemStack biome) {
        ResourceKey<Biome> targetBiome = null;
        ResourceKey<Level> targetDimension = null;
        
        if (biome != null && biome.has(DataComponents.STORED_BIOME.get())) {
            targetBiome = biome.get(DataComponents.STORED_BIOME.get());
            targetDimension = getDimensionForBiome(targetBiome);
            LOGGER.info("Mining with biome filter: {} in dimension: {}", targetBiome.location(), targetDimension.location());
        }
        
        ChunkPos randomPos = getRandomChunkPos();
        LevelChunk chunk = loadChunk(randomPos, targetDimension);
        
        // If we have a biome filter, keep trying until we find a matching chunk
        if (targetBiome != null && chunk != null) {
            ServerLevel targetLevel = level.getServer().getLevel(targetDimension);
            ResourceKey<Biome> chunkBiome = getBiomeOfChunk(targetLevel, randomPos);
            int attempts = 0;
            while (!targetBiome.equals(chunkBiome) && attempts < 100) {
                unloadChunk(chunk);
                randomPos = getRandomChunkPos();
                chunk = loadChunk(randomPos, targetDimension);
                if (chunk != null) {
                    chunkBiome = getBiomeOfChunk(targetLevel, randomPos);
                }
                attempts++;
            }
            if (attempts >= 100) {
                LOGGER.warn("Could not find chunk with biome {} after 100 attempts", targetBiome.location());
            }
        }
        
        if (chunk != null) {
            currentChunk = chunk;
            currentPos = randomPos;
            ServerLevel chunkLevel = (ServerLevel) chunk.getLevel();
            nextBlockToMine = new BlockPos.MutableBlockPos(
                chunk.getPos().getMinBlockX(),
                chunkLevel.getMinBuildHeight(),
                chunk.getPos().getMinBlockZ()
            );
            currentBiome = getBiomeOfChunk(chunkLevel, randomPos);
            currentHolder = getBiomeHolderOfChunk(chunkLevel, randomPos);
            LOGGER.info("Started mining chunk: {} with biome: {}", randomPos, currentBiome != null ? currentBiome.location() : "unknown");
        }
    }

    private ChunkPos getRandomChunkPos() {
        Random random = new Random();
        int x = random.nextInt(1875000 - (-1875000) + 1) + (-1875000);
        int z = random.nextInt(1875000 - (-1875000) + 1) + (-1875000);
        return new ChunkPos(x, z);
    }

    private LevelChunk loadChunk(ChunkPos pos, ResourceKey<Level> dimension) {
        if (dimension == null) {
            dimension = getRandomDimension();
        }
        ServerLevel targetLevel = level.getServer().getLevel(dimension);
        if (targetLevel == null) {
            LOGGER.warn("Dimension {} not found!", dimension.location());
            return null;
        }
        ServerChunkCache chunkCache = targetLevel.getChunkSource();
        chunkCache.addRegionTicket(TicketType.UNKNOWN, pos, 0, pos);
        ChunkAccess chunkAccess = chunkCache.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
        if (chunkAccess instanceof LevelChunk) {
            LOGGER.info("Loaded chunk at position: {} in dimension: {}", pos, dimension);
            this.currentPos = pos;
            this.dimension = targetLevel.dimensionType();
            this.currentBiome = getBiomeOfChunk(targetLevel, chunkAccess.getPos());
            this.currentHolder = getBiomeHolderOfChunk(targetLevel, chunkAccess.getPos());
            return (LevelChunk)chunkAccess;
        } else {
            LOGGER.warn("Failed to cast LevelChunk to chunkAccess!");
            return null;
        }
    }

    private void unloadChunk(LevelChunk chunk) {
        Level targetLevel = chunk.getLevel();
        if (targetLevel instanceof ServerLevel serverLevel) {
            ServerChunkCache chunkCache = serverLevel.getChunkSource();
            chunkCache.removeRegionTicket(TicketType.UNKNOWN, chunk.getPos(), 0, chunk.getPos());
        }
        this.currentPos = null;
        LOGGER.info("Unloaded chunk at position: {} in dimension: {}", chunk.getPos(), targetLevel.dimension().location());
    }

    public boolean mineNextBlock(ItemStack enchants) {
        if (currentChunk == null || nextBlockToMine == null) {
            return false;
        }
        BlockState state = currentChunk.getBlockState(nextBlockToMine);
        Block block = state.getBlock();
        if (block != Blocks.AIR) {
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty() && fluidState.isSource()) {
                FluidStack fluidStack = new FluidStack(fluidState.getType(), 1000);
                fluidsToGive.add(fluidStack);
            } else { //We are assuming its impossible to have a block and fluid source at the same position
                BlockEntity blockEntity = currentChunk.getBlockEntity(nextBlockToMine);
                ServerLevel chunkLevel = (ServerLevel) currentChunk.getLevel();
                
                // Get the appropriate tool for this block
                ItemStack tool = getEnchantedTool(chunkLevel, state, enchants);
                
                // Get block drops (this includes the block itself and any special drops)
                List<ItemStack> drops = Block.getDrops(state, chunkLevel, nextBlockToMine, blockEntity, null, tool);
                itemsToGive.addAll(drops);
                
                // If this is a container, also get its contents
                if (blockEntity instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        ItemStack item = container.getItem(i);
                        if (!item.isEmpty()) {
                            itemsToGive.add(item.copy());
                        }
                    }
                }
            }
        } else {
            while (block == Blocks.AIR) {
                incrementNextBlockToMine();
                if (currentChunk == null || nextBlockToMine == null) {
                    return false;
                }
                state = currentChunk.getBlockState(nextBlockToMine);
                block = state.getBlock();
            }
            return mineNextBlock(enchants);
        }
        incrementNextBlockToMine();
        return true;
    }

    private void incrementNextBlockToMine() {
        if (nextBlockToMine == null || currentChunk == null) {
            return;
        }
        ServerLevel chunkLevel = (ServerLevel) currentChunk.getLevel();
        nextBlockToMine.move(0, 1, 0);
        if (nextBlockToMine.getY() >= chunkLevel.getMaxBuildHeight()) {
            nextBlockToMine.move(1, chunkLevel.getMinBuildHeight() - nextBlockToMine.getY(), 0);
            if (nextBlockToMine.getX() > currentChunk.getPos().getMaxBlockX()) {
                nextBlockToMine.move(currentChunk.getPos().getMinBlockX() - nextBlockToMine.getX(), 0, 1);
                if (nextBlockToMine.getZ() > currentChunk.getPos().getMaxBlockZ()) {
                    unloadChunk(currentChunk);
                    currentChunk = null;
                    LOGGER.info("Finished mining chunk: {}", currentPos);
                }
            }
        }
    }

    private ItemStack getEnchantedTool(ServerLevel level, BlockState state, ItemStack enchants) {
        // Select the optimal tool for the block
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE); // Default tool
        
        // Check each potential tool to find the best one
        for (Item toolItem : potentialTools) {
            ItemStack toolStack = new ItemStack(toolItem);
            if (toolStack.isCorrectToolForDrops(state)) {
                tool = toolStack;
                break;
            }
        }
        
        // Apply enchantments from the book, but only if they're valid for this tool
        if (enchants != null && enchants.getItem() == Items.ENCHANTED_BOOK) {
            ItemEnchantments bookEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchants);
            
            for (var entry : bookEnchantments.entrySet()) {
                Holder<Enchantment> enchantmentHolder = entry.getKey();
                Enchantment enchantment = enchantmentHolder.value();
                int enchantLevel = bookEnchantments.getLevel(enchantmentHolder);
                
                // Check if this enchantment can be applied to this tool
                if (enchantment.canEnchant(tool)) {
                    tool.enchant(enchantmentHolder, enchantLevel);
                }
            }
        }
        
        return tool;
    }

    private ResourceKey<Level> getRandomDimension() {
        List<ResourceKey<Level>> dimensions = Arrays.asList(
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("quantum_quarry", "overworld_dimension_mock")),
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("quantum_quarry", "nether_dimension_mock")),
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("quantum_quarry", "end_dimension_mock"))
        );
        Random random = new Random();
        return dimensions.get(random.nextInt(dimensions.size()));
    }

    private ResourceKey<Level> getDimensionForBiome(ResourceKey<Biome> biome) {
        if (biome == null) {
            return getRandomDimension();
        }
        
        String biomePath = biome.location().getPath();
        
        // Nether biomes
        if (biomePath.contains("nether") || biomePath.contains("warped") || biomePath.contains("crimson") || 
            biomePath.contains("basalt") || biomePath.contains("soul")) {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("quantum_quarry", "nether_dimension_mock"));
        }
        
        // End biomes
        if (biomePath.contains("end") || biomePath.equals("the_void")) {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("quantum_quarry", "end_dimension_mock"));
        }
        
        // Default to overworld for all other biomes
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("quantum_quarry", "overworld_dimension_mock"));
    }

    public String getHumanReadableBiomeName() {
        if (this.level != null && this.currentBiome != null) {
            Registry<Biome> biomeRegistry = this.level.registryAccess().registryOrThrow(Registries.BIOME);
            Holder<Biome> biomeHolder = biomeRegistry.getHolder(this.currentBiome).orElse(null);
            if (biomeHolder != null && biomeHolder.isBound()) {
                ResourceLocation biomeId = biomeHolder.unwrapKey().orElse(null).location();
                return Component.translatable("biome." + biomeId.getNamespace() + "." + biomeId.getPath()).getString();
            }
        }
        return "Unknown Biome";
    }

    public static ResourceKey<Biome> getBiomeOfChunk(ServerLevel level, ChunkPos pos) {
        // Estimated
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        Holder<Biome> biome = chunk.getNoiseBiome(((pos.x << 4) + 8) >> 2, 0, ((pos.z << 4) + 8) >> 2);
        ResourceKey<Biome> biomeKey = biome.unwrapKey().orElse(null);
        return biomeKey;
    }

    public static Holder<Biome> getBiomeHolderOfChunk(ServerLevel level, ChunkPos pos) {
        return level.getChunk(pos.x, pos.z).getNoiseBiome(((pos.x << 4) + 8) >> 2, 0, ((pos.z << 4) + 8) >> 2);
    }

    public void resetForNewBiome() {
        if (currentChunk != null) {
            unloadChunk(currentChunk);
        }
        currentChunk = null;
        currentPos = null;
        nextBlockToMine = null;
        currentBiome = null;
        currentHolder = null;
        LOGGER.info("Reset chunk miner for new biome selection");
    }

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minedBlocks", this.minedBlocks);
        if (currentPos != null) {
            tag.putLong("currentChunkX", currentPos.x);
            tag.putLong("currentChunkZ", currentPos.z);
        }
        if (nextBlockToMine != null) {
            tag.putInt("nextX", nextBlockToMine.getX());
            tag.putInt("nextY", nextBlockToMine.getY());
            tag.putInt("nextZ", nextBlockToMine.getZ());
        }
        if (currentBiome != null) {
            tag.putString("currentBiome", currentBiome.location().toString());
        }
        return tag;
    }

    public void deserializeNBT(CompoundTag tag, HolderLookup.Provider provider) {
        this.minedBlocks = tag.getInt("minedBlocks");
        if (tag.contains("currentChunkX") && tag.contains("currentChunkZ")) {
            ChunkPos pos = new ChunkPos((int)tag.getLong("currentChunkX"), (int)tag.getLong("currentChunkZ"));
            currentChunk = loadChunk(pos, null);
            if (currentChunk != null) {
                currentPos = pos;
                currentBiome = getBiomeOfChunk(level, pos);
                currentHolder = getBiomeHolderOfChunk(level, pos);
            }
        }
        if (tag.contains("nextX") && tag.contains("nextY") && tag.contains("nextZ")) {
            nextBlockToMine = new BlockPos.MutableBlockPos(
                tag.getInt("nextX"),
                tag.getInt("nextY"), 
                tag.getInt("nextZ")
            );
        }
    }
}
