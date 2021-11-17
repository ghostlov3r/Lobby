package dev.ghostlov3r.lobby;

import dev.ghostlov3r.beengine.utils.config.Config;
import dev.ghostlov3r.beengine.utils.config.Name;

import java.util.HashMap;
import java.util.Map;

@Name("lobby")
public class LobbyConfig extends Config {

	public static class JoinEntityData {
		public int serverId;
	}

	public String menuItemDecorSymbol = "◆";
	public boolean doubleJump = true;
	public Map<String, JoinEntityData> joinEntities = new HashMap<>();
}
