package dev.ghostlov3r.lobby;

import dev.ghostlov3r.beengine.entity.any.EntityHuman;
import dev.ghostlov3r.beengine.entity.util.Location;
import dev.ghostlov3r.minecraft.data.skin.SkinData;
import dev.ghostlov3r.minecraft.generic.PacketInputStream;
import dev.ghostlov3r.minecraft.generic.PacketOutputStream;
import dev.ghostlov3r.minecraft.protocol.SkinCoder;
import dev.ghostlov3r.minecraft.protocol.v465.SkinCoder_v465;
import dev.ghostlov3r.nbt.NbtMap;

public class LobbyNpc extends EntityHuman {

	private static final SkinCoder skinCoder = new SkinCoder_v465();

	public LobbyNpc(Location location, SkinData skin) {
		super(location, skin);
	}

	@Override
	public void writeSaveData(NbtMap.Builder nbt) {
		super.writeSaveData(nbt);
		if (skin != null) {
			PacketOutputStream stream = new PacketOutputStream();
			skinCoder.write(skin, stream);
			nbt.setByteArray("LordSkin", stream.trimmedBuffer());
		}
	}

	@Override
	public void readSaveData(NbtMap nbt) {
		super.readSaveData(nbt);
		if (nbt.containsKey("LordSkin")) {
			PacketInputStream stream = new PacketInputStream(nbt.getByteArray("LordSkin"));
			skin = skinCoder.read(stream);
		}
	}
}
