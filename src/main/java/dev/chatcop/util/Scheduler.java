package dev.chatcop.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Scheduler abstraction that works on both Spigot/Paper and Folia.
 *
 * Folia removes the global main thread and the BukkitScheduler, replacing them
 * with region/async/entity schedulers. We detect Folia at runtime and route
 * each task accordingly via reflection, so the plugin still compiles against
 * plain spigot-api and runs unchanged on Spigot, Paper and Folia.
 */
public final class Scheduler {

    private Scheduler() {}

    private static final boolean FOLIA = detect();

    private static boolean detect() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFolia() { return FOLIA; }

    // ── Async work (HTTP, file I/O, periodic saves) ───────────────────────────

    public static void async(Plugin plugin, Runnable task) {
        if (FOLIA) {
            invoke(staticScheduler("getAsyncScheduler"), "runNow",
                    new Class[]{Plugin.class, Consumer.class}, plugin, consumer(task));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void asyncTimerSeconds(Plugin plugin, Runnable task, long initialDelaySec, long periodSec) {
        if (FOLIA) {
            invoke(staticScheduler("getAsyncScheduler"), "runAtFixedRate",
                    new Class[]{Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class},
                    plugin, consumer(task), initialDelaySec, periodSec, TimeUnit.SECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task,
                    initialDelaySec * 20L, periodSec * 20L);
        }
    }

    // ── Server-wide work (e.g. dispatching console commands) ──────────────────

    public static void global(Plugin plugin, Runnable task) {
        if (FOLIA) {
            invoke(staticScheduler("getGlobalRegionScheduler"), "run",
                    new Class[]{Plugin.class, Consumer.class}, plugin, consumer(task));
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void globalLater(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            invoke(staticScheduler("getGlobalRegionScheduler"), "runDelayed",
                    new Class[]{Plugin.class, Consumer.class, long.class},
                    plugin, consumer(task), Math.max(1L, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // ── Entity-affine work (e.g. playing a sound to a specific player) ────────

    public static void atEntity(Plugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            try {
                Object es = entity.getClass().getMethod("getScheduler").invoke(entity);
                es.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class)
                        .invoke(es, plugin, consumer(task), (Runnable) null);
            } catch (Exception e) {
                throw new RuntimeException("Folia entity scheduling failed", e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    // ── reflection helpers ────────────────────────────────────────────────────

    private static Object staticScheduler(String getter) {
        try {
            return Bukkit.class.getMethod(getter).invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Folia scheduler lookup failed: " + getter, e);
        }
    }

    private static void invoke(Object target, String method, Class<?>[] sig, Object... args) {
        try {
            target.getClass().getMethod(method, sig).invoke(target, args);
        } catch (Exception e) {
            throw new RuntimeException("Folia scheduling failed: " + method, e);
        }
    }

    private static Consumer<Object> consumer(Runnable task) {
        return ignored -> task.run();
    }
}
