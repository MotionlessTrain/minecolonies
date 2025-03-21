package com.minecolonies.coremod.entity.ai.citizen.enchanter;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.*;
import com.minecolonies.coremod.Network;
import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
import com.minecolonies.coremod.colony.buildings.modules.EnchanterStationsModule;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingEnchanter;
import com.minecolonies.coremod.colony.interactionhandling.StandardInteraction;
import com.minecolonies.coremod.colony.jobs.JobEnchanter;
import com.minecolonies.coremod.entity.ai.basic.AbstractEntityAICrafting;
import com.minecolonies.coremod.network.messages.client.CircleParticleEffectMessage;
import com.minecolonies.coremod.network.messages.client.StreamParticleEffectMessage;
import com.minecolonies.coremod.util.WorkerUtil;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.TranslationConstants.NO_WORKERS_TO_DRAIN_SET;

/**
 * Enchanter AI class.
 */
public class EntityAIWorkEnchanter extends AbstractEntityAICrafting<JobEnchanter, BuildingEnchanter>
{
    /**
     * Predicate to define an ancient tome which can be enchanted.
     */
    private static final Predicate<ItemStack> IS_ANCIENT_TOME = item -> !item.isEmpty() && item.getItem() == ModItems.ancientTome;

    /**
     * Predicate to define an ancient tome which can be enchanted.
     */
    private static final Predicate<ItemStack> IS_BOOK = item -> !item.isEmpty() && item.getItem() == Items.BOOK;

    /**
     * Min distance to drain from citizen.
     */
    private static final long MIN_DISTANCE_TO_DRAIN = 10;

    /**
     * Max progress ticks until drainage is complete (per Level).
     */
    private static final int MAX_PROGRESS_TICKS = 60;

    /**
     * Max progress ticks until drainage is complete (per Level).
     */
    private static final int MAX_ENCHANTMENT_TICKS = 60 * 5;

    /**
     * Minimum mana requirement per level.
     */
    private static final int MANA_REQ_PER_LEVEL = 10;

    /**
     * XP per drain
     */
    private static final double XP_PER_DRAIN = 10;

    /**
     * The citizen entity to gather from.
     */
    private ICitizenData citizenToGatherFrom = null;

    /**
     * Variable to check if the draining is in progress. And at which tick it is.
     */
    private int progressTicks = 0;

    /**
     * Creates the abstract part of the AI. Always use this constructor!
     *
     * @param job the job to fulfill
     */
    public EntityAIWorkEnchanter(@NotNull final JobEnchanter job)
    {
        super(job);
        super.registerTargets(
          new AITarget(IDLE, START_WORKING, TICKS_SECOND),
          new AITarget(START_WORKING, DECIDE, TICKS_SECOND),
          new AITarget(DECIDE, this::decide, TICKS_SECOND),
          new AITarget(ENCHANTER_DRAIN, this::gatherAndDrain, 10),
          new AITarget(ENCHANT, this::enchant, TICKS_SECOND)
        );
        worker.setCanPickUpLoot(true);
    }

    /**
     * Decide method of the enchanter. Check if everything is alright to work and then decide between gathering and draining and actually enchanting.
     *
     * @return the next state to go to.
     */
    protected IAIState decide()
    {
        worker.setItemInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        if (walkToBuilding())
        {
            return DECIDE;
        }

        final IAIState craftState = getNextCraftingState();
        if (craftState != getState() && !WorldUtil.isPastTime(world, 13000))
        {
            return craftState;
        }

        if (getPrimarySkillLevel() < getOwnBuilding().getBuildingLevel() * MANA_REQ_PER_LEVEL)
        {
            final BuildingEnchanter enchanterBuilding = getOwnBuilding();
            final EnchanterStationsModule module = enchanterBuilding.getFirstModuleOccurance(EnchanterStationsModule.class);
            if (module.getBuildingsToGatherFrom().isEmpty())
            {
                if (worker.getCitizenData() != null)
                {
                    worker.getCitizenData()
                      .triggerInteraction(new StandardInteraction(new TranslationTextComponent(NO_WORKERS_TO_DRAIN_SET), ChatPriority.BLOCKING));
                }
                return IDLE;
            }

            final int booksInInv = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), IS_BOOK);
            if (booksInInv <= 0)
            {
                final int numberOfBooksInBuilding = InventoryUtils.getCountFromBuilding(getOwnBuilding(), IS_BOOK);
                if (numberOfBooksInBuilding > 0)
                {
                    needsCurrently = new Tuple<>(IS_BOOK, 1);
                    return GATHERING_REQUIRED_MATERIALS;
                }
                checkIfRequestForItemExistOrCreateAsynch(new ItemStack(Items.BOOK, 1));
                return IDLE;
            }

