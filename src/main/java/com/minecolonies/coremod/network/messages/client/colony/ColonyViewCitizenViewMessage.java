package com.minecolonies.coremod.network.messages.client.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.network.IMessage;
import com.minecolonies.coremod.colony.Colony;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Add or Update a ColonyView on the client.
 */
public class ColonyViewCitizenViewMessage implements IMessage
{
    private final int          colonyId;
    private final int          citizenId;
    private final PacketBuffer citizenBuffer;

    /**
     * The dimension the citizen is in.
     */
    private final ResourceLocation dimension;

    /**
     * Empty constructor used when registering the
     */
    public ColonyViewCitizenViewMessage(final PacketBuffer buf)
    {
        this.colonyId = buf.readInt();
        this.citizenId = buf.readInt();
        this.dimension = new ResourceLocation(buf.readString(32767));
        this.citizenBuffer = new PacketBuffer(buf.retain());
    }

    /**
     * Updates a {@link com.minecolonies.coremod.colony.CitizenDataView} of the citizens.
     *
     * @param colony  Colony of the citizen
     * @param citizen Citizen data of the citizen to update view
     */
    public ColonyViewCitizenViewMessage(@NotNull final Colony colony, @NotNull final ICitizenData citizen)
    {
        this.colonyId = colony.getID();
        this.citizenId = citizen.getId();
        this.citizenBuffer = new PacketBuffer(Unpooled.buffer());
        this.dimension = citizen.getColony().getDimension();
        citizen.serializeViewNetworkData(citizenBuffer);
    }

    @Override
    public void toBytes(@NotNull final PacketBuffer buf)
    {
        buf.writeInt(colonyId);
        buf.writeInt(citizenId);
        buf.writeString(dimension.toString());
        buf.writeBytes(citizenBuffer);
    }

    @Nullable
    @Override
    public LogicalSide getExecutionSide()
    {
        return LogicalSide.CLIENT;
    }

    @Override
    public void onExecute(final NetworkEvent.Context ctxIn, final boolean isLogicalServer)
    {
        IColonyManager.getInstance().handleColonyViewCitizensMessage(colonyId, citizenId, citizenBuffer, dimension);
        citizenBuffer.release();
    }
}
