package dev.ghostlov3r.lobby;

import dev.ghostlov3r.beengine.form.Form;
import dev.ghostlov3r.beengine.form.SimpleForm;
import dev.ghostlov3r.nbt.NbtMap;
import lord.core.Lord;
import lord.core.gamer.Gamer;
import lord.core.util.LordCommand;

public class JoinNpcCommand extends LordCommand {
	public JoinNpcCommand() {
		super("joinnpc");
	}

	@Override
	public void execute(Gamer gamer, String[] strings) {
		gamer.sendForm(Form.simple()
				.button("Создать", __ -> {
					LobbyNpc npc = new LobbyNpc(gamer, gamer.skin());
					NbtMap.Builder data = NbtMap.builder();
					gamer.writeOriginalPlayerSaveData(data);
					SimpleForm form = Form.simple();
					Lord.unionHandler.servers().forEach(server -> {
						form.button(server.name, ___ -> {
							npc.readSaveData(data.build());
							npc.setNameTag(server.name);
							npc.setNameTagVisible();
							npc.setNameTagAlwaysVisible();
							npc.spawn();

							var configData = new LobbyConfig.JoinEntityData();
							configData.serverId = server.id;
							Lobby.instance.config().joinEntities.put(npc.uniqueId().toString(), configData);
							Lobby.instance.config().save();

							Lobby.instance.joinEntities.put(npc.uniqueId(), configData);
							Lobby.instance.updateJoinNpc(npc);
						});
					});
					gamer.sendForm(form);
				})
		);
	}
}
