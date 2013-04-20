package org.mcteam.ancientgates.listeners;

import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.mcteam.ancientgates.Conf;
import org.mcteam.ancientgates.Gate;
import org.mcteam.ancientgates.Plugin;
import org.mcteam.ancientgates.util.GeometryUtil;


public class PluginPlayerListener implements Listener {

	public Plugin plugin;

	public PluginPlayerListener(Plugin plugin)
	{
		this.plugin = plugin;
	}

	@EventHandler
	public void onPlayerPortal(PlayerPortalEvent event) {
		if (event.isCancelled()) {
			return;
		}
		Gate nearestGate = null;
		Location playerLocation = event.getPlayer().getLocation();
		double shortestDistance = -1;

		for (Gate gate : Gate.getAll()) {
			if ( gate.getFrom() == null || gate.getTo() == null) {
				continue;
			}

			if ( ! gate.getFrom().getWorld().equals(playerLocation.getWorld())) {
				continue; 
			}

			double distance = GeometryUtil.distanceBetweenLocations(playerLocation, gate.getFrom());

			if (distance > Conf.getGateSearchRadius()) {
				continue;
			}

			if (shortestDistance == -1 || shortestDistance > distance) {
				nearestGate = gate;
				shortestDistance = distance;
			}
		}

		if (nearestGate != null) {
			event.setCancelled(true);
			checkChunkLoad(nearestGate.getTo().getBlock());
			event.getPlayer().teleport(nearestGate.getTo()); 
			event.setTo(nearestGate.getTo());
		}
	}

	private void checkChunkLoad(Block b) 
	{
		World w = b.getWorld();
		Chunk c = b.getChunk();

		if ( ! w.isChunkLoaded(c) )
		{
			Plugin.log(Level.FINE, "Loading chunk: " + c.toString() + " on: " + w.toString());
			w.loadChunk(c);
		}
	}
}
