package plugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ReplayModRestriction extends JavaPlugin implements Listener
{
    // ---------------------  Restriction Strings ---------------------  //
    /**
     * Show suffocation screen when in solid block
     */
    public static final String NO_XRAY = "no_xray";

    /**
     * Disable the ability to fly through blocks
     */
    public static final String NO_NOCLIP = "no_noclip";

    /**
     * The camera may not leave the first person view (this should be used with no_xray in case the player clips into blocks)
     */
    public static final String ONLY_FIRST_PERSON = "only_first_person";

    /**
     * Same as {@link #ONLY_FIRST_PERSON} except that changing the view to another player is also forbidden
     */
    public static final String ONLY_RECORDING_PLAYER = "only_recording_player";

    /**
     * Hides the coordinates from the F3 screen
     */
    public static final String HIDE_COORDINATE = "hide_coordinate";

    // ---------------------  Restriction Channel ---------------------  //

    /**
     * The channel used by replay mod restriction protocol.
     */
    public static final String RESTRICTION_CHANNEL = "Replay|Restrict";

    /**
     * Object reference to be used by API calls
     */
    private static ReplayModRestriction plugin;

    // ---------------------  API Methods ---------------------  //

    /**
     * Sends restriction strings to the player using {@link ReplayModRestriction) as plugin
     * @param player The player that will receive the restrictions
     * @param value The value that will be set on all restrictions
     * @param restrictions The restrictions that will be changed, restrictions that aren't specified won't be changed
     */
    public static void sendRestrictions(Player player, boolean value, String... restrictions)
    {
        Map<String, Boolean> map = new HashMap<String, Boolean>(restrictions.length);
        for(String restriction: restrictions)
            map.put(restriction, value);
        sendRestrictions(plugin, player, map);
    }

    /**
     * Sends restriction strings to the player using {@link ReplayModRestriction) as plugin
     * @param player The player that will receive the restrictions
     * @param restrictions The restrictions that will be changed, restrictions that aren't specified in this map won't be changed
     */
    public static void sendRestrictions(Player player, Map<String, Boolean> restrictions)
    {
        sendRestrictions(plugin, player, restrictions);
    }

    /**
     * Sends restriction strings to the player using {@link ReplayModRestriction) as plugin
     * @param plugin The plugins must have registered {@link #RESTRICTION_CHANNEL} as plugin message output
     * @param player The player that will receive the restrictions
     * @param restrictions The restrictions that will be changed, restrictions that aren't specified in this map won't be changed
     */
    public static void sendRestrictions(Plugin plugin, Player player, Map<String, Boolean> restrictions)
    {
        if(restrictions.isEmpty())
            return;

        player.sendPluginMessage(plugin, RESTRICTION_CHANNEL, createMessage(restrictions));
    }

    /**
     * Serializes a restriction map to be sent as plugin message
     * @param restrictions The restrictions that will be changed, restrictions that aren't specified in this map won't be changed
     * @throws NullPointerException if the map contains {@code null} keys or values
     */
    public static byte[] createMessage(Map<String, Boolean> restrictions)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        try
        {
            for(Map.Entry<String, Boolean> entry : restrictions.entrySet())
            {
                byte[] key = entry.getKey().toLowerCase().getBytes("UTF-8");
                out.writeByte(key.length);
                out.write(key);
                out.writeBoolean(entry.getValue());
            }
            out.flush();
        }
        catch(IOException e)
        {
            // This should be impossible
            throw new RuntimeException(e);
        }

        return bos.toByteArray();
    }

    // ---------------------  Configurations and Backwards compatibility ---------------------  //

    /**
     * The default restrictions that are applied to all players
     */
    private Map<String, Boolean> defaultRestrictions = new HashMap<String, Boolean>();

    /**
     * <p>A method reference to org.bukkit.craftbukkit.vX_X_RX.entity.CraftPlayer#addChannel(String)</p>
     * <p>This is used to force the server to send the plugin message even if the client did not register the channel.<br>
     *     This is needed because ReplayMod didn't register the channel properly before version 1.1.
     * </p>
     */
    private Method playerAddChannel;

    @Override
    public void onEnable()
    {
        // Required for API calls
        plugin = this;

        // Load the configs
        saveDefaultConfig();
        reloadConfig();

        // Register the channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, RESTRICTION_CHANNEL);

        // Parse the configs
        ConfigurationSection defaultSection = getConfig().getConfigurationSection("default-restrictions");
        if(defaultSection != null)
            for(String key: defaultSection.getKeys(false))
            {
                String value = defaultSection.getString(key);
                if(value.equalsIgnoreCase("force-false"))
                    defaultRestrictions.put(key, false);
                else if(defaultSection.getBoolean(key))
                    defaultRestrictions.put(key, true);
            }

        // Register the join event
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable()
    {
        HandlerList.unregisterAll((Plugin) this);
        plugin = null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();

        // This is needed for backwards compatibility but is not required for ReplayMod 1.1+
        try
        {
            if(playerAddChannel == null)
                playerAddChannel = player.getClass().getDeclaredMethod("addChannel", String.class);
            playerAddChannel.invoke(player, RESTRICTION_CHANNEL);
        }
        catch(Exception e)
        {
            getLogger().warning("Failed to force the server to send "+RESTRICTION_CHANNEL+" messages to "+player.getName()+". "+e);
        }

        // Sends the default restrictions
        sendRestrictions(this, player, defaultRestrictions);
    }
}
