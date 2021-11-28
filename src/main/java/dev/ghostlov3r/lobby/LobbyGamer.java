package dev.ghostlov3r.lobby;

import dev.ghostlov3r.beengine.player.PlayerInfo;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.minecraft.MinecraftSession;
import dev.ghostlov3r.minigame.MGGamer;
import dev.ghostlov3r.nbt.NbtMap;
import lord.core.Lord;

import java.util.concurrent.TimeUnit;

import static dev.ghostlov3r.beengine.utils.TextFormat.GREEN;
import static dev.ghostlov3r.beengine.utils.TextFormat.YELLOW;

public class LobbyGamer extends MGGamer {

	public LobbyGamer(MinecraftSession session, PlayerInfo clientID, boolean ip, NbtMap port) {
		super(session, clientID, ip, port);
	}

	@Override
	protected ScoreUpdater newScoreUpdater() {
		return new LobbyScoreUpdater();
	}

	@Override
	protected InventoryUpdater newInvUpdater() {
		return new LobbyInventoryUpdater();
	}

	class LobbyInventoryUpdater extends InventoryUpdater {
		@Override
		protected void giveGameInfoItem() {
			// NOOP
		}

		@Override
		protected void giveStatsItem() {
			// NOOP
		}

		@Override
		protected void giveServerListItem() {
			// NOOP
		}
	}

	class LobbyScoreUpdater extends ScoreUpdater {
		@Override
		public void onLobbyJoin() {
			updateAllScoreLines();
		}

		void updateAllScoreLines () {
			score().set(0, " ");
			score().set(1, " Ник: "+ TextFormat.YELLOW+name()+ " ");
			score().set(2, " Ранг: "+ group().getPrefix() + " ");
			score().set(3, "  ");
			updateBalance();
			updatePlayedTime();
			score().set(6, "   ");
			onFullOnlineCountChange(Lord.unionHandler.thisServer().onlineCount);
			score().set(8, "    ");
		}

		void updateBalance () {
			score().set(4, " Золото: " + TextFormat.YELLOW+money+" ");
		}

		void updatePlayedTime () {
			long hours = TimeUnit.MINUTES.toHours(playedMinutes);
			String strHours = String.valueOf(hours);
			String end = switch (strHours.charAt(strHours.length() - 1)) {
				case '1' -> " час ";
				case '2', '3', '4' -> " часа ";
				default -> " часов ";
			};
			score().set(5, " Наиграно: "+GREEN+strHours+end);
		}

		@Override
		public void onFullOnlineCountChange(int newCount) {
			score().set(7, " Онлайн проекта: "+YELLOW+newCount+" ");
		}
	}
}
