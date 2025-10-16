package eu.kotori.justRTP.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CooldownManager {
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public void setCooldown(UUID uuid, int seconds) {
        if (seconds <= 0) {
            cooldowns.remove(uuid);
            return;
        }
        cooldowns.put(uuid, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds));
    }

    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }

    public long getRemaining(UUID uuid) {
        Long expiryTime = cooldowns.get(uuid);
        if (expiryTime == null || expiryTime <= System.currentTimeMillis()) {
            return 0;
        }
        return TimeUnit.MILLISECONDS.toSeconds(expiryTime - System.currentTimeMillis());
    }
}