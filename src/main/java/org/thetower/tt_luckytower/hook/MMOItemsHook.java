package org.thetower.tt_luckytower.hook;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.inventory.ItemStack;
import org.thetower.tt_luckytower.LuckyTowerPlugin;

public class MMOItemsHook {

    private final LuckyTowerPlugin plugin;
    private boolean enabled = false;

    public MMOItemsHook(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (plugin.getServer().getPluginManager().getPlugin("MMOItems") != null) {
            enabled = true;
            plugin.getLogger().info("MMOItems 연동 완료.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * MMOItem을 ItemStack으로 변환하여 반환.
     * @param typeId MMOItem 타입 (예: "SWORD")
     * @param itemId MMOItem ID (예: "LEGENDARY_SWORD")
     * @return ItemStack 또는 null
     */
    public ItemStack getItem(String typeId, String itemId) {
        if (!enabled) return null;
        try {
            Type type = MMOItems.plugin.getTypes().get(typeId);
            if (type == null) return null;
            MMOItem mmoItem = MMOItems.plugin.getItems().getMMOItem(type, itemId);
            if (mmoItem == null) return null;
            ItemStackBuilder builder = mmoItem.newBuilder();
            return builder.build();
        } catch (Exception e) {
            plugin.getLogger().warning("MMOItem 생성 실패: " + typeId + "/" + itemId + " - " + e.getMessage());
            return null;
        }
    }
}
