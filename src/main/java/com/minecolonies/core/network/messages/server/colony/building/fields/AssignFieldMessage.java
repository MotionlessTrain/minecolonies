package com.minecolonies.core.network.messages.server.colony.building.fields;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildingextensions.registry.BuildingExtensionDataManager;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Message which handles the assignment of fields to farmers.
 */
public class AssignFieldMessage extends AbstractBuildingServerMessage<IBuilding>
{
    /**
     * The modules ID
     */
    private int       moduleID = 0;

    /**
     * The field to (un)assign.
     */
    private FriendlyByteBuf fieldData;

    /**
     * Whether to assign or un-assign this field.
     */
    private boolean assign;

    /**
     * Empty standard constructor.
     */
    public AssignFieldMessage()
    {
        super();
    }

    /**
     * Creates the message to assign a field.
     *
     * @param assign   assign if true, free if false.
     * @param field    the field.
     * @param building the building we're executing on.
     */
    public AssignFieldMessage(final IBuildingView building, final IBuildingExtension field, final boolean assign, final int moduleID)
    {
        super(building);
        this.assign = assign;
        this.fieldData = BuildingExtensionDataManager.extensionToBuffer(field);
        this.moduleID = moduleID;
    }

    @Override
    public void toBytesOverride(@NotNull final FriendlyByteBuf buf)
    {
        fieldData.resetReaderIndex();
        buf.writeBoolean(assign);
        buf.writeInt(moduleID);
        buf.writeBytes(fieldData);
    }

    @Override
    public void fromBytesOverride(@NotNull final FriendlyByteBuf buf)
    {
        assign = buf.readBoolean();
        moduleID = buf.readInt();
        fieldData = new FriendlyByteBuf(Unpooled.buffer(buf.readableBytes()));
        buf.readBytes(fieldData, buf.readableBytes());
    }

    @Override
    public void onExecute(
      final NetworkEvent.Context ctxIn, final boolean isLogicalServer, final IColony colony, final IBuilding building)
    {
        final IBuildingExtension parsedField = BuildingExtensionDataManager.bufferToExtension(fieldData);
        colony.getBuildingManager().getBuildingExtension(otherField -> otherField.equals(parsedField)).ifPresent(field -> {

            if (building.getModule(moduleID) instanceof BuildingExtensionsModule fieldsModule)
            {
                if (assign)
                {
                    fieldsModule.assignExtension(field);
                }
                else
                {
                    fieldsModule.freeExtension(field);
                }
            }
        });
    }
}

