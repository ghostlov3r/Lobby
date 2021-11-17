package dev.ghostlov3r.lobby;

import dev.ghostlov3r.beengine.Server;
import dev.ghostlov3r.beengine.entity.Entity;
import dev.ghostlov3r.beengine.event.EventListener;
import dev.ghostlov3r.beengine.event.EventManager;
import dev.ghostlov3r.beengine.event.EventPriority;
import dev.ghostlov3r.beengine.event.Priority;
import dev.ghostlov3r.beengine.event.block.BlockBreakEvent;
import dev.ghostlov3r.beengine.event.block.BlockPlaceEvent;
import dev.ghostlov3r.beengine.event.entity.EntityDamageByEntityEvent;
import dev.ghostlov3r.beengine.event.entity.EntityDamageEvent;
import dev.ghostlov3r.beengine.event.inventory.InventoryTransactionEvent;
import dev.ghostlov3r.beengine.event.player.*;
import dev.ghostlov3r.beengine.event.server.QueryRegenerateEvent;
import dev.ghostlov3r.beengine.permission.BanEntry;
import dev.ghostlov3r.beengine.plugin.AbstractPlugin;
import dev.ghostlov3r.beengine.scheduler.AsyncTask;
import dev.ghostlov3r.beengine.scheduler.Scheduler;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.Sound;
import dev.ghostlov3r.beengine.world.World;
import dev.ghostlov3r.minecraft.protocol.v113.packet.WorldSoundEvent;
import dev.ghostlov3r.nbt.*;
import lombok.SneakyThrows;
import lord.core.Lord;
import lord.core.union.UnionDataProvider;
import lord.core.union.UnionServer;
import lord.core.union.packet.GamerDataRequest;
import lord.core.union.packet.GamerDataResponse;
import lord.core.union.packet.GamerDataSave;
import lord.core.union.packet.UnionPacketHandler;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

@Priority(EventPriority.HIGH)
public class Lobby extends AbstractPlugin<LobbyConfig> implements EventListener<LobbyGamer> {

	static Lobby instance;
	DB dataDB;
	int lastOnline;
	int online;
	Map<UUID, LobbyConfig.JoinEntityData> joinEntities = new HashMap<>();

	@SneakyThrows
	@Override
	protected void onLoad() {
		instance = this;
		config().save();
		config().joinEntities.forEach((strUuid, data) -> {
			joinEntities.put(UUID.fromString(strUuid), data);
		});
		dataDB = Iq80DBFactory.factory.open(dataPath().resolve("union").toFile(), new Options());
		Lord.unionHandler.servers().forEach(server -> {
			server.handler = new PacketHandler();
		});
		Lord.unionHandler.setProvider(new DataProvider());

		IntSupplier currentCalc = Lord.unionHandler.onlineCalculator();
		Lord.unionHandler.setOnlineCalculator(() ->
				online = (Lord.unionHandler.servers().stream()
				.filter(server -> server.isOnline)
				.mapToInt(server -> server.onlineCount)
				.sum()
				+ currentCalc.getAsInt()));
	}

	@SneakyThrows
	@Override
	protected void onEnable() {
		EventManager.get().register(this, this);
		Server.commandMap().register("joinnpc", new JoinNpcCommand());

		Scheduler.repeat(100, () -> {
			int onl = online;
			if (lastOnline != onl) {
				lastOnline = onl;
				World.defaultWorld().unsafe().players().values().forEach(player -> {
					LobbyGamer gamer = (LobbyGamer) player;
					gamer.updateOnlineScore();
				});
			}
			updateJoinNpcs();
		});
	}

	void updateJoinNpcs () {
		for (Entity entity : World.defaultWorld().getEntities()) {
			if (entity instanceof LobbyNpc) {
				if (entity.uniqueIdCalculated()) {
					updateJoinNpc(entity);
				}
			}
		}
	}

	void updateJoinNpc (Entity entity) {
		LobbyConfig.JoinEntityData data = joinEntities.get(entity.uniqueId());
		if (data != null) {
			UnionServer server = Lord.unionHandler.getServer(data.serverId);
			if (server != null) {
				String nameTag = server.name + "\n";
				if (server.isOnline) {
					nameTag += TextFormat.GREEN+"Онлайн: "+TextFormat.YELLOW+server.onlineCount;
				} else {
					nameTag += TextFormat.RED+"Оффлайн";
				}
				entity.setNameTag(nameTag);
			}
			else {
				entity.setNameTag(TextFormat.RED+"Оффлайн");
			}
		}
	}

