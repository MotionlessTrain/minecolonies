package com.minecolonies.core.entity.citizen;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.citizens.event.CitizenRemovedEvent;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.compatibility.Compatibility;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.combat.threat.ThreatTable;
import com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.EntityState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.citizenhandlers.*;
import com.minecolonies.api.entity.citizen.happiness.ExpirationBasedHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import com.minecolonies.api.entity.pathfinding.proxy.IWalkToProxy;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.inventory.container.ContainerCitizenInventory;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.MessageUtils.MessagePriority;
import com.minecolonies.api.util.constant.HappinessConstants;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.client.gui.WindowInteraction;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.eventhooks.citizenEvents.CitizenDiedEvent;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.JobKnight;
import com.minecolonies.core.colony.jobs.JobNetherWorker;
import com.minecolonies.core.colony.jobs.JobRanger;
import com.minecolonies.core.datalistener.DiseasesListener;
import com.minecolonies.core.entity.ai.minimal.*;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.CitizenAI;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.minecolonies.core.entity.citizen.citizenhandlers.*;
import com.minecolonies.core.entity.pathfinding.navigation.MovementHandler;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.entity.pathfinding.proxy.EntityCitizenWalkToProxy;
import com.minecolonies.core.event.EventHandler;
import com.minecolonies.core.event.TextureReloadListener;
import com.minecolonies.core.network.messages.client.ItemParticleEffectMessage;
import com.minecolonies.core.network.messages.client.VanillaParticleMessage;
import com.minecolonies.core.network.messages.client.colony.ColonyViewCitizenViewMessage;
import com.minecolonies.core.network.messages.client.colony.PlaySoundForCitizenMessage;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import com.minecolonies.core.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;

import static com.minecolonies.api.research.util.ResearchConstants.*;
import static com.minecolonies.api.util.ItemStackUtils.ISFOOD;
import static com.minecolonies.api.util.constant.CitizenConstants.*;
import static com.minecolonies.api.util.constant.Constants.*;
import static com.minecolonies.api.util.constant.HappinessConstants.DAMAGE;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_CITIZEN;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_COLONY_ID;
import static com.minecolonies.api.util.constant.StatisticsConstants.DEATH;
import static com.minecolonies.api.util.constant.Suppression.INCREMENT_AND_DECREMENT_OPERATORS_SHOULD_NOT_BE_USED_IN_A_METHOD_CALL_OR_MIXED_WITH_OTHER_OPERATORS_IN_AN_EXPRESSION;
import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.core.entity.ai.minimal.EntityAIInteractToggleAble.*;

/**
 * The Class used to represent the citizen entities.
 */
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.CouplingBetweenObjects", "PMD.ExcessiveClassLength"})
public class EntityCitizen extends AbstractEntityCitizen implements IThreatTableEntity
{
    /**
     * Cooldown for calling help, in ticks.
     */
    private static final int    CALL_HELP_CD        = 100;
    /**
     * The amount of damage a guard takes on blocking.
     */
    private static final float  GUARD_BLOCK_DAMAGE  = 0.5f;
    /**
     * Max speed factor.
     */
    private static final double MAX_SPEED_FACTOR    = 0.5;
    private static final int    CALL_TO_HELP_AMOUNT = 2;

    /**
     * It's citizen Id.
     */
    private int                       citizenId = 0;
    /**
     * The Walk to proxy (Shortest path through intermediate blocks).
     */
    private IWalkToProxy              proxy;
    /**
     * Reference to the data representation inside the colony.
     */
    private ICitizenData              citizenData;
    /**
     * The citizen experience handler.
     */
    private ICitizenExperienceHandler citizenExperienceHandler;

    /**
     * The citizen inv handler.
     */
    private ICitizenInventoryHandler  citizenInventoryHandler;

    /**
     * The citizen colony handler.
     */
    private ICitizenColonyHandler citizenColonyHandler;

    /**
     * The citizen job handler.
     */
    private ICitizenJobHandler citizenJobHandler;

    /**
     * The citizen sleep handler.
     */
    private ICitizenSleepHandler citizenSleepHandler;

    /**
     * Our custom combat tracker.
     */
    private final CitizenCombatTracker combatTracker;

    /**
     * The path-result of trying to move away
     */
    private PathResult moveAwayPath;

    /**
     * IsChild flag
     */
    private boolean child = false;

    /**
     * Timer for the call for help cd.
     */
    private int callForHelpCooldown = 0;

    /**
     * Distance walked for consuming food
     */
    private float lastDistanceWalked = 0;

    /**
     * Citizen data view.
     */
    private ICitizenDataView citizenDataView;

    /**
     * The location used for requests
     */
    private ILocation location = null;

    /**
     * The current chunkpos.
     */
    private ChunkPos lastChunk;

    /**
     * Our entities threat list
     */
    private final ThreatTable threatTable         = new ThreatTable<>(this);
    private       int         interactionCooldown = 0;

    /**
     * The citizen AI
     */
    private ITickRateStateMachine<IState> citizenAI = new TickRateStateMachine<>(CitizenAIState.IDLE, e -> {}, ENTITY_AI_TICKRATE);

    /**
     * Maximum air supply
     */
    private int maxAir = 300;

    /**
     * Local client is glowing.
     */
    private boolean isGlowing;