            final BlockPos posToDrainFrom = module.getRandomBuildingToDrainFrom();
            if (posToDrainFrom == null)
            {
                return IDLE;
            }
            job.setBuildingToDrainFrom(posToDrainFrom);
            return ENCHANTER_DRAIN;
        }

        final int ancientTomesInInv = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), IS_ANCIENT_TOME);
        if (ancientTomesInInv <= 0)
        {
            final int amountOfAncientTomes = InventoryUtils.getCountFromBuilding(getOwnBuilding(), IS_ANCIENT_TOME);
            if (amountOfAncientTomes > 0)
            {
                needsCurrently = new Tuple<>(IS_ANCIENT_TOME, 1);
                return GATHERING_REQUIRED_MATERIALS;
            }
            checkIfRequestForItemExistOrCreateAsynch(new ItemStack(ModItems.ancientTome, 1), 1, 1, false);
            return IDLE;
        }

        return ENCHANT;
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return 1;
    }

    /**
     * Actually do the enchanting. Making some great effects for some time and then apply a random enchantment. Reduce own levels depending on the found enchantment.
     *
     * @return the next state to go to.
     */
    private IAIState enchant()
    {
        // this assumes that the only empty-output (pure loot table) recipes are for ancient tome -> enchanted book
        currentRecipeStorage = getOwnBuilding().getFirstModuleOccurance(BuildingEnchanter.CraftingModule.class).getFirstFulfillableRecipe(ItemStackUtils::isEmpty, 1, false);
        if (currentRecipeStorage == null)
        {
            progressTicks = 0;
            return DECIDE;
        }

        if (progressTicks++ < MAX_ENCHANTMENT_TICKS / getOwnBuilding().getBuildingLevel())
        {
            Network.getNetwork().sendToTrackingEntity(
              new CircleParticleEffectMessage(
                worker.position().add(0, 2, 0),
                ParticleTypes.ENCHANT,
                progressTicks), worker);

            Network.getNetwork().sendToTrackingEntity(
              new CircleParticleEffectMessage(
                worker.position().add(0, 1.5, 0),
                ParticleTypes.ENCHANT,
                progressTicks), worker);

            Network.getNetwork().sendToTrackingEntity(
              new CircleParticleEffectMessage(
                worker.position().add(0, 1, 0),
                ParticleTypes.ENCHANT,
                progressTicks), worker);

            if (worker.getRandom().nextBoolean())
            {
                worker.swing(Hand.MAIN_HAND);
            }
            else
            {
                worker.swing(Hand.OFF_HAND);
            }
            return getState();
        }

        final ICitizenData data = worker.getCitizenData();
        if (data != null)
        {
            final List<ItemStack> loot = currentRecipeStorage.fullfillRecipeAndCopy(getLootContext(), getOwnBuilding().getHandlers());
            if (loot != null)
            {
                final int enchantmentLevel = loot.stream()
                        .mapToInt(EntityAIWorkEnchanter::getEnchantedBookLevel)
                        .max().orElse(0);

                //Decrement mana.
                data.getCitizenSkillHandler().incrementLevel(Skill.Mana, -enchantmentLevel);
                incrementActionsDoneAndDecSaturation();
            }
        }

        currentRecipeStorage = null;
        progressTicks = 0;
        return IDLE;
    }

    private static int getEnchantedBookLevel(@NotNull final ItemStack stack)
    {
        if (stack.getItem().equals(Items.ENCHANTED_BOOK))
        {
            return EnchantedBookItem.getEnchantments(stack).stream()
                    .mapToInt(nbt -> ((CompoundNBT) nbt).getShort("lvl"))
                    .max().orElse(0);
        }
        return 0;
    }

    /**
     * Gather experience from a worker. Go to the hut of the worker. Wait for the worker. Drain, and then return to work building.
     *
     * @return next state to go to.
     */
    private IAIState gatherAndDrain()
    {
        if (job.getPosToDrainFrom() == null)
        {
            return IDLE;
        }

        if (walkToBlock(job.getPosToDrainFrom()))
        {
            return getState();
        }

        final AbstractBuilding buildingWorker = getOwnBuilding().getColony().getBuildingManager().getBuilding(job.getPosToDrainFrom(), AbstractBuilding.class);

        if (buildingWorker == null)
        {
            resetDraining();
            getOwnBuilding().getFirstModuleOccurance(EnchanterStationsModule.class).removeWorker(job.getPosToDrainFrom());
            return IDLE;
        }

        if (citizenToGatherFrom == null)
        {
            final List<AbstractEntityCitizen> workers = new ArrayList<>();
            for (final Optional<AbstractEntityCitizen> citizen : getModuleForJob().getAssignedEntities())
            {
                citizen.ifPresent(workers::add);
            }

            final AbstractEntityCitizen citizen;
            if (workers.size() > 1)
            {
                citizen = workers.get(worker.getRandom().nextInt(workers.size()));
            }
            else
            {
                if (workers.isEmpty())
                {
                    resetDraining();
                    return DECIDE;
                }
                citizen = workers.get(0);
            }

            citizenToGatherFrom = citizen.getCitizenData();
            progressTicks = 0;
            return getState();
        }

        if (!citizenToGatherFrom.getEntity().isPresent())
        {
            citizenToGatherFrom = null;
            return getState();
        }

        if (progressTicks == 0)
        {
            // If worker is too far away wait.
            if (BlockPosUtil.getDistance2D(citizenToGatherFrom.getEntity().get().blockPosition(), worker.blockPosition()) > MIN_DISTANCE_TO_DRAIN)
            {
                if (!job.incrementWaitingTicks())
                {
                    resetDraining();
                    return DECIDE;
                }
                return getState();
            }
        }

        progressTicks++;
        if (progressTicks < MAX_PROGRESS_TICKS)
        {
            final Vector3d start = worker.position().add(0, 2, 0);
            final Vector3d goal = citizenToGatherFrom.getEntity().get().position().add(0, 2, 0);

            Network.getNetwork().sendToTrackingEntity(
              new StreamParticleEffectMessage(
                start,
                goal,
                ParticleTypes.ENCHANT,
                progressTicks % MAX_PROGRESS_TICKS,
                MAX_PROGRESS_TICKS), worker);

            Network.getNetwork().sendToTrackingEntity(
              new CircleParticleEffectMessage(
                start,
                ParticleTypes.HAPPY_VILLAGER,
                progressTicks), worker);

            WorkerUtil.faceBlock(new BlockPos(goal), worker);

            if (worker.getRandom().nextBoolean())
            {
                worker.swing(Hand.MAIN_HAND);
            }
            else
            {
                worker.swing(Hand.OFF_HAND);
            }

            return getState();
        }

        final int bookSlot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.BOOK);
        if (bookSlot != -1)
        {
            final int size = citizenToGatherFrom.getInventory().getSlots();
            final int attempts = (int) (getSecondarySkillLevel() / 5.0);

            for (int i = 0; i < attempts; i++)
            {
                int randomSlot = worker.getRandom().nextInt(size);
                final ItemStack stack = citizenToGatherFrom.getInventory().getStackInSlot(randomSlot);
                if (!stack.isEmpty() && stack.isEnchantable())
                {
                    EnchantmentHelper.enchantItem(worker.getRandom(), stack, getSecondarySkillLevel() > 50 ? 2 : 1, false);
                    break;
                }
            }

            worker.getInventoryCitizen().extractItem(bookSlot, 1, false);
            worker.getCitizenData().getCitizenSkillHandler().incrementLevel(Skill.Mana, 1);
            worker.getCitizenExperienceHandler().addExperience(XP_PER_DRAIN);
            worker.getCitizenData().markDirty();
        }
        resetDraining();
        return IDLE;
    }

    /**
     * Helper method to reset all variables of the draining.
     */
    private void resetDraining()
    {

        getOwnBuilding().getFirstModuleOccurance(EnchanterStationsModule.class).setAsGathered(job.getPosToDrainFrom());
        citizenToGatherFrom = null;
        job.setBuildingToDrainFrom(null);
        progressTicks = 0;
        incrementActionsDoneAndDecSaturation();
    }

    @Override
    public Class<BuildingEnchanter> getExpectedBuildingClass()
    {
        return BuildingEnchanter.class;
    }
}