	public class PacketHandler extends UnionPacketHandler {
		@SneakyThrows
		@Override
		public boolean handle(GamerDataRequest packet, UnionServer server) {
			GamerDataResponse response = new GamerDataResponse();
			response.requestId = packet.requestId;
			BanEntry ban = Server.bannedNames().getEntry(packet.name);
			if (ban != null && !ban.hasExpired()) {
				response.status = GamerDataResponse.Status.BANNED;
				response.bannedUntil = ban.expirationMilli();
			} else {
				NbtMap data = unionDataOf(packet.name);
				response.status = GamerDataResponse.Status.ALLOW;
				response.gamerData = data != null ? data : NbtMap.EMPTY;
			}
			// TODO DUPLICATE
			server.sendPacket(response);
			return true;
		}

		@Override
		public boolean handle(GamerDataSave packet, UnionServer server) {
			writeUnionData(packet.name, packet.data);
			return true;
		}
	}

	public class DataProvider extends UnionDataProvider {

		@Override
		public NbtMap readData(String name) {
			NbtMap data = unionDataOf(name);
			return data != null ? data : NbtMap.EMPTY;
		}

		@Override
		public void writeData(String name, NbtMap data) {
			Server.asyncPool().execute(new AsyncTask() {
				@Override
				public void run() {
					writeUnionData(name, data);
				}
			});
		}
	}

	public NbtMap unionDataOf (String name) {
		byte[] data = dataDB.get(Iq80DBFactory.bytes(name.toLowerCase()));
		if (data != null) {
			return NbtDecoder.decode(NbtReader.NETWORK, NbtType.COMPOUND, data);
		}
		return null;
	}

	public void writeUnionData (String name, NbtMap data) {
		dataDB.put(Iq80DBFactory.bytes(name.toLowerCase()), NbtEncoder.encode(NbtWriter.NETWORK, data).trimmedBuffer());
	}

	@Override
	public void onPlayerMove(PlayerMoveEvent<LobbyGamer> event) {
		if (event.player().y < 10) {
			event.player().teleport(event.player().world().getSpawnPosition().addY(2));
			event.player().broadcastSound(Sound.ENDERMAN_TELEPORT, event.player().asList());
		}
	}

	@Override
	public void onBlockBreak(BlockBreakEvent<LobbyGamer> event) {
		event.cancel();
	}

	@Override
	public void onBlockPlace(BlockPlaceEvent<LobbyGamer> event) {
		event.cancel();
	}

	@Override
	public void onInventoryTransaction(InventoryTransactionEvent event) {
		event.cancel();
	}

	@Override
	public void onEntityDamage(EntityDamageEvent event) {
		event.cancel();
	}

	@Override
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		if (event.damager() instanceof LobbyGamer gamer) {
			if (gamer.isAuthorized()) {
				if (event.entity() instanceof LobbyNpc npc) {
					if (npc.uniqueIdCalculated()) {
						LobbyConfig.JoinEntityData data = joinEntities.get(npc.uniqueId());
						if (data != null) {
							UnionServer server = Lord.unionHandler.getServer(data.serverId);
							if (server != null && server.isOnline) {
								gamer.transfer(server.address.getAddress().getHostAddress(), server.address.getPort());
							} else {
								gamer.sendMessage(TextFormat.RED+"Этот режим временно выключен");
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void onPlayerItemConsume(PlayerItemConsumeEvent<LobbyGamer> event) {
		event.cancel();
	}

	@SneakyThrows
	@Override
	protected void onDisable() {
		dataDB.close();
	}

	@Override
	public void onPlayerDataSave(PlayerDataSaveEvent event) {
		event.cancel();
	}

	@Override
	public void onPlayerCreation(PlayerCreationEvent event) {
		event.setActualClass(LobbyGamer.class);
	}

	@Override
	public void onQueryRegenerate(QueryRegenerateEvent event) {
		event.getQueryInfo().setPlayerCount(online);
		event.getQueryInfo().setMaxPlayerCount(Math.max(online, 750));
	}

	@Override
	public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
		if (event.isFlying()) {
			if (event.player().isSurvival()) {
				if (config().doubleJump) {
					event.cancel();
					event.player().setMotion(event.player().directionVector().addY(0.5f).multiply(1.2f));
					event.player().broadcastSound(Sound.of(WorldSoundEvent.SoundId.ARMOR_EQUIP_GENERIC));
				}
			}
		}
	}
}
