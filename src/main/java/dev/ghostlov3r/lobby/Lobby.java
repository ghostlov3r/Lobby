package dev.ghostlov3r.lobby;

import beengine.Server;
import beengine.event.EventListener;
import beengine.event.EventManager;
import beengine.event.EventPriority;
import beengine.event.Priority;
import beengine.event.player.PlayerCreationEvent;
import beengine.event.player.PlayerDataSaveEvent;
import beengine.event.player.PlayerPreLoginEvent;
import beengine.event.server.QueryRegenerateEvent;
import beengine.minecraft.LoginSuccessor;
import beengine.nbt.*;
import beengine.permission.BanEntry;
import beengine.plugin.AbstractPlugin;
import beengine.scheduler.AsyncTask;
import beengine.util.config.Config;
import dev.ghostlov3r.minigame.MiniGame;
import lombok.SneakyThrows;
import lord.core.Lord;
import lord.core.union.UnionDataProvider;
import lord.core.union.UnionServer;
import lord.core.union.packet.*;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.util.function.IntSupplier;

@Priority(EventPriority.HIGHEST)
public class Lobby extends AbstractPlugin<Config> implements EventListener<LobbyGamer> {

	static Lobby instance;
	DB dataDB;
	// Map<String, UnionServer> players = Collections.synchronizedMap(new HashMap<>());

	@SneakyThrows
	@Override
	protected void onLoad() {
		instance = this;

		MiniGame.builder().build(); // DUMMY

		dataDB = Iq80DBFactory.factory.open(dataPath().resolve("union").toFile(), new Options());
		Lord.unionHandler.servers().forEach(server -> {
			server.handler = new PacketHandler();
		});
		Lord.unionHandler.setProvider(new DataProvider());

		IntSupplier currentCalc = Lord.unionHandler.onlineCalculator();
		Lord.unionHandler.setOnlineCalculator(() ->
				Lord.unionHandler.thisServer().onlineCount = (Lord.unionHandler.servers().stream()
				.filter(server -> server.isOnline)
				.filter(server -> server != Lord.unionHandler.thisServer())
				.mapToInt(server -> server.onlineCount)
				.sum()
				+ currentCalc.getAsInt()));
	}

	@SneakyThrows
	@Override
	protected void onEnable() {
		EventManager.get().register(this, this);
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
			}
			//else if (players.containsKey(packet.name)) {
			//	response.status = GamerDataResponse.Status.DUPLICATE;
			//}
			else {
				NbtMap data = unionDataOf(packet.name);
				response.status = GamerDataResponse.Status.ALLOW;
				response.gamerData = data != null ? data : NbtMap.EMPTY;
			}
			server.sendPacket(response);
			return true;
		}

		@Override
		public boolean handle(GamerDataSave packet, UnionServer server) {
			GamerDataSaved response = new GamerDataSaved();
			response.name = packet.name;
			// Send response before saving because DB is able to start slow compaction during save process
			server.sendPacket(response);
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
	public void onPlayerPreLogin(PlayerPreLoginEvent event) {
		event.setSuccessor(new LoginSuccessor(event.session()) {
			@Override
			public void onLoginSuccess() {
				//if (players.containsKey(event.session().playerInfo().username())) {
				//	event.session().disconnect(
				//			"Ты уже играешь с другого устройства! \nЕсли это не так, попробуй зайти снова.");
				//} else {
					session().onServerLoginSuccess();
				//}
			}
		});
	}

	@Override
	public void onQueryRegenerate(QueryRegenerateEvent event) {
		event.getQueryInfo().setPlayerCount(Lord.unionHandler.thisServer().onlineCount);
		event.getQueryInfo().setMaxPlayerCount(Math.max(Lord.unionHandler.thisServer().onlineCount, 750));
	}

	/*SkinAdapterSingleton.set(new LegacySkinAdapter() {

			@SneakyThrows
			@Override
			public SkinData toSkinData(Skin skin) {
				if (!Files.exists(Beengine.DATA_PATH.resolve("steve"))) {
					var out = new PacketOutputStream();
					out.writeString(skin.getSkinId());
					out.writeByteArray(skin.getSkinData());
					out.writeByteArray(skin.getCapeData());
					out.writeString(skin.getGeometryName());
					out.writeByteArray(skin.getGeometryData());
					Files.write(Beengine.DATA_PATH.resolve("steve"), out.trimmedBuffer());
				}
				return super.toSkinData(skin);
			}
		});*/
}
