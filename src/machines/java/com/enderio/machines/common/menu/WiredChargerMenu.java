package com.enderio.machines.common.menu;

import com.enderio.machines.common.blockentity.WiredChargerBlockEntity;
import com.enderio.machines.common.init.MachineMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;

public class WiredChargerMenu extends MachineMenu<WiredChargerBlockEntity>{

    public WiredChargerMenu(WiredChargerBlockEntity blockEntity, Inventory inventory, int pContainerId) {
        super(blockEntity, inventory, MachineMenus.WIRED_CHARGER.get(), pContainerId);

        addSlot(new MachineSlot(blockEntity.getInventory(), blockEntity.getCapacitorSlot(), 33, 60));
        addSlot(new MachineSlot(blockEntity.getInventory(), WiredChargerBlockEntity.ITEM_TO_CHARGE, 75, 28));
        addSlot(new MachineSlot(blockEntity.getInventory(), WiredChargerBlockEntity.ITEM_CHARGED, 126, 28));

        addInventorySlots(29,84);
    }

    public static WiredChargerMenu factory(@Nullable MenuType<WiredChargerMenu> pMenuType, int pContainerId, Inventory inventory, FriendlyByteBuf buf) {
        BlockEntity entity = inventory.player.level().getBlockEntity(buf.readBlockPos());

        if (entity instanceof WiredChargerBlockEntity castBlockEntity)
            return new WiredChargerMenu(castBlockEntity, inventory, pContainerId);
        LogManager.getLogger().warn("couldn't find BlockEntity");

        return new WiredChargerMenu(null, inventory, pContainerId);
    }
}
