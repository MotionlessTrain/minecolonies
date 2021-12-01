package com.minecolonies.coremod.colony.buildings.workerbuildings;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;

import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.CraftingUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.client.gui.huts.WindowHutWorkerModulePlaceholder;
import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
import com.minecolonies.coremod.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.coremod.colony.buildings.modules.settings.PlantationSetting;
import com.minecolonies.coremod.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.coremod.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.coremod.colony.jobs.JobPlanter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.minecolonies.api.research.util.ResearchConstants.PLANT_2;
import static com.minecolonies.api.util.constant.BuildingConstants.CONST_DEFAULT_MAX_BUILDING_LEVEL;
import static com.minecolonies.api.util.constant.NbtTagConstants.*;
import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;

/**
 * Class of the plantation building. Worker will grow sugarcane/bamboo/cactus + craft paper and books.
 */
public class BuildingPlantation extends AbstractBuilding
{
    /**
     * Settings key for the building mode.
     */
    public static final ISettingKey<PlantationSetting> MODE = new SettingKey<>(PlantationSetting.class, new ResourceLocation(com.minecolonies.api.util.constant.Constants.MOD_ID, "mode"));

    /**
     * Description string of the building.
     */
    private static final String PLANTATION = "plantation";

    /**
     * List of sand blocks to grow onto.
     */
    private final List<BlockPos> sand = new ArrayList<>();

    /**
     * The current phase (default sugarcane).
     */
    private Item currentPhase = Items.SUGAR_CANE;

    /**
     * All the possible settings.
     */
    private final List<Item> settings = Arrays.asList(Items.SUGAR_CANE, Items.CACTUS, Items.BAMBOO);

    /**
     * Instantiates a new plantation building.
     *
     * @param c the colony.
     * @param l the location
     */
    public BuildingPlantation(final IColony c, final BlockPos l)
    {
        super(c, l);
        keepX.put(itemStack -> ItemStackUtils.hasToolLevel(itemStack, ToolType.AXE, TOOL_LEVEL_WOOD_OR_GOLD, getMaxToolLevel()), new Tuple<>(1, true));
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return PLANTATION;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return CONST_DEFAULT_MAX_BUILDING_LEVEL;
    }

    @Override
    public void registerBlockPosition(@NotNull final Block block, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        super.registerBlockPosition(block, pos, world);
        if (block == Blocks.SAND)
        {
            final Block down = world.getBlockState(pos.below()).getBlock();
            if (down == Blocks.COBBLESTONE || down == Blocks.STONE_BRICKS)
            {
                sand.add(pos);
            }
        }
    }

    @Override
    public void deserializeNBT(final CompoundTag compound)
    {
        super.deserializeNBT(compound);
        final ListTag sandPos = compound.getList(TAG_PLANTGROUND, Tag.TAG_COMPOUND);
        for (int i = 0; i < sandPos.size(); ++i)
        {
            sand.add(NbtUtils.readBlockPos(sandPos.getCompound(i).getCompound(TAG_POS)));
        }
        this.currentPhase = ItemStack.of(compound.getCompound(TAG_CURRENT_PHASE)).getItem();
    }

    @Override
    public CompoundTag serializeNBT()
    {
        final CompoundTag compound = super.serializeNBT();
        @NotNull final ListTag sandCompoundList = new ListTag();
        for (@NotNull final BlockPos entry : sand)
        {
            @NotNull final CompoundTag sandCompound = new CompoundTag();
            sandCompound.put(TAG_POS, NbtUtils.writeBlockPos(entry));
            sandCompoundList.add(sandCompound);
        }
        compound.put(TAG_PLANTGROUND, sandCompoundList);
        compound.put(TAG_CURRENT_PHASE, new ItemStack(currentPhase).save(new CompoundTag()));
        return compound;
    }

    /**
     * Get a list of positions to check for crops for the current phase.
     *
     * @return the list of positions.
     */
    public List<BlockPos> getPosForPhase()
    {
        final List<BlockPos> filtered = new ArrayList<>();
        if (tileEntity != null && !tileEntity.getPositionedTags().isEmpty())
        {
            final Item phase = nextPlantPhase();
            for (final Map.Entry<BlockPos, List<String>> entry : tileEntity.getPositionedTags().entrySet())
            {
                if ((entry.getValue().contains("bamboo") && phase == Items.BAMBOO)
                      || (entry.getValue().contains("sugar") && phase == Items.SUGAR_CANE)
                      || (entry.getValue().contains("cactus") && phase== Items.CACTUS))
                {
                    filtered.add(getPosition().offset(entry.getKey()));
                }
            }
        }

        return filtered;
    }

    /**
     * Iterates over available plants
     * @return the item of the new or unchanged plant phase
     */
    public Item nextPlantPhase()
    {
        if (getColony().getResearchManager().getResearchEffects().getEffectStrength(PLANT_2) > 0)
        {
            int next = settings.indexOf(currentPhase);

            do
            {
                next = (next + 1) % settings.size();
            }
            while (settings.get(next) == getSetting());

            currentPhase = settings.get(next);
            return currentPhase;
        }

        return getSetting();
    }

    private Item getSetting()
    {
        final String setting = getSetting(MODE).getValue();
        if (setting.equals(Items.SUGAR_CANE.getDescriptionId()))
        {
            return Items.SUGAR_CANE;
        }
        if (setting.equals(Items.CACTUS.getDescriptionId()))
        {
            return Items.CACTUS;
        }
        if (setting.equals(Items.BAMBOO.getDescriptionId()))
        {
            return Items.BAMBOO;
        }
        return Items.SUGAR_CANE;
    }

    public static class CraftingModule extends AbstractCraftingBuildingModule.Crafting
    {
        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public CraftingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }

        @Override
        public boolean isRecipeCompatible(@NotNull final IGenericRecipe recipe)
        {
            if (!super.isRecipeCompatible(recipe)) return false;
            final Optional<Boolean> isRecipeAllowed = CraftingUtils.isRecipeCompatibleBasedOnTags(recipe, PLANTATION);
            return isRecipeAllowed.orElse(false);
        }
    }
}
