package com.minecolonies.coremod.entity.citizen.citizenhandlers;

import com.ldtteam.structurize.util.LanguageHandler;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenChatHandler;
import com.minecolonies.api.util.CompatibilityUtils;
import com.minecolonies.coremod.util.ServerUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The citizen chat handler which handles all possible notifications (blocking or not).
 */
public class CitizenChatHandler implements ICitizenChatHandler
{
    /**
     * The citizen assigned to this manager.
     */
    private final AbstractEntityCitizen citizen;

    /**
     * Constructor for the experience handler.
     *
     * @param citizen the citizen owning the handler.
     */
    public CitizenChatHandler(final AbstractEntityCitizen citizen)
    {
        this.citizen = citizen;
    }

    /**
     * Notify about death of citizen.
     *
     * @param damageSource the damage source.
     */
    @Override
    public void notifyDeath(final DamageSource damageSource)
    {
        if (citizen.getCitizenColonyHandler().getColony() != null && citizen.getCitizenData() != null)
        {
            final IJob<?> job = citizen.getCitizenJobHandler().getColonyJob();
            if (job != null)
            {
                final ITextComponent component = new TranslationTextComponent(
                  "block.blockhuttownhall.messageworkerdead",
                  new TranslationTextComponent(job.getJobRegistryEntry().getTranslationKey()),
                  citizen.getCitizenData().getName(),
                  (int) citizen.getX(), (int) citizen.getY(),
                  (int) citizen.getZ(), new TranslationTextComponent(damageSource.msgId));
                LanguageHandler.sendPlayersMessage(citizen.getCitizenColonyHandler().getColony().getImportantMessageEntityPlayers(), "", component);
            }
            else
            {
                LanguageHandler.sendPlayersMessage(
                  citizen.getCitizenColonyHandler().getColony().getImportantMessageEntityPlayers(), "",
                  new TranslationTextComponent("block.blockhuttownhall.messagecolonistdead",
                    citizen.getCitizenData().getName(), (int) citizen.getX(), (int) citizen.getY(),
                    (int) citizen.getZ(), new TranslationTextComponent(damageSource.msgId)));
            }
        }
    }

    @Override
    public void sendLocalizedChat(final String keyIn, final Object... msg)
    {
        final String key = keyIn.toLowerCase(Locale.US);
        if (msg == null)
        {
            return;
        }

        final TranslationTextComponent requiredItem;

        if (msg.length == 0)
        {
            requiredItem = new TranslationTextComponent(key);
        }
        else
        {
            requiredItem = new TranslationTextComponent(key, msg);
        }

        final ITextComponent citizenDescription = new StringTextComponent(citizen.getCustomName().getString());
        if (citizen.getCitizenColonyHandler().getColony() != null)
        {
            final StringTextComponent colonyDescription = new StringTextComponent(" at " + citizen.getCitizenColonyHandler().getColony().getName() + ": ");
            final List<PlayerEntity> players = new ArrayList<>(citizen.getCitizenColonyHandler().getColony().getMessagePlayerEntities());
            final PlayerEntity owner =
              ServerUtils.getPlayerFromUUID(CompatibilityUtils.getWorldFromCitizen(citizen), citizen.getCitizenColonyHandler().getColony().getPermissions().getOwner());

            if (owner != null)
            {
                players.remove(owner);
                LanguageHandler.sendPlayerMessage(owner,
                  citizen.getCitizenJobHandler().getColonyJob() == null ? "" : citizen.getCitizenJobHandler().getColonyJob().getJobRegistryEntry().getTranslationKey(), new StringTextComponent(" "), citizenDescription, requiredItem);
            }

            LanguageHandler.sendPlayersMessage(players,
              citizen.getCitizenJobHandler().getColonyJob() == null ? "" : citizen.getCitizenJobHandler().getColonyJob().getJobRegistryEntry().getTranslationKey(), new StringTextComponent(" "),
              citizenDescription,
              colonyDescription,
              requiredItem);
        }
    }
}
