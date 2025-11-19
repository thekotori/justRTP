package eu.kotori.justRTP.events;

import eu.kotori.justRTP.utils.RTPZone;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * API Documentation can be found on https://kotori.ink/wiki/justrtp/api
 * Called when a player leaves an RTP Zone.
 * This event is NOT cancellable.
 * 
 * <p>Use this event to:</p>
 * <ul>
 *   <li>Track zone departures</li>
 *   <li>Clean up player data</li>
 *   <li>Trigger custom effects on zone exit</li>
 *   <li>Log zone participation time</li>
 * </ul>
 * 
 * @see PlayerRTPZoneEnterEvent for zone entry handling (cancellable)
 */
public class PlayerRTPZoneLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player player;
    private final RTPZone zone;
    private final LeaveReason reason;
    private final int playersInZone;
    
    /**
     * Reasons why a player might leave a zone.
     */
    public enum LeaveReason {
        /** Player physically moved out of the zone boundaries */
        MOVED_OUT,
        /** Player was teleported by the zone countdown */
        TELEPORTED,
        /** Player disconnected while in the zone */
        DISCONNECTED,
        /** Player used /rtpzone ignore or similar command */
        COMMAND,
        /** Plugin was disabled/reloaded */
        PLUGIN_DISABLE,
        /** Unknown or other reason */
        OTHER
    }
    
    /**
     * Constructs a new PlayerRTPZoneLeaveEvent.
     *
     * @param player The player leaving the zone
     * @param zone The RTP zone being left
     * @param reason The reason for leaving
     * @param playersInZone The number of players remaining in the zone (after this player left)
     */
    public PlayerRTPZoneLeaveEvent(@NotNull Player player, @NotNull RTPZone zone, 
                                  @NotNull LeaveReason reason, int playersInZone) {
        this.player = player;
        this.zone = zone;
        this.reason = reason;
        this.playersInZone = playersInZone;
    }
    
    /**
     * Gets the player who left the zone.
     *
     * @return The player
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }
    
    /**
     * Gets the RTP zone that was left.
     *
     * @return The zone
     */
    @NotNull
    public RTPZone getZone() {
        return zone;
    }
    
    /**
     * Gets the zone ID.
     *
     * @return The zone identifier
     */
    @NotNull
    public String getZoneId() {
        return zone.getId();
    }
    
    /**
     * Gets the reason why the player left the zone.
     *
     * @return The leave reason
     */
    @NotNull
    public LeaveReason getReason() {
        return reason;
    }
    
    /**
     * Gets the number of players remaining in the zone.
     * This count is after the player has already left.
     *
     * @return The remaining player count
     */
    public int getPlayersInZone() {
        return playersInZone;
    }
    
    /**
     * Checks if the player left because they were teleported.
     *
     * @return true if teleported, false otherwise
     */
    public boolean wasTeleported() {
        return reason == LeaveReason.TELEPORTED;
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
