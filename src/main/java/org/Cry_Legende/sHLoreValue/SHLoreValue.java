package org.Cry_Legende.sHLoreValue;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SHLoreValue extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_RELOAD = "shlorevalue.admin.reload";
    private static final String MESSAGES_FILE = "messages.yml";
    private static final String DEFAULT_LORE = "&8%value% Niv. d'île";
    private static final String DEFAULT_SSB2_PATH = "plugins/SuperiorSkyblock2/block-values/levels.yml";

    // Table optimisée : contient directement la ligne de lore finale pour chaque matériau. Elle n'est jamais modifiée
    // après sa publication : un reload en construit une nouvelle et l'échange d'un coup, les threads de paquets
    // lisent toujours une table complète.
    private volatile Map<Material, String> cachedLoreLines = Map.of();
    private volatile YamlConfiguration messages = new YamlConfiguration();
    private ProtocolManager protocolManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), MESSAGES_FILE).exists()) {
            saveResource(MESSAGES_FILE, false);
        }
        messages = withDefaults(YamlConfiguration.loadConfiguration(new File(getDataFolder(), MESSAGES_FILE)));
        loadConfigValues(); // Charge la config et pré-calcule les lores

        protocolManager = ProtocolLibrary.getProtocolManager();
        setupPacketListeners();

        PluginCommand command = getCommand("shlorevalue");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        getLogger().info("SHLoreValue (Optimized) enabled with ProtocolLib integration!");
    }

    /**
     * Charge les valeurs depuis le fichier de SuperiorSkyblock2 et les pré-calcule.
     */
    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        String loreTextFormat = config.getString("lore-text", DEFAULT_LORE);
        String ssb2ValuesPath = config.getString("superior-skyblock-values-path", DEFAULT_SSB2_PATH);

        File ssbValuesFile = ssbFile(ssb2ValuesPath);
        if (!ssbValuesFile.exists()) {
            getLogger().severe("Fichier de valeurs de SSB2 non trouvé : " + ssbValuesFile.getAbsolutePath());
            cachedLoreLines = Map.of();
            return;
        }

        FileConfiguration ssbConfig = YamlConfiguration.loadConfiguration(ssbValuesFile);
        cachedLoreLines = buildTable(loreTextFormat, ssbConfig);
        getLogger().info("Chargé " + cachedLoreLines.size() + " valeurs de blocs depuis le fichier de SuperiorSkyblock2.");
    }

    private File ssbFile(String path) {
        File serverRootFolder = this.getDataFolder().getParentFile().getParentFile();
        return new File(serverRootFolder, path);
    }

    private Map<Material, String> buildTable(String loreTextFormat, FileConfiguration ssbConfig) {
        Map<Material, String> table = new EnumMap<>(Material.class);
        for (String key : ssbConfig.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());

                if (ssbConfig.isDouble(key) || ssbConfig.isInt(key)) {
                    double value = ssbConfig.getDouble(key, 0.0);

                    if (value > 0) {
                        // On formate et pré-calcule la ligne de lore directement
                        String formattedValue = (value % 1 == 0) ? String.valueOf((int) value) : String.valueOf(value);
                        table.put(material, ChatColor.translateAlternateColorCodes('&', loreTextFormat.replace("%value%", formattedValue)));
                    }
                }
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Matériau '" + key + "' ignoré (invalide).");
            }
        }
        return Collections.unmodifiableMap(table);
    }

    /**
     * Relit config.yml, messages.yml et le fichier de valeurs de SuperiorSkyblock2, puis remplace la table de lores.
     * Si l'un des trois est absent ou invalide, rien n'est appliqué : l'ancienne table reste en place et le message
     * donne le fichier et l'erreur.
     */
    private void reloadAll(CommandSender sender) {
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration newConfig = new YamlConfiguration();
        YamlConfiguration newMessages = new YamlConfiguration();
        YamlConfiguration ssbConfig = new YamlConfiguration();
        String current = "config.yml";
        try {
            newConfig.load(configFile);
            current = MESSAGES_FILE;
            newMessages.load(new File(getDataFolder(), MESSAGES_FILE));
            // Le chemin de SSB2 est celui du nouveau config.yml, avec le défaut du jar si la clé manque
            String path = newConfig.getString("superior-skyblock-values-path", DEFAULT_SSB2_PATH);
            File ssbValuesFile = ssbFile(path);
            current = path;
            if (!ssbValuesFile.isFile()) {
                throw new IOException("fichier introuvable");
            }
            ssbConfig.load(ssbValuesFile);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("Rechargement annulé, " + current + " : " + e.getMessage());
            sender.sendMessage(message("reload-failed").replace("%file%", current).replace("%error%", firstLine(e)));
            return;
        }

        reloadConfig();
        messages = withDefaults(newMessages);
        Map<Material, String> table = buildTable(newConfig.getString("lore-text", DEFAULT_LORE), ssbConfig);
        cachedLoreLines = table;
        getLogger().info("Rechargé : " + table.size() + " valeurs de blocs (" + sender.getName() + ").");
        sender.sendMessage(message("reloaded").replace("%count%", String.valueOf(table.size())));
    }

    private static String firstLine(Exception e) {
        String text = String.valueOf(e.getMessage()).strip();
        int end = text.indexOf('\n');
        String line = end < 0 ? text : text.substring(0, end).strip();
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    /** Les clés absentes du messages.yml du serveur reprennent le texte du jar. */
    private YamlConfiguration withDefaults(YamlConfiguration loaded) {
        try (Reader defaults = new InputStreamReader(getResource(MESSAGES_FILE), StandardCharsets.UTF_8)) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        } catch (IOException | RuntimeException e) {
            getLogger().warning("messages.yml du jar illisible : " + e.getMessage());
        }
        return loaded;
    }

    private String message(String key) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(key, key));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean canReload = sender.hasPermission(PERMISSION_RELOAD);
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && canReload) {
            reloadAll(sender);
            return true;
        }
        if (!canReload) {
            sender.sendMessage(message("no-permission"));
            return true;
        }
        sender.sendMessage(message("usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(PERMISSION_RELOAD) && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
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
        Map<Material, String> table = cachedLoreLines;
        PacketContainer packet = event.getPacket();
        ItemStack item = packet.getItemModifier().read(0);

        // On ne traite que les items qui ont une valeur dans notre cache
        if (item != null && table.containsKey(item.getType())) {
            ItemStack clone = item.clone(); // On clone pour ne pas modifier l'item serveur
            if (addClientSideLore(clone, table)) { // Si l'ajout de lore a réussi...
                packet.getItemModifier().write(0, clone); // ...on envoie le clone modifié au client.
            }
        }
    }

    private void modifyWindowItemsPacket(PacketEvent event) {
        Map<Material, String> table = cachedLoreLines;
        PacketContainer packet = event.getPacket();
        // Le paquet WINDOW_ITEMS utilise une List, contrairement à SET_SLOT.
        // ProtocolLib gère cette différence via les modifiers (getItemListModifier vs getItemModifier).
        List<ItemStack> items = packet.getItemListModifier().read(0);
        List<ItemStack> modifiedItems = new ArrayList<>(items.size());
        boolean changed = false;

        for (ItemStack item : items) {
            if (item != null && table.containsKey(item.getType())) {
                ItemStack clone = item.clone();
                if (addClientSideLore(clone, table)) {
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
     * @param table La table de lores en vigueur pour ce paquet.
     * @return true si l'item a été modifié, false sinon.
     */
    private boolean addClientSideLore(ItemStack item, Map<Material, String> table) {
        ItemMeta meta = item.getItemMeta();

        // RÈGLE SIMPLE : Si l'item a DÉJÀ un lore (n'importe lequel), on ne touche à rien.
        if (meta != null && meta.hasLore()) {
            return false;
        }

        // L'item n'a pas de lore, on peut ajouter le nôtre.
        String lineToAdd = table.get(item.getType());

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
