package org.mcteam.ancientgates;

import java.lang.reflect.Modifier;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import org.bukkit.craftbukkit.libs.com.google.gson.Gson;
import org.bukkit.craftbukkit.libs.com.google.gson.GsonBuilder;
import org.mcteam.ancientgates.gson.typeadapters.LocationTypeAdapter;

import org.mcteam.ancientgates.commands.BaseCommand;
import org.mcteam.ancientgates.commands.base.CommandAddFrom;
import org.mcteam.ancientgates.commands.base.CommandAddTo;
import org.mcteam.ancientgates.commands.base.CommandClose;
import org.mcteam.ancientgates.commands.base.CommandCloseAll;
import org.mcteam.ancientgates.commands.base.CommandCreate;
import org.mcteam.ancientgates.commands.base.CommandDelete;
import org.mcteam.ancientgates.commands.base.CommandHelp;
import org.mcteam.ancientgates.commands.base.CommandInfo;
import org.mcteam.ancientgates.commands.base.CommandList;
import org.mcteam.ancientgates.commands.base.CommandOpen;
import org.mcteam.ancientgates.commands.base.CommandOpenAll;
import org.mcteam.ancientgates.commands.base.CommandRemExec;
import org.mcteam.ancientgates.commands.base.CommandRemFrom;
import org.mcteam.ancientgates.commands.base.CommandRemTo;
import org.mcteam.ancientgates.commands.base.CommandRename;
import org.mcteam.ancientgates.commands.base.CommandSetConf;
import org.mcteam.ancientgates.commands.base.CommandSetEntities;
import org.mcteam.ancientgates.commands.base.CommandSetExec;
import org.mcteam.ancientgates.commands.base.CommandSetFrom;
import org.mcteam.ancientgates.commands.base.CommandSetInventory;
import org.mcteam.ancientgates.commands.base.CommandSetMaterial;
import org.mcteam.ancientgates.commands.base.CommandSetMessage;
import org.mcteam.ancientgates.commands.base.CommandSetTo;
import org.mcteam.ancientgates.commands.base.CommandSetVehicles;
import org.mcteam.ancientgates.commands.base.CommandTeleportFrom;
import org.mcteam.ancientgates.commands.base.CommandTeleportTo;
import org.mcteam.ancientgates.commands.bungee.CommandSetBungeeType;
import org.mcteam.ancientgates.commands.economy.CommandSetCost;
import org.mcteam.ancientgates.commands.sockets.CommandAddServer;
import org.mcteam.ancientgates.commands.sockets.CommandRemServer;
import org.mcteam.ancientgates.commands.sockets.CommandServerList;
import org.mcteam.ancientgates.listeners.PluginBlockListener;
import org.mcteam.ancientgates.listeners.PluginEntityListener;
import org.mcteam.ancientgates.listeners.PluginMessengerListener;
import org.mcteam.ancientgates.listeners.PluginPlayerListener;
import org.mcteam.ancientgates.listeners.PluginMovementListener;
import org.mcteam.ancientgates.listeners.PluginSocketListener;
import org.mcteam.ancientgates.metrics.MetricsStarter;
import org.mcteam.ancientgates.queue.BungeeQueue;
import org.mcteam.ancientgates.sockets.SocketServer;
import org.mcteam.ancientgates.util.types.PluginMessage;

public class Plugin extends JavaPlugin {
	
	public static Plugin instance;
	public static Logger log;
	
	public static SocketServer serv = null;
	public static Permission perms = null;
    public static Economy econ = null;
    
	public static String bungeeServerName = null;
	public static ArrayList<String> bungeeServerList = null;
    
    private Listener pluginMovementListener = null;
    private PluginMessageListener pluginMessengerListener = null;
	private String baseCommand;
	
