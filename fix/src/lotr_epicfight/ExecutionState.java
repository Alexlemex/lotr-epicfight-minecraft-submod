package lotr_epicfight;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Executions in progress, per player (shared by the execution mixins). */
public final class ExecutionState {
    /** Hard cap on the invulnerability window, in ticks, in case an execution is interrupted. */
    public static final long MAX_INVULNERABLE_TICKS = 60L;

    public record Execution(LivingEntity victim, Object animation, long startTick) {}

    private static final Map<Player, Execution> EXECUTIONS = new WeakHashMap<>();

    private ExecutionState() {}

    public static synchronized void start(Player player, LivingEntity victim, Object animation) {
        EXECUTIONS.put(player, new Execution(victim, animation, player.level().getGameTime()));
    }

    public static synchronized Execution get(Player player) { return EXECUTIONS.get(player); }

    public static synchronized Execution take(Player player) { return EXECUTIONS.remove(player); }

    public static synchronized boolean isInvulnerable(Player player) {
        Execution e = EXECUTIONS.get(player);
        if (e == null) return false;
        if (player.level().getGameTime() - e.startTick() > MAX_INVULNERABLE_TICKS) {
            EXECUTIONS.remove(player);
            return false;
        }
        return true;
    }

    /** Kills the victim (credited to the player) if it survived the finishing animation. */
    public static void finish(Player player, LivingEntity victim) {
        if (victim != null && victim.isAlive()) {
            victim.invulnerableTime = 0;
            victim.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
            if (victim.isAlive()) victim.kill();
        }
    }
}
