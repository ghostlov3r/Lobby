package dev.ghostlov3r.lobby;

import dev.ghostlov3r.beengine.Beengine;
import dev.ghostlov3r.beengine.block.Blocks;
import dev.ghostlov3r.beengine.entity.util.EntitySpawnHelper;
import dev.ghostlov3r.beengine.inventory.PlayerInventory;
import dev.ghostlov3r.beengine.item.Item;
import dev.ghostlov3r.beengine.player.Player;
import dev.ghostlov3r.beengine.player.PlayerInfo;
import dev.ghostlov3r.beengine.score.Scoreboard;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.Sound;
import dev.ghostlov3r.beengine.world.World;
import dev.ghostlov3r.minecraft.MinecraftSession;
import dev.ghostlov3r.nbt.NbtMap;
import lord.core.gamer.Gamer;

public class LobbyGamer extends Gamer {

	public boolean shouldHidePlayers = false;

	public LobbyGamer(MinecraftSession session, PlayerInfo clientID, boolean ip, NbtMap port) {
		super(session, clientID, ip, port);
	}

	@Override
	public void onSuccessAuth() {
		setAllowFlight(true);
		giveItems();
		setScore(new Scoreboard(this));
		score().setHeader("Lord Hub");
		score().show();
		score().set(0, " ");
		updateOnlineScore();
		score().set(2, "  ");
	}

	void updateOnlineScore () {
		score().set(1, " Общий онлайн: "+Lobby.instance.online);
	}

	private void giveItems () {
		PlayerInventory inv = inventory();

		giveHidingItem();
	}

	protected Item hidingItem () {
		return shouldHidePlayers ? Blocks.CARVED_PUMPKIN().asItem() : Blocks.LIT_PUMPKIN().asItem();
	}

	private int nextHidingUse;

	private static final int HIDE_PLAYERS_SLOT = 1;

	protected void giveHidingItem () {
		inventory().setItem(HIDE_PLAYERS_SLOT, hidingItem()
				.setCustomName(decorateName(shouldHidePlayers ? "Показать игроков" : "Скрыть игроков"))
				.onInteract((p, b) -> {
					if (world == World.defaultWorld()) {
						if (nextHidingUse < Beengine.thread().currentTick()) {
							nextHidingUse = Beengine.thread().currentTick() + 40;
							shouldHidePlayers = !shouldHidePlayers;
							if (shouldHidePlayers) {
								viewers().forEach(viewer -> {
									EntitySpawnHelper.despawn(viewer, LobbyGamer.this);
								});
							} else {
								world.unsafe().getViewersForPosition(LobbyGamer.this).forEach(viewer -> {
									EntitySpawnHelper.spawn(viewer, LobbyGamer.this);
								});
							}
							giveHidingItem();
							broadcastSound(Sound.POP, asList());
						}
					}
				})
		);
	}

	public String decorateName(String name) {
		return TextFormat.GREEN + Lobby.instance.config().menuItemDecorSymbol + " " + TextFormat.GOLD + name + " " + TextFormat.GREEN + Lobby.instance.config().menuItemDecorSymbol;
	}

	@Override
	public boolean shouldSpawnTo (Player player) {
		LobbyGamer gamer = (LobbyGamer) player;
		if (gamer.world == World.defaultWorld() && gamer.shouldHidePlayers) {
			return false;
		}
		return super.shouldSpawnTo(player);
	}
}