	public final static Gson gson = new GsonBuilder()
	.setPrettyPrinting()
	.excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.VOLATILE)
	.registerTypeAdapter(Location.class, new LocationTypeAdapter())
	.create();
	
	// HashMap of incoming BungeeCord players & passengers
	public static Map<String, BungeeQueue> bungeeCordInQueue = new HashMap<String, BungeeQueue>();

	// ArrayList of incoming BungeeCord entities & vehicles
	public static ArrayList<BungeeQueue> bungeeCordEntityInQueue = new ArrayList<BungeeQueue>();
	public static ArrayList<BungeeQueue> bungeeCordVehicleInQueue = new ArrayList<BungeeQueue>();
	
	// HashMap of outgoing BungeeCord players & passengers
	public static Map<String, String> bungeeCordOutQueue = new HashMap<String, String>();
	
	// ArrayList of outgoing BungeeCord messages
	public static ArrayList<PluginMessage> bungeeMsgQueue = new ArrayList<PluginMessage>();
	
	public static HashMap<String, Long> lastTeleportTime = new HashMap<String, Long>();
	public static HashMap<String, Long> lastMessageTime = new HashMap<String, Long>();
	
	// Commands
	public List<BaseCommand> commands = new ArrayList<BaseCommand>();
	
	public Plugin() {
		instance = this;
	}

	@Override
	public void onDisable() {
		// Unbind SocketComms listener
		if (serv != null) {
			Plugin.log("Disabling comms channel");
			serv.close();
			serv.stop();
			serv = null;
		}
		
		log("Disabled");
	}

	@Override
	public void onEnable() {
		//Enable permissions and economy
		if (getServer().getPluginManager().getPlugin("Vault") != null) {
			if (!setupPermissions()) {
				log("Vault permissions hook not found. Permissions support unavailable.");
			}	
			if(!setupEconomy()) {
				log("Vault economy hook not found. Economy support unavailable.");
			}
		} else {
			log("Vault dependency not found, defaulting to Bukkit Permissions. Economy support unavailable.");
		}
		
		// Ensure base folder exists!
		this.getDataFolder().mkdirs();
		
		// Load config from disc
		Conf.load();
		
		//Check for updates
		if (Conf.autoUpdate) {
			new Updater(this, 51406, this.getFile(), Updater.UpdateType.DEFAULT, false);
		}
		
		// Register events
		PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvents(new PluginBlockListener(this), this);
		pm.registerEvents(new PluginEntityListener(this), this);
		pm.registerEvents(new PluginPlayerListener(this), this);

		// Load reloadable config
		reload(null);
		
		//Submit Stats
		MetricsStarter metrics = new MetricsStarter(this);
		metrics.setupMetrics();
		
		// Load gates from disc (1 tick ensures worlds are loaded)
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			public void run() {
				Gates.load();
				log("Enabled");
			}
		}, 1);
	}
	
	// -------------------------------------------- //
	// Auto-reload config
	// -------------------------------------------- //
	public void reload(CommandSender sender) {
		// Takedown BungeeCord support
		if (!Conf.bungeeCordSupport && pluginMessengerListener != null) takedownBungeeCord();
		if (serv != null) takedownSocketComms();
		// Setup BungeeCord support
		if (Conf.bungeeCordSupport && pluginMessengerListener == null) setupBungeeCord();
		if (Conf.bungeeCordSupport && Conf.useSocketComms && serv == null ) setupSocketComms(sender);
		
		// Add the commands
		if (!commands.isEmpty()) commands.clear();
		commands.add(new CommandHelp());
		commands.add(new CommandCreate());
		commands.add(new CommandDelete());
		commands.add(new CommandSetExec());
		commands.add(new CommandSetFrom());
		commands.add(new CommandSetTo());
		commands.add(new CommandOpen());
		commands.add(new CommandClose());
		commands.add(new CommandRemExec());
		commands.add(new CommandRename());
		commands.add(new CommandSetMessage());
		if (Conf.useEconomy && econ != null) {
			commands.add(new CommandSetCost());
		}
		commands.add(new CommandSetEntities());
		if (!Conf.useVanillaPortals) {
			commands.add(new CommandSetVehicles());
			commands.add(new CommandSetMaterial());
		}
		commands.add(new CommandSetInventory());
		commands.add(new CommandTeleportFrom());
		commands.add(new CommandTeleportTo());
		commands.add(new CommandAddFrom());
		commands.add(new CommandRemFrom());
		commands.add(new CommandAddTo());
		commands.add(new CommandRemTo());
		commands.add(new CommandInfo());
		commands.add(new CommandList());
		commands.add(new CommandOpenAll());
		commands.add(new CommandCloseAll());
		if (Conf.bungeeCordSupport) {
			commands.add(new CommandSetBungeeType());
		}
		if (Conf.useSocketComms && serv != null) {
			commands.add(new CommandAddServer());
			commands.add(new CommandRemServer());
			commands.add(new CommandServerList());
		}
		commands.add(new CommandSetConf());
		
		// Register/Unregister events
		if (!Conf.useVanillaPortals && pluginMovementListener == null) {
			pluginMovementListener = new PluginMovementListener(this);
			PluginManager pm = this.getServer().getPluginManager();
			pm.registerEvents(pluginMovementListener, this);
		} else if (Conf.useVanillaPortals && pluginMovementListener != null) {
			HandlerList.unregisterAll(pluginMovementListener);
			pluginMovementListener = null;
		}
	}
	
	// -------------------------------------------- //
	// Setup optional dependencies
	// -------------------------------------------- //
	private void setupBungeeCord() {	
		// Enable required plugin channels 
		Plugin.log("Enabling bungeecord channels");
		pluginMessengerListener = new PluginMessengerListener();
		Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		Bukkit.getMessenger().registerIncomingPluginChannel(this, "BungeeCord", pluginMessengerListener);
		
		if (!Conf.useSocketComms) {
			Plugin.log("Socket comms disabled. Using generic BungeeCord messaging.");
			return;
		}
	}
	
	private void setupSocketComms(CommandSender sender) {
		// Load servers list
		Server.load();
		
		// Check socket comms port in range
		if (Conf.socketCommsPort > 65535) {
			Plugin.log("socketCommsPort out of range. Using generic BungeeCord messaging.");
			if (sender != null) sender.sendMessage(Conf.colorSystem+"\"socketCommsPort\" is out of range. Try another port.");
			return;
		}
		
		// Enable server socket channel
		Plugin.log("Enabling comms channel");
		try {
			serv = new SocketServer(0, Conf.socketCommsPort, Conf.socketCommsPass);
		} catch (BindException e) {
			Plugin.log("socketCommsPort already in use. Using generic BungeeCord messaging.");
			if (sender != null) sender.sendMessage(Conf.colorSystem+"\"socketCommsPort\" "+Conf.socketCommsPort+" is already in use. Try another port.");
			return;
		}
		serv.addClientListener(new PluginSocketListener());
	}
	
	private void takedownBungeeCord() {	
		// Disable required plugin channels 
		Plugin.log("Disabling bungeecord channels");
		Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
		Bukkit.getMessenger().unregisterIncomingPluginChannel(this, "BungeeCord", pluginMessengerListener);
		pluginMessengerListener = null;
    }
	
	private void takedownSocketComms() {	
		// Disable server socket channel
		Plugin.log("Disabling comms channel");
		serv.close();
		serv.stop();
		serv = null;
    }
	
	private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            perms = permissionProvider.getProvider();
        }
        return (perms != null);
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            econ = economyProvider.getProvider();
        }

        return (econ != null);
    }
	
	// -------------------------------------------- //
	// Check rights (online player)
	// -------------------------------------------- //
	public static boolean hasPermManage(CommandSender sender, String requiredPermission) {
		if(!(sender instanceof Player)) return true;
		
		Player player = (Player) sender;

		if (perms == null) {
			return sender.hasPermission(requiredPermission);
		} else {
			return perms.has(player, requiredPermission);
		}
	}
	
	// -------------------------------------------- //
	// Check rights (bungeeCord player)
	// -------------------------------------------- //
	public static boolean hasPermManage(String player, String requiredPermission) {
		if (perms == null) {
			return Bukkit.getServer().getOfflinePlayer(player).isOp();
		} else {
			return perms.playerHas(Bukkit.getWorlds().get(0), player, requiredPermission) |
					   Bukkit.getServer().getOfflinePlayer(player).isOp();
		}
	}
	
	// -------------------------------------------- //
	// Handle economy
	// -------------------------------------------- //
	public static boolean handleEconManage(CommandSender sender, Double requiredCost) {
		if(!(sender instanceof Player)) return true;
		
		Player player = (Player) sender;
		
		if (econ == null || !Conf.useEconomy || hasPermManage(sender, "ancientgates.econbypass") || requiredCost == 0.00) {
			return true;
		} else {
			Double balance = econ.getBalance(player.getName());
			if (requiredCost <= balance) {
				EconomyResponse r = econ.withdrawPlayer(player.getName(), requiredCost);
				if(r.transactionSuccess()) {
					sender.sendMessage(String.format("You were charged %s and now have %s.", econ.format(r.amount), econ.format(r.balance)));
					return true;
				} else {
	                sender.sendMessage(String.format("An error occured: %s.", r.errorMessage));
	                return false;
	            }
			} else {
				return false;
			}
		}
	}
	
	// -------------------------------------------- //
	// Commands
	// -------------------------------------------- //
	public String getBaseCommand() {
		if (this.baseCommand != null) 
                {
			return this.baseCommand;
		}
		
		Map<String, Map<String, Object>> Commands = this.getDescription().getCommands();
		this.baseCommand = Commands.keySet().iterator().next();
		return this.baseCommand;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) 
        {
		List<String> parameters = new ArrayList<String>(Arrays.asList(args));
		this.handleCommand(sender, parameters);
		return true;
	}
	
	public void handleCommand(CommandSender sender, List<String> parameters) {
		if (parameters.size() == 0) {
			this.commands.get(0).execute(sender, parameters);
			return;
		}
		
		String commandName = parameters.get(0).toLowerCase();
		parameters.remove(0);
		
		for (BaseCommand fcommand : this.commands) {
			if (fcommand.getAliases().contains(commandName)) {
				fcommand.execute(sender, parameters);
				return;
			}
		}
		
		sender.sendMessage(Conf.colorSystem+"Unknown gate-command \""+commandName+"\". Try "+Conf.colorCommand+"/"+getBaseCommand()+" help");       
	}
	
	// -------------------------------------------- //
	// Logging
	// -------------------------------------------- //
	public static void log(String msg) {
		log(Level.INFO, msg);
	}
	
	public static void log(Level level, String msg) {
		Logger.getLogger("Minecraft").log(level, "["+instance.getDescription().getFullName()+"] "+msg);
	}
	
}
