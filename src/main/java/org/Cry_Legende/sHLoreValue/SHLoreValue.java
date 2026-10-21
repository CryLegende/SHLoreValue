package org.Cry_Legende.sHLoreValue;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.io.File;
import java.util.*;

public class SHLoreValue extends JavaPlugin {
    private String loreTextFormat;
    private String ssb2ValuesPath;
    // Map optimisée : contient directement la ligne de lore finale pour chaque matériau.
    private final Map<Material, String> cachedLoreLines = new HashMap<>();
    private ProtocolManager protocolManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues(); // Charge la config et pré-calcule les lores

        protocolManager = ProtocolLibrary.getProtocolManager();
        setupPacketListeners();

        getLogger().info("SHLoreValue (Optimized) enabled with ProtocolLib integration!");
    }

    /**
     * Charge les valeurs depuis le fichier de SuperiorSkyblock2 et les pré-calcule.
     */
    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        loreTextFormat = config.getString("lore-text", "&8Valeur: %value%Lv");
        ssb2ValuesPath = config.getString("superior-skyblock-values-path", "plugins/SuperiorSkyblock2/block-values/levels.yml");

        // Nettoyage avant rechargement
        cachedLoreLines.clear();

        File serverRootFolder = this.getDataFolder().getParentFile().getParentFile();
        File ssbValuesFile = new File(serverRootFolder, ssb2ValuesPath);

        if (!ssbValuesFile.exists()) {
            getLogger().severe("Fichier de valeurs de SSB2 non trouvé : " + ssbValuesFile.getAbsolutePath());
            return;
        }

        FileConfiguration ssbConfig = YamlConfiguration.loadConfiguration(ssbValuesFile);
        int loadedCount = 0;

        for (String key : ssbConfig.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());

                if (ssbConfig.isDouble(key) || ssbConfig.isInt(key)) {
                    double value = ssbConfig.getDouble(key, 0.0);

                    if (value > 0) {
                        // On formate et pré-calcule la ligne de lore directement
                        String formattedValue = (value % 1 == 0) ? String.valueOf((int) value) : String.valueOf(value);
                        String precomputedLine = ChatColor.translateAlternateColorCodes('&', loreTextFormat.replace("%value%", formattedValue));
                        cachedLoreLines.put(material, precomputedLine);
                        loadedCount++;
                    }
                }
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Matériau '" + key + "' ignoré (invalide).");
            }
        }

        getLogger().info("Chargé " + loadedCount + " valeurs de blocs depuis le fichier de SuperiorSkyblock2.");
    }

    /**
     * Met en place les listeners de paquets pour modifier les items envoyés au client.
     * La partie qui gérait le retrait du lore a été supprimée.
     */
    private void setupPacketListeners() {
        protocolManager.addPacketListener(new PacketAdapter(this,
            ListenerPriority.NORMAL,
            PacketType.Play.Server.SET_SLOT,
            PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                    modifyItemPacket(event);
                } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                    modifyWindowItemsPacket(event);
                }
            }
        });
    }

    private void modifyItemPacket(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        ItemStack item = packet.getItemModifier().read(0);

        // On ne traite que les items qui ont une valeur dans notre cache
        if (item != null && cachedLoreLines.containsKey(item.getType())) {
            ItemStack clone = item.clone(); // On clone pour ne pas modifier l'item serveur
            if (addClientSideLore(clone)) { // Si l'ajout de lore a réussi...
                packet.getItemModifier().write(0, clone); // ...on envoie le clone modifié au client.
            }
        }
    }

    private void modifyWindowItemsPacket(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        // Le paquet WINDOW_ITEMS utilise une List, contrairement à SET_SLOT.
        // ProtocolLib gère cette différence via les modifiers (getItemListModifier vs getItemModifier).
        List<ItemStack> items = packet.getItemListModifier().read(0);
        List<ItemStack> modifiedItems = new ArrayList<>(items.size());
        boolean changed = false;

        for (ItemStack item : items) {
            if (item != null && cachedLoreLines.containsKey(item.getType())) {
                ItemStack clone = item.clone();
                if (addClientSideLore(clone)) {
                    modifiedItems.add(clone);
                    changed = true;
                } else {
                    modifiedItems.add(item); // Ajoute l'original si pas de modif
                }
            } else {
                modifiedItems.add(item);
            }
        }

        if (changed) {
            packet.getItemListModifier().write(0, modifiedItems);
        }
    }

    /**
     * LOGIQUE PRINCIPALE SIMPLIFIÉE
     * Ajoute le lore à un item SEULEMENT s'il n'a pas déjà de lore.
     *
     * @param item L'item (un clone) à modifier.
     * @return true si l'item a été modifié, false sinon.
     */
    private boolean addClientSideLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();

        // RÈGLE SIMPLE : Si l'item a DÉJÀ un lore (n'importe lequel), on ne touche à rien.
        if (meta != null && meta.hasLore()) {
            return false;
        }

        // L'item n'a pas de lore, on peut ajouter le nôtre.
        String lineToAdd = this.cachedLoreLines.get(item.getType());

        // Si meta est null (cas d'un item de base sans aucune donnée), on le crée.
        if (meta == null) {
            meta = getServer().getItemFactory().getItemMeta(item.getType());
            if (meta == null) return false; // Ne devrait pas arriver pour les items normaux
        }

        List<String> newLoreList = new ArrayList<>(1);
        newLoreList.add(lineToAdd);

        meta.setLore(newLoreList);
        item.setItemMeta(meta);

        return true; // L'item a été modifié avec succès.
    }
}
