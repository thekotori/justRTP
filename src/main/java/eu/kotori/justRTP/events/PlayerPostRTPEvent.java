package eu.kotori.justRTP.events;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * API Documentation can be found on https://kotori.ink/wiki/justrtp/api
 * Called after a player has been successfully teleported via RTP.
 * This event is NOT cancellable - the teleport has already occurred.
 * 
 * <p>Use this event to:</p>
 * <ul>
 *   <li>Track successful teleports</li>
 *   <li>Reward players for exploring</li>
 *   <li>Log teleport destinations</li>
 *   <li>Trigger custom effects or actions after teleport</li>
 * </ul>
 * 
 * @see PlayerRTPEvent for pre-teleport handling (cancellable)
 */
public class PlayerPostRTPEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player player;
    private final Location from;
    private final Location to;
    private final World targetWorld;
    private final Integer minRadius;
    private final Integer maxRadius;
    private final double cost;
    private final boolean isCrossServer;
    private final String targetServer;
    
    /**
     * Constructs a new PlayerPostRTPEvent.
     * @param player The player who was teleported
     * @param from The location the player teleported from
     * @param to The location the player teleported to
     * @param targetWorld The target world of the teleport
     * @param minRadius Minimum radius used (null if default)
     * @param maxRadius Maximum radius used (null if default)
     * @param cost The economy cost of the teleport
     * @param isCrossServer Whether this was a cross-server teleport
     * @param targetServer The target server name (null if local teleport)
     */
    public PlayerPostRTPEvent(@NotNull Player player, @NotNull Location from, @NotNull Location to,
                             @NotNull World targetWorld, @Nullable Integer minRadius, 
                             @Nullable Integer maxRadius, double cost, boolean isCrossServer,
                             @Nullable String targetServer) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.targetWorld = targetWorld;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.cost = cost;
        this.isCrossServer = isCrossServer;
        this.targetServer = targetServer;
    }
    
    /**
     * Gets the player who was teleported.
     *
     * @return The player
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }
    
    /**
     * Gets the location the player teleported from.
     *
     * @return The origin location
     */
    @NotNull
    public Location getFrom() {
        return from;
    }
    
    /**
     * Gets the location the player teleported to.
     *
     * @return The destination location
     */
    @NotNull
    public Location getTo() {
        return to;
    }
    
    /**
     * Gets the target world of the teleport.
     *
     * @return The target world
     */
    @NotNull
    public World getTargetWorld() {
        return targetWorld;
    }
    
    /**
     * Gets the minimum radius used for the teleport.
     *
     * @return The minimum radius, or null if default was used
     */
    @Nullable
    public Integer getMinRadius() {
        return minRadius;
    }
    
    /**
     * Gets the maximum radius used for the teleport.
     *
     * @return The maximum radius, or null if default was used
     */
    @Nullable
    public Integer getMaxRadius() {
        return maxRadius;
    }
    
    /**
     * Gets the economy cost that was charged for the teleport.
     *
     * @return The cost (0 if economy disabled or player had bypass)
     */
    public double getCost() {
        return cost;
    }
    
    /**
     * Checks if this was a cross-server teleport.
     *
     * @return true if cross-server, false if local
     */
    public boolean isCrossServer() {
        return isCrossServer;
    }
    
    /**
     * Gets the target server name for cross-server teleports.
     *
     * @return The server name, or null if local teleport
     */
    @Nullable
    public String getTargetServer() {
        return targetServer;
    }
    
    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
