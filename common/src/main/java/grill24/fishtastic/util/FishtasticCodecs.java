package grill24.fishtastic.util;

import com.mojang.serialization.Codec;
import net.minecraft.Util;
import net.minecraft.world.phys.Vec2;

import java.util.List;

/** Codecs that vanilla gained after 1.21.1. Same wire/JSON format as the vanilla versions on 26.1.2. */
public final class FishtasticCodecs {
    private FishtasticCodecs() {}

    /** 26.1.2's {@code Vec2.CODEC}: a two-float list, {@code [x, y]}. */
    public static final Codec<Vec2> VEC2 = Codec.FLOAT.listOf().comapFlatMap(
            floats -> Util.fixedSize(floats, 2).map(list -> new Vec2(list.get(0), list.get(1))),
            vec -> List.of(vec.x, vec.y));
}
