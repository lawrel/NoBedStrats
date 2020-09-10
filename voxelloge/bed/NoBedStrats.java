package voxelloge.bed;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.entity.EntityDamageEvent;
import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.ComplexLivingEntity;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.boss.DragonBattle;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import java.util.List;
import static java.util.Collections.emptyList;


public class NoBedStrats extends JavaPlugin implements Listener {
	private static final float POWER = 5f;
	private static final double RADIUS_SQUARED = Math.pow(2d*POWER, 2d);
	private static final String META_KEY = "beddamage", CONFIG_KEY = "nodamage";


	private boolean disabled;
	private final FixedMetadataValue defaultMeta, triggerMeta;

	public NoBedStrats() {
		this.disabled = true;
		this.defaultMeta = new FixedMetadataValue(this, false);
		this.triggerMeta = new FixedMetadataValue(this, true);
	}

	@Override
	public void onLoad() {
		this.saveDefaultConfig();
	}

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		this.disabled = !this.getConfig().getBoolean(CONFIG_KEY);
	}

	@Override
	public void onDisable() {
		// I don't want to have to remove potential metadata from every entity in every dimension
		this.disabled = true;
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (this.disabled || !event.getAction().equals(RIGHT_CLICK_BLOCK)) return;
		Player player = event.getPlayer();
		if (player.isSneaking()) return;
		Block block = event.getClickedBlock();
		World world = block.getWorld();
		boolean checkBed = true, checkAnchor = true;
		switch (world.getEnvironment()) {
			case NORMAL:
				checkBed = false;
				break;
			case NETHER:
				checkAnchor = false;
		}
		Location blockLocation = block.getLocation();
		BlockData data = block.getBlockData();
		if (checkBed && data instanceof Bed) {
			Bed bedData = (Bed)data;
			// For some reason, if you replace the head with air, the foot doesn't drop a bed, but
			// the same doesn't happen when you replace the foot.
			if (bedData.getPart().equals(Bed.Part.HEAD)) block.setType(Material.AIR);
			else
				world.getBlockAt(blockLocation.add(bedData.getFacing().getDirection()))
					.setType(Material.AIR);
		} else if (checkAnchor && data instanceof RespawnAnchor) {
			RespawnAnchor anchorData = (RespawnAnchor)data;
			int charges = anchorData.getCharges();
			if (charges == 0 || event.hasItem()
				&& event.getItem().getType().equals(Material.GLOWSTONE)
				&& charges != anchorData.getMaximumCharges()
			) return;
			block.setType(Material.AIR);
		} else return;
		event.setCancelled(true);
		for (Entity entity : world.getEntities()) {
			boolean eligible = true;
			if (entity instanceof HumanEntity)
				switch (((HumanEntity)entity).getGameMode()) {
					case CREATIVE: // FALL THROUGH
					case SPECTATOR: eligible = false;
				}
			else if (entity instanceof ComplexEntityPart) eligible = false;
			if (eligible && entity.getLocation().distanceSquared(blockLocation) <= RADIUS_SQUARED)
				entity.setMetadata(
					META_KEY, entity == player ? this.triggerMeta : this.defaultMeta
				);
		}
		DragonBattle dragonBattle = world.getEnderDragonBattle();
		if (dragonBattle != null) {
			ComplexLivingEntity dragon = dragonBattle.getEnderDragon();
			if (dragon != null) {
				int counter = 0;
				for (ComplexEntityPart part : dragon.getParts())
					if (part.getLocation().distanceSquared(blockLocation) <= RADIUS_SQUARED)
						++counter;
				if (counter != 0) dragon.setMetadata(META_KEY, new MetaCounter(this, counter));
			}
		}
		world.createExplosion(blockLocation, POWER, false, false);
	}

	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		if (this.disabled || !event.getCause().equals(BLOCK_EXPLOSION)) return;
		Entity entity = event.getEntity();
		MetadataValue meta = null;
		for (MetadataValue value : entity.getMetadata(META_KEY))
			if (value.getOwningPlugin() == this) {
				meta = value;
				break;
			}
		if (meta == null) return;
		if (meta == this.defaultMeta) {
			event.setCancelled(true);
			entity.removeMetadata(META_KEY, this);
		} else if (
			meta == this.triggerMeta
				&& event.getFinalDamage() < ((Damageable)(event.getEntity())).getHealth()
		) entity.removeMetadata(META_KEY, this);
		else {
			event.setCancelled(true);
			if (((MetaCounter)meta).preDecrement() == 0) entity.removeMetadata(META_KEY, this);
		}
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		if (!player.hasMetadata(META_KEY)) return;
		event.setDeathMessage(player.getName() + " was killed by [More Precise Game Design]");
		player.removeMetadata(META_KEY, this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 0) {
			sender.sendMessage(ChatColor.RED + "Too many arguments");
			return false;
		}
		this.reloadConfig();
		boolean oldDisabled = this.disabled;
		this.disabled = !this.getConfig().getBoolean(CONFIG_KEY);
		if (this.disabled == oldDisabled)
			sender.sendMessage(ChatColor.GRAY + "[NoBedStrats] Config was unchanged");
		else
			Bukkit.broadcastMessage(
				ChatColor.GRAY + "[NoBedStrats] Config reloaded; " + ChatColor.WHITE
					+ (this.disabled ? "re-enabled" : "disabled") + ChatColor.GRAY
					+ " block & entity damage from bed & respawn anchor explosions"
			);
		return true;
	}

	@Override
	public List<String> onTabComplete(
		CommandSender sender, Command command, String label, String[] args
	) {
		return emptyList();
	}
}
