package com.minecolonies.coremod.network.messages.client.colony;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderView;
import com.minecolonies.api.network.IMessage;
import com.minecolonies.coremod.colony.Colony;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Add or Update a ColonyView on the client.
 */
public class ColonyViewWorkOrderMessage implements IMessage
{
    private final int              colonyId;
    private final ResourceLocation dimension;
    private final PacketBuffer     workOrderBuffer;

    /**
     * Empty constructor used when registering the
     */
    public ColonyViewWorkOrderMessage(final PacketBuffer buf)
    {
        final PacketBuffer newbuf = new PacketBuffer(buf.retain());
        this.colonyId = newbuf.readInt();
        this.dimension = new ResourceLocation(newbuf.readString(32767));
        this.workOrderBuffer = newbuf;
    }

    /**
     * Updates a {@link WorkOrderView} of the workOrders.
     *
     * @param colony        colony of the workOrder.
     * @param workOrderList list of workorders to send to the client
     */
    public ColonyViewWorkOrderMessage(@NotNull final Colony colony, @NotNull final List<IWorkOrder> workOrderList)
    {
        this.colonyId = colony.getID();
        this.workOrderBuffer = new PacketBuffer(Unpooled.buffer());
        this.dimension = colony.getDimension();

        workOrderBuffer.writeInt(workOrderList.size());
        for (final IWorkOrder workOrder : workOrderList)
        {
            workOrder.serializeViewNetworkData(workOrderBuffer);
        }
    }

    @Override
    public void toBytes(@NotNull final PacketBuffer buf)
    {
        buf.writeInt(colonyId);
        buf.writeString(dimension.toString());
        buf.writeBytes(workOrderBuffer);
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
        IColonyManager.getInstance().handleColonyViewWorkOrderMessage(colonyId, workOrderBuffer, dimension);
        workOrderBuffer.release();
    }
}


