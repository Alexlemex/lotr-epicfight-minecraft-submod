package lotr_epicfight;

import net.minecraft.world.entity.player.Player;

/** The player currently finishing an item use (eating/drinking), so FoodData knows who to heal. */
public final class EatingState {
    private static final ThreadLocal<Player> EATER = new ThreadLocal<>();

    private EatingState() {}

    public static void set(Player player) { EATER.set(player); }
    public static Player get() { return EATER.get(); }
    public static void clear() { EATER.remove(); }
}