    /**
     * Constructor for a new citizen typed entity.
     *
     * @param type  the entity type.
     * @param world the world.
     */
    public EntityCitizen(final EntityType<? extends PathfinderMob> type, final Level world)
    {
        super(type, world);
        this.citizenExperienceHandler = new CitizenExperienceHandler(this);
        this.citizenInventoryHandler = new CitizenInventoryHandler(this);
        this.citizenColonyHandler = new CitizenColonyHandler(this);
        this.citizenJobHandler = new CitizenJobHandler(this);
        this.citizenSleepHandler = new CitizenSleepHandler(this);

        this.combatTracker = new CitizenCombatTracker(this);
        this.moveControl = new MovementHandler(this);
        this.setPersistenceRequired();
        this.setCustomNameVisible(MineColonies.getConfig().getServer().alwaysRenderNameTag.get());

        entityStateController.addTransition(new TickingTransition<>(EntityState.INIT, () -> true, this::initialize, 40));

        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_CLIENT, () -> {
            citizenColonyHandler.updateColonyClient();
            return false;
        }, () -> null, 1));

        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_CLIENT, this::shouldBeInactive, () -> EntityState.INACTIVE, TICKS_20));
        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_CLIENT, this::refreshCitizenDataView, () -> null, TICKS_20));

        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_SERVER, this::updateSaturation, () -> null, HEAL_CITIZENS_AFTER));
        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_SERVER, this::updateVisualData, () -> null, 200));
        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_SERVER, this::onServerUpdateHandlers, () -> null, TICKS_20));
        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_SERVER, this::onTickDecrements, () -> null, 1));
        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_SERVER, this::shouldBeInactive, () -> EntityState.INACTIVE, TICKS_20));
        entityStateController.addTransition(new TickingTransition<>(EntityState.ACTIVE_SERVER, () -> {
            citizenAI.tick();
            return false;
        }, () -> null, 1));

        entityStateController.addTransition(new TickingTransition<>(EntityState.INACTIVE, this::isAlive, () -> EntityState.INIT, 100));
    }

    /**
     * Whether the entity should be inactive
     *
     * @return
     */
    private boolean shouldBeInactive()
    {
        if (citizenData == null && citizenDataView == null)
        {
            return true;
        }
        return !isAlive();
    }

    /**
     * Initializes vital colony and data connections before the entity is active
     */
    private EntityState initialize()
    {
        if (CompatibilityUtils.getWorldFromCitizen(this).isClientSide)
        {
            citizenColonyHandler.updateColonyClient();
            if (citizenColonyHandler.getColonyId() != 0 && citizenId != 0)
            {
                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level().dimension());
                if (colonyView != null)
                {
                    this.citizenDataView = colonyView.getCitizen(citizenId);
                    if (citizenDataView != null)
                    {
                        initTasks();
                        return EntityState.ACTIVE_CLIENT;
                    }
                }
            }
        }
        else
        {
            citizenColonyHandler.registerWithColony(citizenColonyHandler.getColonyId(), citizenId);
            if (citizenData != null && isAlive() && citizenColonyHandler.getColonyOrRegister() != null)
            {
                initTasks();
                return EntityState.ACTIVE_SERVER;
            }
        }

        return null;
    }

    /**
     * Initiates citizen goalSelector Suppressing Sonar Rule Squid:S881 The rule thinks we should extract ++priority in a proper statement. But in this case the rule does not apply
     * because that would remove the readability.
     */
    @SuppressWarnings(INCREMENT_AND_DECREMENT_OPERATORS_SHOULD_NOT_BE_USED_IN_A_METHOD_CALL_OR_MIXED_WITH_OTHER_OPERATORS_IN_AN_EXPRESSION)
    private void initTasks()
    {
        new CitizenAI(this);

        int priority = 0;
        this.goalSelector.addGoal(priority, new EntityAIFloat(this));
        this.goalSelector.addGoal(priority, new EntityAIInteractToggleAble(this, FENCE_TOGGLE, TRAP_TOGGLE, DOOR_TOGGLE));
        this.goalSelector.addGoal(++priority, new LookAtEntityInteractGoal(this, Player.class, WATCH_CLOSEST2, 0.2F));
        this.goalSelector.addGoal(++priority, new LookAtEntityInteractGoal(this, EntityCitizen.class, WATCH_CLOSEST2_FAR, WATCH_CLOSEST2_FAR_CHANCE));
        this.goalSelector.addGoal(++priority, new LookAtEntityGoal(this, LivingEntity.class, WATCH_CLOSEST));
    }

    /**
     * Called when a player tries to interact with a citizen.
     *
     * @param player which interacts with the citizen.
     * @return If citizen should interact or not.
     */
    @NotNull
    @Override
    public InteractionResult checkAndHandleImportantInteractions(final Player player, @NotNull final InteractionHand hand)
    {
        final IColonyView iColonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), player.level().dimension());
        if (iColonyView != null && !iColonyView.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
        {
            return InteractionResult.FAIL;
        }

        if (!ItemStackUtils.isEmpty(player.getItemInHand(hand)) && player.getItemInHand(hand).is(Items.NAME_TAG))
        {
            return super.checkAndHandleImportantInteractions(player, hand);
        }

        final InteractionResult result = directPlayerInteraction(player, hand);
        if (result != null)
        {
            return result;
        }

        if (CompatibilityUtils.getWorldFromCitizen(this).isClientSide && iColonyView != null)
        {
            if (player.isShiftKeyDown() && !isInvisible())
            {
                new OpenInventoryMessage(iColonyView, this.getName().getString(), this.getId()).sendToServer();
            }
            else
            {
                final ICitizenDataView citizenDataView = getCitizenDataView();
                if (citizenDataView != null && !isInvisible())
                {
                    new WindowInteraction(citizenDataView).open();
                }
            }
        }

        if (!level().isClientSide && getCitizenData() != null)
        {
            citizenData.update(TICKS_SECOND * 3);
            citizenData.setInteractedRecently(player.getUUID());
            final ColonyViewCitizenViewMessage message = new ColonyViewCitizenViewMessage((Colony) getCitizenData().getColony(), getCitizenData());
            message.sendToPlayer((ServerPlayer) player);

            if (citizenData.getJob() != null)
            {
                ((AbstractEntityAIBasic) citizenData.getJob().getWorkerAI()).setDelay(TICKS_SECOND * 3);
            }

            getNavigation().stop();
            getLookControl().setLookAt(player);
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Direct interaction actions with a player
     *
     * @param player
     * @param hand
     * @return interaction result
     */
    private InteractionResult directPlayerInteraction(final Player player, final InteractionHand hand)
    {
        if (player.isShiftKeyDown())
        {
            return null;
        }

        final ItemStack usedStack = player.getItemInHand(hand);
        if (MineColonies.getConfig().getServer().enableInDevelopmentFeatures.get() &&
              usedStack.getItem() instanceof BlockItem && ((BlockItem) usedStack.getItem()).getBlock() instanceof AbstractBlockHut<?>)
        {
            final BuildingEntry entry = ((AbstractBlockHut<?>) ((BlockItem) usedStack.getItem()).getBlock()).getBuildingEntry();
            for (final BuildingEntry.ModuleProducer moduleProducer : entry.getModuleProducers())
            {
                if (BuildingEntry.produceModuleWithoutBuilding(moduleProducer.key) instanceof WorkerBuildingModule module)
                {
                    getCitizenJobHandler().setModelDependingOnJob(module.getJobEntry().produceJob(null));
                    return InteractionResult.SUCCESS;
                }
            }
        }

        if (isInteractionItem(usedStack) && interactionCooldown > 0)
        {
            if (!level().isClientSide())
            {
                playSound(SoundEvents.VILLAGER_NO, 0.5f, (float) SoundUtils.getRandomPitch(getRandom()));
                MessageUtils.format(WARNING_INTERACTION_CANT_DO_NOW, this.getCitizenData().getName())
                  .withPriority(MessagePriority.DANGER)
                  .sendTo(player);
            }
            return InteractionResult.PASS;
        }

        final boolean isSick = (getCitizenData() != null && getCitizenData().getCitizenDiseaseHandler().isSick()) || (citizenDataView != null
            && citizenDataView.getVisibleStatus() == VisibleCitizenStatus.SICK);
        if (usedStack.getItem() == Items.GOLDEN_APPLE && isSick)
        {
            usedStack.shrink(1);
            player.setItemInHand(hand, usedStack);

            if (!level().isClientSide())
            {
                if (getRandom().nextInt(3) == 0)
                {
                    getCitizenData().getCitizenDiseaseHandler().cure();
                    playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, (float) SoundUtils.getRandomPitch(getRandom()));
                    new VanillaParticleMessage(getX(), getY(), getZ(), ParticleTypes.HAPPY_VILLAGER).sendToTrackingEntity(this);
                }
            }

            interactionCooldown = 20 * 60 * 5;
            return InteractionResult.CONSUME;
        }

        if (usedStack.is(ModTags.poisonous_food))
        {
            usedStack.shrink(1);
            player.setItemInHand(hand, usedStack);

            if (!level().isClientSide())
            {
                if (getCitizenData().getCitizenDiseaseHandler().setDisease(DiseasesListener.getRandomDisease(getRandom()))) {
                    playSound(SoundEvents.VILLAGER_HURT, 1.0f, (float) SoundUtils.getRandomPitch(getRandom()));
                    getCitizenData().markDirty(20);

                    MessageUtils.format(MESSAGE_INTERACTION_POISON, this.getCitizenData().getName())
                            .withPriority(MessagePriority.DANGER)
                            .sendTo(player);
                }
            }

            interactionCooldown = 20 * 20;
            return InteractionResult.CONSUME;
        }

        if (isSick)
        {
            return null;
        }

        if (ISFOOD.test(usedStack) && usedStack.getItem() != Items.GOLDEN_APPLE)
        {
            if (isBaby())
            {
                childFoodInteraction(usedStack, player, hand);
            }
            else
            {
                eatFoodInteraction(usedStack, player, hand);
            }
            return InteractionResult.CONSUME;
        }

        if (usedStack.getItem() == Items.BOOK && isBaby())
        {
            usedStack.shrink(1);
            player.setItemInHand(hand, usedStack);

            if (!level().isClientSide())
            {
                getCitizenData().getCitizenSkillHandler().addXpToSkill(Skill.Intelligence, 50, getCitizenData());
            }

            interactionCooldown = 20 * 60 * 5;
            return InteractionResult.CONSUME;
        }

        if (usedStack.getItem() == Items.CACTUS)
        {
            usedStack.shrink(1);
            player.setItemInHand(hand, usedStack);

            if (!level().isClientSide())
            {
                MessageUtils.format(MESSAGE_INTERACTION_OUCH, getCitizenData().getName()).sendTo(player);
                getNavigation().moveAwayFromLivingEntity(player, 5, 1);
                setJumping(true);
            }

            interactionCooldown = 20 * 60 * 5;
            return InteractionResult.CONSUME;
        }

        if (usedStack.getItem() == Items.GLOWSTONE_DUST)
        {
            usedStack.shrink(1);
            player.setItemInHand(hand, usedStack);

            if (!level().isClientSide())
            {
                addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 3));
            }

            interactionCooldown = 20 * 60 * 3;
            return InteractionResult.CONSUME;
        }

        return null;
    }

    /**
     * Tests if the itemstack is used for citizen interactions
     *
     * @param stack
     * @return
     */
    public boolean isInteractionItem(final ItemStack stack)
    {
        return ISFOOD.test(stack) || stack.getItem() == Items.BOOK || stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.CACTUS
                 || stack.getItem() == Items.GLOWSTONE_DUST || stack.is(ModTags.poisonous_food);
    }

    /**
     * Interaction with children for offering food
     *
     * @param usedStack
     * @param player
     * @param hand
     */
    private void childFoodInteraction(final ItemStack usedStack, final Player player, final InteractionHand hand)
    {
        if (usedStack.getDisplayName().getString().toLowerCase(Locale.US).contains("cookie"))
        {
            interactionCooldown = 100;

            if (!level().isClientSide())
            {
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300));

                playSound(SoundEvents.GENERIC_EAT, 1.5f, (float) SoundUtils.getRandomPitch(getRandom()));
                new ItemParticleEffectMessage(usedStack.copy(), getX(), getY(), getZ(), getXRot(), getYRot(), getEyeHeight()).sendToTrackingEntity(this);
                ItemStackUtils.consumeFood(usedStack, this, player);
            }
        }
        else
        {
            player.getInventory().removeItem(usedStack);
            player.drop(usedStack, true, true);
            if (!level().isClientSide())
            {
                playSound(SoundEvents.VILLAGER_NO, 1.0f, (float) SoundUtils.getRandomPitch(getRandom()));
                MessageUtils.format(MESSAGE_INTERACTION_COOKIE, this.getCitizenData().getName())
                  .withPriority(MessagePriority.DANGER)
                  .sendTo(player);
            }
        }
    }

    /**
     * Eats food on right click
     *
     * @param usedStack
     * @param player
     * @param hand
     */
    private void eatFoodInteraction(final ItemStack usedStack, final Player player, final InteractionHand hand)
    {
        if (!level().isClientSide())
        {
            playSound(SoundEvents.GENERIC_EAT, 1.5f, (float) SoundUtils.getRandomPitch(getRandom()));
            // Position needs to be centered on citizen, Eat AI wrong too?
            new ItemParticleEffectMessage(usedStack.copy(), getX(), getY(), getZ(), getXRot(), getYRot(), getEyeHeight()).sendToTrackingEntity(this);
            if (citizenData != null)
            {
                citizenData.getCitizenFoodHandler().addLastEaten(usedStack.getItem());
            }
            ItemStackUtils.consumeFood(usedStack, this, player);
        }

        interactionCooldown = 100;
    }

    @Override
    @NotNull
    public String getScoreboardName()
    {
        return getName().getString() + " (" + getCivilianID() + ")";
    }

    /**
     * Getter of the dataview, the clientside representation of the citizen.
     *
     * @return the view.
     */
    @Override
    public ICitizenDataView getCitizenDataView()
    {
        if (this.citizenDataView == null)
        {
            if (citizenColonyHandler.getColonyId() != 0 && citizenId != 0)
            {
                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level().dimension());
                if (colonyView != null)
                {
                    this.citizenDataView = colonyView.getCitizen(citizenId);
                    return this.citizenDataView;
                }
            }
        }
        else
        {
            return this.citizenDataView;
        }

        return null;
    }

    @Override
    public void addAdditionalSaveData(final CompoundTag compound)
    {
        super.addAdditionalSaveData(compound);

        // Avoid accessing chunks in here, may cause loads during unload
        compound.putInt(TAG_COLONY_ID, citizenColonyHandler.getColonyId());
        if (citizenData != null)
        {
            compound.putInt(TAG_CITIZEN, citizenData.getId());
        }
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag compound)
    {
        super.readAdditionalSaveData(compound);

        if (compound.contains(TAG_COLONY_ID))
        {
            citizenColonyHandler.setColonyId(compound.getInt(TAG_COLONY_ID));
            if (compound.contains(TAG_CITIZEN))
            {
                citizenId = compound.getInt(TAG_CITIZEN);
            }
        }
        setPose(Pose.STANDING);
    }

    /**
     * Called frequently so the entity can update its state every tick as required. For example, zombies and skeletons. use this to react to sunlight and start to burn.
     */
    @Override
    public void aiStep()
    {
        super.aiStep();
        if (interactionCooldown > 0)
        {
            interactionCooldown--;
        }
    }

    /**
     * Refreshes the saved view data
     *
     * @return false
     */
    public boolean refreshCitizenDataView()
    {
        if (citizenColonyHandler.getColonyId() != 0 && citizenId != 0)
        {
            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level().dimension());
            if (colonyView != null)
            {
                this.citizenDataView = colonyView.getCitizen(citizenId);
                // TODO: Why is this here on clientside?
                this.getNavigation().getPathingOptions().setCanUseRails(canPathOnRails());
                this.getNavigation().getPathingOptions().setCanClimbAdvanced(canClimbVines());
            }
        }
        return false;
    }

    /**
     * Decrements values each tick
     *
     * @return false
     */
    private boolean onTickDecrements()
    {
        decrementCallForHelpCooldown();
        decreaseWalkingSaturation();
        return false;
    }

    /**
     * Updates handlers on living tick, each 20 ticks.
     */
    private boolean onServerUpdateHandlers()
    {
        // Every 20 ticks
        citizenExperienceHandler.gatherXp();
        CitizenItemUtils.pickupItems(this);
        citizenData.setLastPosition(blockPosition());
        onLivingSoundUpdate();

        final ChunkPos currentChunk = chunkPosition();
        if ((!Objects.equals(currentChunk, lastChunk)))
        {
            lastChunk = currentChunk;
            EventHandler.onEnteringChunkEntity(this, currentChunk);
        }

        return false;
    }

    @Override
    public int getMaxAirSupply()
    {
        return maxAir;
    }

    /**
     * Updates visual data for the citizen
     *
     * @return false
     */
    private boolean updateVisualData()
    {
        this.setCustomNameVisible(MineColonies.getConfig().getServer().alwaysRenderNameTag.get());

        if (!citizenColonyHandler.getColonyOrRegister().getTextureStyleId().equals(getEntityData().get(DATA_STYLE)))
        {
            getEntityData().set(DATA_STYLE, citizenColonyHandler.getColonyOrRegister().getTextureStyleId());
        }
        if (!citizenData.getTextureSuffix().equals(getEntityData().get(DATA_TEXTURE_SUFFIX)))
        {
            getEntityData().set(DATA_TEXTURE_SUFFIX, citizenData.getTextureSuffix());
        }

        return false;
    }

    /**
     * Adds potion effect and regenerates life, depending on saturation
     */
    private boolean updateSaturation()
    {
        checkHeal();
        if (citizenData.getSaturation() <= 0)
        {
            if (this.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null)
            {
                this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TICKS_SECOND * 30));
            }
        }
        else
        {
            this.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        return false;
    }

    private void decrementCallForHelpCooldown()
    {
        if (callForHelpCooldown > 0)
        {
            callForHelpCooldown--;
        }
    }

    /**
     * A boolean check to test if the citizen can path on rails.
     *
     * @return true if so.
     */
    public boolean canPathOnRails()
    {
        if (level().isClientSide)
        {
            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level().dimension());
            if (colonyView != null)
            {
                return colonyView.getResearchManager().getResearchEffects().getEffectStrength(RAILS) > 0;
            }
            return false;
        }
        return getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(RAILS) > 0;
    }

    /**
     * A boolean check to test if the citizen can climb vines.
     *
     * @return true if so.
     */
    public boolean canClimbVines()
    {
        if (level().isClientSide)
        {
            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level().dimension());
            if (colonyView != null)
            {
                return colonyView.getResearchManager().getResearchEffects().getEffectStrength(VINES) > 0;
            }
            return false;
        }
        return getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(VINES) > 0;
    }

    /**
     * Reduces saturation for walking every 25 blocks.
     */
    private void decreaseWalkingSaturation()
    {
        if (walkDist - lastDistanceWalked > ACTIONS_EACH_BLOCKS_WALKED)
        {
            lastDistanceWalked = walkDist;
            decreaseSaturationForContinuousAction();
        }
    }

    /**
     * Checks the citizens health status and heals the citizen if necessary.
     */
    private void checkHeal()
    {
        if (getCitizenData() != null && getHealth() < (getCitizenData().getCitizenDiseaseHandler().isSick() ? getMaxHealth() / 3 : getMaxHealth()) && getLastHurtByMob() == null)
        {
            final double limitDecrease = getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffectStrength(SATLIMIT);
            final double citizenSaturation = citizenData.getSaturation();
            final double healAmount;
            if (citizenSaturation >= FULL_SATURATION + limitDecrease)
            {
                healAmount = 2 * (1.0 + getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(REGENERATION));
            }
            else if (citizenSaturation < LOW_SATURATION)
            {
                healAmount = 1 * (citizenSaturation / FULL_SATURATION) / 2.0;
            }
            else
            {
                healAmount = 1 * (1.0 + getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(REGENERATION));
            }

            heal((float) healAmount);
        }
    }

    /**
     * Plays a random sound by chance during day
     */
    private void onLivingSoundUpdate()
    {
        if (WorldUtil.isDayTime(level()) && !isSilent())
        {
            SoundUtils.playRandomSound(level(), this.blockPosition(), citizenData);
        }
    }

    @Override
    public boolean isBaby()
    {
        return child;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_COLONY_ID, citizenColonyHandler == null ? 0 : citizenColonyHandler.getColonyId());
        builder.define(DATA_CITIZEN_ID, citizenId);
    }

    /**
     * Set the metadata for rendering.
     *
     * @param metadata the metadata required.
     */
    @Override
    public void setRenderMetadata(final String metadata)
    {
        super.setRenderMetadata(metadata);

        //Display some debug info always available while testing
        //Will help track down some hard to find bugs (Pathfinding etc.)
        if (citizenJobHandler.getColonyJob() != null && MineColonies.getConfig().getServer().enableInDevelopmentFeatures.get())
        {
            super.setCustomName(Component.literal(
              citizenData.getName() + "[" + citizenJobHandler.getColonyJob().getNameTagDescription() + "]"));
        }
    }

    /**
     * Get the ILocation of the citizen.
     *
     * @return an ILocation object which contains the dimension and is unique.
     */
    @Override
    public ILocation getLocation()
    {
        if (location == null)
        {
            location = StandardFactoryController.getInstance().getNewInstance(TypeConstants.ILOCATION, this);
        }
        return location;
    }

    /**
     * Checks if a worker is at his working site. If he isn't, sets it's path to the location
     *
     * @param site  the place where he should walk to
     * @param range Range to check in
     * @return True if worker is at site, otherwise false.
     */
    @Override
    public boolean isWorkerAtSiteWithMove(@NotNull final BlockPos site, final int range)
    {
        if (proxy == null)
        {
            proxy = new EntityCitizenWalkToProxy(this);
        }
        return proxy.walkToBlock(site, range, true);
    }

    /**
     * Getter for the citizendata. Tries to get it from the colony is the data is null.
     *
     * @return the data.
     */
    @Override
    public ICitizenData getCitizenData()
    {
        return citizenData;
    }

    /**
     * Getter for the civilian data
     *
     * @return the data.
     */
    @Override
    public ICivilianData getCivilianData()
    {
        return citizenData;
    }

    /**
     * Setter for the citizen data.
     *
     * @param data the data to set.
     */
    @Override
    public void setCivilianData(@Nullable final ICivilianData data)
    {
        if (data != null)
        {
            this.citizenData = (ICitizenData) data;
            data.initEntityValues();
        }
    }

    /**
     * Return this citizens inventory.
     *
     * @return the inventory this citizen has.
     */
    @Override
    @NotNull
    public InventoryCitizen getInventoryCitizen()
    {
        return getCitizenData().getInventory();
    }

    @Override
    @NotNull
    public IItemHandler getItemHandlerCitizen()
    {
        return getInventoryCitizen();
    }

    /**
     * Mark the citizen dirty to synch the data with the client.
     */
    @Override
    public void markDirty(final int time)
    {
        if (citizenData != null)
        {
            citizenData.markDirty(time);
        }
    }

    /**
     * Sets whether this entity is a child
     *
     * @param isChild boolean
     */
    @Override
    public void setIsChild(final boolean isChild)
    {
        if (isChild && !this.child)
        {
            new EntityAICitizenChild(this);
        }
        else
        {
            if (!isChild && this.child)
            {
                this.child = isChild;
                getCitizenJobHandler().setModelDependingOnJob(citizenJobHandler.getColonyJob());
            }
        }
        this.child = isChild;
        this.getEntityData().set(DATA_IS_CHILD, isChild);
        refreshDimensions();
        markDirty(0);
    }

    /**
     * Play move away sound when running from an entity.
     */
    @Override
    public void playMoveAwaySound()
    {
        if (citizenJobHandler.getColonyJob() != null)
        {
            SoundUtils.playSoundAtCitizenWith(level(), blockPosition(), EventType.DANGER, getCitizenData());
        }
    }

    /**
     * Get the path proxy of the citizen.
     *
     * @return the proxy.
     */
    @Override
    public IWalkToProxy getProxy()
    {
        return proxy;
    }

    /**
     * Decrease the saturation of the citizen for 1 action.
     */
    @Override
    public void decreaseSaturationForAction()
    {
        if (citizenData != null)
        {
            citizenData.decreaseSaturation(citizenColonyHandler.getPerBuildingFoodCost());
            citizenData.markDirty(20 * 20);
        }
    }

    /**
     * Decrease the saturation of the citizen for 1 action.
     */
    @Override
    public void decreaseSaturationForContinuousAction()
    {
        if (citizenData != null)
        {
            citizenData.decreaseSaturation(citizenColonyHandler.getPerBuildingFoodCost() / 100.0);
            citizenData.markDirty(20 * 60 * 2);
        }
    }

    /**
     * Getter for the citizen id.
     *
     * @return the id.
     */
    @Override
    public int getCivilianID()
    {
        return citizenId;
    }

    /**
     * Setter for the citizen id.
     *
     * @param id the id to set.
     */
    @Override
    public void setCitizenId(final int id)
    {
        this.citizenId = id;
    }

    /**
     * The Handler for all experience related methods.
     *
     * @return the instance of the handler.
     */
    @Override
    public ICitizenExperienceHandler getCitizenExperienceHandler()
    {
        return citizenExperienceHandler;
    }

    /**
     * The Handler for all inventory related methods.
     *
     * @return the instance of the handler.
     */
    @Override
    public ICitizenInventoryHandler getCitizenInventoryHandler()
    {
        return citizenInventoryHandler;
    }

    @Override
    public void setCitizenInventoryHandler(final ICitizenInventoryHandler citizenInventoryHandler)
    {
        this.citizenInventoryHandler = citizenInventoryHandler;
    }

    /**
     * The Handler for all colony related methods.
     *
     * @return the instance of the handler.
     */
    @Override
    public ICitizenColonyHandler getCitizenColonyHandler()
    {
        return citizenColonyHandler;
    }

    @Override
    public void setCitizenColonyHandler(final ICitizenColonyHandler citizenColonyHandler)
    {
        this.citizenColonyHandler = citizenColonyHandler;
    }

    /**
     * The Handler for all job related methods.
     *
     * @return the instance of the handler.
     */
    @Override
    public ICitizenJobHandler getCitizenJobHandler()
    {
        return citizenJobHandler;
    }

    /**
     * The Handler for all job related methods.
     *
     * @return the instance of the handler.
     */
    @Override
    public ICitizenSleepHandler getCitizenSleepHandler()
    {
        return citizenSleepHandler;
    }

    /**
     * Sets the visible status if there is none
     *
     * @param status status to set
     */
    public void setVisibleStatusIfNone(final VisibleCitizenStatus status)
    {
        if (getCitizenData().getStatus() == null)
        {
            getCitizenData().setVisibleStatus(status);
        }
    }

    @Override
    public float getRotationYaw()
    {
        return this.getYRot();
    }

    @Override
    public float getRotationPitch()
    {
        return this.getXRot();
    }

    @Override
    public boolean isDead()
    {
        return !isAlive();
    }

    @Override
    public void setCitizenSleepHandler(final ICitizenSleepHandler citizenSleepHandler)
    {
        this.citizenSleepHandler = citizenSleepHandler;
    }

    @Override
    public void setCitizenJobHandler(final ICitizenJobHandler citizenJobHandler)
    {
        this.citizenJobHandler = citizenJobHandler;
    }

    @Override
    public void setCitizenExperienceHandler(final ICitizenExperienceHandler citizenExperienceHandler)
    {
        this.citizenExperienceHandler = citizenExperienceHandler;
    }

    @Override
    public boolean hurt(@NotNull final DamageSource damageSource, final float damage)
    {
        // TODO: temporary debug data
        if (damageSource.getEntity() instanceof Player player && player.isCreative() && player.getMainHandItem().getItem() == ModItems.scanAnalyzer)
        {
            CompoundTag tag = new CompoundTag();
            try
            {
                save(tag);
            }
            catch (Exception e)
            {
                Log.getLogger().warn("Error while saving:", e);
            }

            Log.getLogger()
              .warn("Entity:" + getName().toString() + " uuid:" + getUUID() + " id:" + getId() + " removed:" + isRemoved() + " colonyid:" + citizenColonyHandler.getColonyId()
                      + " entitydata colony id:" + getEntityData().get(DATA_COLONY_ID) + " hascolony:" + (citizenColonyHandler.getColony() != null) +
                      " registered:" + citizenColonyHandler.registered() + " world:" + level() + " saved data:" + tag);
        }

        if (handleInWallDamage(damageSource))
        {
            return false;
        }

        final Entity sourceEntity = damageSource.getEntity();
        if (!checkIfValidDamageSource(damageSource, damage))
        {
            return false;
        }

        if (getCitizenJobHandler().getColonyJob() != null && getCitizenJobHandler().getColonyJob().ignoresDamage(damageSource))
        {
            return false;
        }

        if (getCitizenColonyHandler().getColonyOrRegister() == null)
        {
            return super.hurt(damageSource, damage);
        }

        // Maxdmg cap so citizens need a certain amount of hits to die, so we get more gameplay value and less scaling issues.
        return handleDamagePerformed(damageSource, damage, sourceEntity);
    }

    ///////// -------------------- The Handlers -------------------- /////////

    private boolean handleInWallDamage(@NotNull final DamageSource damageSource)
    {
        if (damageSource.typeHolder().is(DamageTypes.IN_WALL))
        {
            TeleportHelper.teleportCitizen(this, level(), blockPosition());
            return true;
        }

        return damageSource.typeHolder().is(DamageTypes.IN_WALL) && citizenSleepHandler.isAsleep()
                 || Compatibility.isDynTreePresent() && damageSource.typeHolder().is(Compatibility.getDynamicTreeDamage()) || this.isInvulnerable();
    }

    /**
     * Check if the damage source is valid.
     *
     * @param source the damage source.
     * @param damage the dealt damage.
     * @return true if valid.
     */
    private boolean checkIfValidDamageSource(final DamageSource source, final float damage)
    {
        final Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof EntityCitizen)
        {
            if (((EntityCitizen) sourceEntity).citizenColonyHandler.getColonyId() == citizenColonyHandler.getColonyId())
            {
                return false;
            }

            final IColony attackerColony = ((EntityCitizen) sourceEntity).citizenColonyHandler.getColonyOrRegister();
            if (attackerColony != null && citizenColonyHandler.getColonyOrRegister() != null)
            {
                final IPermissions permission = attackerColony.getPermissions();
                citizenColonyHandler.getColonyOrRegister().getPermissions().addPlayer(permission.getOwner(), permission.getOwnerName(), permission.getRank(permission.HOSTILE_RANK_ID));
            }
        }

        if (sourceEntity instanceof Player)
        {
            if (sourceEntity instanceof ServerPlayer)
            {
                if (citizenColonyHandler.getColonyOrRegister().getRaiderManager().isRaided())
                {
                    return false;
                }

                if (damage > 1 && !getCitizenColonyHandler().getColonyOrRegister().getPermissions().hasPermission((Player) sourceEntity, Action.HURT_CITIZEN))
                {
                    return false;
                }

                if (getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard)
                {
                    return IGuardBuilding.checkIfGuardShouldTakeDamage(this, (Player) sourceEntity);
                }
            }
            else
            {
                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(getCitizenColonyHandler().getColonyId(), level().dimension());
                return damage <= 1 || colonyView == null || colonyView.getPermissions().hasPermission((Player) sourceEntity, Action.HURT_CITIZEN);
            }
        }
        return true;
    }

    @Override
    public float getSpeed()
    {
        return (float) Math.min(MAX_SPEED_FACTOR, super.getSpeed());
    }

    private boolean handleDamagePerformed(@NotNull final DamageSource damageSource, final float damage, final Entity sourceEntity)
    {
        float damageInc = Math.min(damage, (getMaxHealth() * 0.2f));

        //If we are in simulation, don't cap damage
        if (citizenJobHandler.getColonyJob() instanceof JobNetherWorker && citizenData != null && damageSource.typeHolder().is(DamageSourceKeys.NETHER))
        {
            damageInc = damage;
        }

        if (!level().isClientSide && !this.isInvisible())
        {
            performMoveAway(sourceEntity);
        }
        setLastHurtMob(damageSource.getEntity());

        if (!level().isClientSide)
        {
            if (citizenJobHandler.getColonyJob() instanceof AbstractJobGuard && citizenData != null)
            {
                if (citizenJobHandler.getColonyJob() instanceof JobKnight)
                {
                    if (citizenColonyHandler.getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(BLOCK_ATTACKS) > 0)
                    {
                        if (getRandom().nextDouble() < citizenColonyHandler.getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(BLOCK_ATTACKS))
                        {
                            return false;
                        }
                    }
                }

                if (citizenData.getWorkBuilding() instanceof AbstractBuildingGuards && ((AbstractBuildingGuards) citizenData.getWorkBuilding()).shallRetrieveOnLowHealth()
                      && getHealth() < ((int) getMaxHealth() * 0.2D))
                {
                    damageInc *= 1 - citizenColonyHandler.getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(FLEEING_DAMAGE);
                }
            }
        }

        boolean result = super.hurt(damageSource, damageInc);
        if (!result)
        {
            LivingShieldBlockEvent ev;
            if (damageInc > 0.0F && !this.damageContainers.empty() && (ev = CommonHooks.onDamageBlock(this, this.damageContainers.peek(), this.isDamageSourceBlocked(damageSource))).getBlocked())
            {
                final float blockedDamage = this.damageContainers.peek().getBlockedDamage();
                if (blockedDamage > 0)
                {
                    if (getItemInHand(getUsedItemHand()).getItem() instanceof ShieldItem)
                    {
                        if (getHealth() > blockedDamage * GUARD_BLOCK_DAMAGE)
                        {
                            final float blockDamage = CombatRules.getDamageAfterAbsorb(this,blockedDamage * GUARD_BLOCK_DAMAGE,
                              damageSource,
                              (float) this.getArmorValue(),
                              (float) this.getAttribute(Attributes.ARMOR_TOUGHNESS).getValue());
                            setHealth(getHealth() - Math.max(GUARD_BLOCK_DAMAGE, blockDamage));
                            result = true;
                        }
                        CitizenItemUtils.damageItemInHand(this, this.getUsedItemHand(), (int) (blockedDamage * GUARD_BLOCK_DAMAGE));
                    }
                }
            }
        }

        if (!result)
        {
            return false;
        }

        if (damageSource.getEntity() instanceof LivingEntity)
        {
            threatTable.addThreat((LivingEntity) damageSource.getEntity(), (int) damageInc);
        }

        if (damageSource.is(DamageTypeTags.IS_FIRE) || damageSource.is(DamageTypeTags.IS_LIGHTNING))
        {
            return true;
        }

        if (!level().isClientSide)
        {
            CitizenItemUtils.updateArmorDamage(this, damageInc);
            if (citizenData != null)
            {
                getCitizenData().getCitizenHappinessHandler().addModifier(new ExpirationBasedHappinessModifier(DAMAGE, 2.0, new StaticHappinessSupplier(0.0), 1));
            }
        }

        return true;
    }

    /**
     * Run away from an attacker
     *
     * @param attacker the attacking Entity
     */
    private void performMoveAway(@Nullable final Entity attacker)
    {
        // Environmental damage
        if (!(attacker instanceof LivingEntity) &&
              (!(getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard) || getCitizenJobHandler().getColonyJob().canAIBeInterrupted()))
        {
            if (moveAwayPath == null || !moveAwayPath.isInProgress())
            {
                moveAwayPath = this.getNavigation().moveAwayFromLivingEntity(this, 5, INITIAL_RUN_SPEED_AVOID);
            }
            return;
        }

        if ((getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard))
        {
            // 30 Blocks range
            callForHelp(attacker, 900);
            return;
        }

        citizenAI.addTransition(new AIOneTimeEventTarget<>(CitizenAIState.FLEE));
        callForHelp(attacker, MAX_GUARD_CALL_RANGE);
        if (moveAwayPath == null || !moveAwayPath.isInProgress())
        {
            moveAwayPath = this.getNavigation().moveAwayFromLivingEntity(attacker, 15, INITIAL_RUN_SPEED_AVOID);
        }
    }

    @Override
    public void callForHelp(final Entity attacker, final int guardHelpRange)
    {
        if (!(attacker instanceof LivingEntity) || callForHelpCooldown != 0)
        {
            return;
        }

        // Don't call for help when a guard gets woken up
        if (citizenJobHandler.getColonyJob() instanceof AbstractJobGuard && citizenJobHandler.getColonyJob(AbstractJobGuard.class).isAsleep())
        {
            return;
        }

        callForHelpCooldown = CALL_HELP_CD;

        List<AbstractEntityCitizen> possibleGuards = new ArrayList<>();

        for (final ICitizenData entry : getCitizenColonyHandler().getColonyOrRegister().getCitizenManager().getCitizens())
        {
            if (entry.getEntity().isPresent())
            {
                // Checking for guard nearby
                if (entry.getJob() instanceof AbstractJobGuard && entry.getId() != citizenData.getId()
                      && BlockPosUtil.getDistanceSquared(entry.getEntity().get().blockPosition(), blockPosition()) < guardHelpRange && entry.getJob().getWorkerAI() != null)
                {
                    final ThreatTable table = ((EntityCitizen) entry.getEntity().get()).getThreatTable();
                    table.addThreat((LivingEntity) attacker, 0);
                    if (((AbstractEntityAIGuard<?, ?>) entry.getJob().getWorkerAI()).canHelp(attacker.blockPosition()))
                    {
                        possibleGuards.add(entry.getEntity().get());
                    }
                }
            }
        }

        Collections.sort(possibleGuards, Comparator.comparingInt(guard -> (int) blockPosition().distSqr(guard.blockPosition())));

        for (int i = 0; i < possibleGuards.size() && i <= CALL_TO_HELP_AMOUNT; i++)
        {
            ((AbstractEntityAIGuard<?, ?>) possibleGuards.get(i).getCitizenData().getJob().getWorkerAI()).startHelpCitizen((LivingEntity) attacker);
        }
    }

    @Override
    protected void doPush(final Entity entity)
    {
        if (!citizenSleepHandler.isAsleep())
        {
            super.doPush(entity);
        }

        if (!level().isClientSide && getCitizenData() != null && entity instanceof AbstractEntityCitizen otherCitizen && otherCitizen.getCitizenData() != null)
        {
            getCitizenData().getCitizenDiseaseHandler().onCollission(otherCitizen.getCitizenData());
        }
    }

    @Override
    public void onPlayerCollide(final Player player)
    {
        super.onPlayerCollide(player);
        if (citizenJobHandler.getColonyJob() != null && citizenJobHandler.getColonyJob().getWorkerAI() instanceof AbstractEntityAIBasic && !citizenJobHandler.getColonyJob()
                                                                                                                                              .isGuard())
        {
            ((AbstractEntityAIBasic) citizenJobHandler.getColonyJob().getWorkerAI()).setDelay(TICKS_SECOND * 3);
        }
    }

    /**
     * Called when the mob's health reaches 0.
     *
     * @param damageSource the attacking entity.
     */
    @Override
    public void die(@NotNull final DamageSource damageSource)
    {
        if (citizenColonyHandler.getColonyOrRegister() != null && getCitizenData() != null)
        {
            citizenColonyHandler.getColonyOrRegister().getRaiderManager().onLostCitizen(getCitizenData());

            citizenExperienceHandler.dropExperience();
            this.remove(RemovalReason.KILLED);
            if (!(citizenJobHandler.getColonyJob() instanceof AbstractJobGuard))
            {
                citizenColonyHandler.getColonyOrRegister()
                  .getCitizenManager()
                  .injectModifier(new ExpirationBasedHappinessModifier(HappinessConstants.DEATH, 3.0, new StaticHappinessSupplier(0.0), 3));
            }
            triggerDeathAchievement(damageSource, citizenJobHandler.getColonyJob());

            if (!(citizenJobHandler.getColonyJob() instanceof AbstractJobGuard))
            {
                citizenColonyHandler.getColonyOrRegister().getCitizenManager().updateCitizenMourn(citizenData, true);
            }

            getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().increment(DEATH, getCitizenColonyHandler().getColonyOrRegister().getDay());

            boolean graveSpawned = false;
            if (!isInvisible())
            {
                if (citizenColonyHandler.getColonyOrRegister().isCoordInColony(level(), blockPosition()))
                {
                    graveSpawned = getCitizenColonyHandler().getColonyOrRegister().getGraveManager().createCitizenGrave(level(), blockPosition(), citizenData);
                }
                else
                {
                    InventoryUtils.dropItemHandler(citizenData.getInventory(), level(), (int) getX(), (int) getY(), (int) getZ());
                }
            }

            if (getCitizenColonyHandler().getColonyOrRegister() != null && getCitizenData() != null)
            {
                MessageUtils.format(getCombatTracker().getDeathMessage())
                  .append(Component.literal("! "))
                  .append(Component.translatable(TranslationConstants.COLONIST_GRAVE_LOCATION, Math.round(getX()), Math.round(getY()), Math.round(getZ())))
                  .append(!(citizenJobHandler.getColonyJob() instanceof AbstractJobGuard<?>)
                            ? Component.translatable(COM_MINECOLONIES_COREMOD_MOURN, getCitizenData().getName())
                            : Component.empty())
                  .append(graveSpawned ? Component.translatable(WARNING_GRAVE_SPAWNED) : Component.empty())
                  .withPriority(MessagePriority.DANGER)
                  .sendTo(getCitizenColonyHandler().getColonyOrRegister()).forManagers();
            }

            if (citizenData.getJob() != null)
            {
                citizenData.getJob().onRemoval();
            }
            citizenColonyHandler.getColonyOrRegister().getCitizenManager().removeCivilian(getCitizenData());

            final String deathCause =
              Component.literal(damageSource.getLocalizedDeathMessage(this).getString()).getString().replaceFirst(this.getDisplayName().getString(), "Citizen");
            citizenColonyHandler.getColonyOrRegister().getEventDescriptionManager().addEventDescription(new CitizenDiedEvent(blockPosition(), citizenData.getName(), deathCause));

            try
            {
                NeoForge.EVENT_BUS.post(new CitizenRemovedEvent(citizenData, damageSource));
            }
            catch (final Exception e)
            {
                Log.getLogger().error("Error during CitizenRemovedEvent", e);
            }
        }
        super.die(damageSource);
    }

    /**
     * Trigger the corresponding death achievement.
     *
     * @param source The damage source.
     * @param job    The job of the citizen.
     */
    private void triggerDeathAchievement(final DamageSource source, final IJob<?> job)
    {
        // If the job is null, then we can trigger jobless citizen achievement
        if (job != null)
        {
            job.triggerDeathAchievement(source, this);
        }
    }

    @Override
    protected void dropEquipment()
    {
        //Drop actual inventory
        for (int i = 0; i < getInventoryCitizen().getSlots(); i++)
        {
            final ItemStack itemstack = getCitizenData().getInventory().getStackInSlot(i);
            if (ItemStackUtils.getSize(itemstack) > 0)
            {
                CitizenItemUtils.entityDropItem(this, itemstack);
            }
        }
    }

    @NotNull
    @Override
    public Iterable<ItemStack> getAllSlots()
    {
        if (citizenData != null)
        {
            return citizenData.getInventory().getIterableArmorAndHandInv();
        }
        else if (citizenDataView != null)
        {
            return citizenDataView.getInventory().getIterableArmorAndHandInv();
        }
        return super.getAllSlots();
    }

    @NotNull
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slotType)
    {
        switch (slotType.getType())
        {
            case HAND:
                return super.getItemBySlot(slotType);
            case HUMANOID_ARMOR:
                if (citizenData != null)
                {
                    return citizenData.getInventory().getArmorInSlot(slotType);
                }
                else if (citizenDataView != null)
                {
                    return citizenDataView.getInventory().getArmorInSlot(slotType);
                }
                return super.getItemBySlot(slotType);
            default:
                return ItemStack.EMPTY;
        }
    }

    @Override
    public int getArmorValue()
    {
        if (citizenJobHandler.getColonyJob() instanceof JobKnight)
        {
            return (int) (super.getArmorValue() * (1 + citizenColonyHandler.getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(MELEE_ARMOR)));
        }
        else if (citizenJobHandler.getColonyJob() instanceof JobRanger)
        {
            return (int) (super.getArmorValue() * (1 + citizenColonyHandler.getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ARCHER_ARMOR)));
        }
        return super.getArmorValue();
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (obj instanceof EntityCitizen)
        {
            final EntityCitizen citizen = (EntityCitizen) obj;
            return citizen.citizenColonyHandler.getColonyId() == this.citizenColonyHandler.getColonyId() && citizen.citizenId == this.citizenId;
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        if (citizenColonyHandler == null)
        {
            return super.hashCode();
        }
        return Objects.hash(citizenId, citizenColonyHandler.getColonyId());
    }

    @Override
    public void setRemoved(final RemovalReason reason)
    {
        citizenColonyHandler.onCitizenRemoved();
        super.setRemoved(reason);
    }

    @Override
    public void setCustomName(@Nullable final Component name)
    {
        if (citizenData != null && citizenColonyHandler.getColonyOrRegister() != null && name != null)
        {
            citizenData.setName(name.getString());
            super.setCustomName(name);
        }
    }

    @Override
    public void spawnAnim()
    {
        super.spawnAnim();
    }

    @Override
    protected void pickUpItem(final ItemEntity itemEntity)
    {
        /*
         * Intentionally left empty.
         */
    }

    @Override
    public boolean requiresCustomPersistence()
    {
        return true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(final int id, @NotNull final Inventory inv, @NotNull final Player player)
    {
        return new ContainerCitizenInventory(id, inv, citizenColonyHandler.getColonyId(), citizenId);
    }

    @Override
    public void setTexture()
    {
        super.setTexture();
    }

    @Override
    public void queueSound(@NotNull final SoundEvent soundEvent, final BlockPos pos, final int length, final int repetitions)
    {
        if (soundEvent == null || !BuiltInRegistries.SOUND_EVENT.containsKey(soundEvent.getLocation()))
        {
            return;
        }

        new PlaySoundForCitizenMessage(this.getId(), soundEvent, this.getSoundSource(), pos, level(), length, repetitions).sendToTargetPoint((ServerLevel) level(), null, pos.getX(), pos.getY(), pos.getZ(), BLOCK_BREAK_SOUND_RANGE);
    }

    @Override
    public void queueSound(@NotNull final SoundEvent soundEvent, final BlockPos pos, final int length, final int repetitions, final float volume, final float pitch)
    {
        if (soundEvent == null || !BuiltInRegistries.SOUND_EVENT.containsKey(soundEvent.getLocation()))
        {
            return;
        }

        new PlaySoundForCitizenMessage(this.getId(), soundEvent, this.getSoundSource(), pos, level(), volume, pitch, length, repetitions).sendToTargetPoint(
          (ServerLevel) level(), null, pos.getX(), pos.getY(), pos.getZ(), BLOCK_BREAK_SOUND_RANGE);
    }

    /**
     * Whether this entity is active and loaded
     *
     * @return
     */
    public boolean isActive()
    {
        return level().isClientSide ? entityStateController.getState() == EntityState.ACTIVE_CLIENT : entityStateController.getState() == EntityState.ACTIVE_SERVER;
    }

    @Override
    public ThreatTable getThreatTable()
    {
        return threatTable;
    }

    /**
     * Get the AI controlling the citizens behaviour
     *
     * @return
     */
    public ITickRateStateMachine<IState> getCitizenAI()
    {
        return citizenAI;
    }

    @Override
    public boolean isSuppressingBounce()
    {
        if (citizenSleepHandler.isAsleep())
        {
            return true;
        }
        return super.isSuppressingBounce();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> dataAccessor)
    {
        super.onSyncedDataUpdated(dataAccessor);
        if (citizenColonyHandler != null)
        {
            citizenColonyHandler.onSyncDataUpdate(dataAccessor);
        }

        if (level().isClientSide && dataAccessor == DATA_STYLE)
        {
            if (!TextureReloadListener.TEXTURE_PACKS.contains(getEntityData().get(DATA_STYLE)))
            {
                getEntityData().set(DATA_STYLE, TextureReloadListener.TEXTURE_PACKS.get(0));
            }
        }
    }

    @Override
    public boolean isCurrentlyGlowing()
    {
        return isGlowing || super.isCurrentlyGlowing();
    }

    public void setGlowing(final boolean isGlowing)
    {
        this.isGlowing = isGlowing;
    }

    @Override
    public CombatTracker getCombatTracker()
    {
        return combatTracker;
    }

    /**
     * Sets the max air
     *
     * @param maxAir
     */
    public void setMaxAir(final int maxAir)
    {
        this.maxAir = maxAir;
    }

    @Override
    public int getTeamId()
    {
        return citizenColonyHandler.getColonyId();
    }
}
