package com.zenith.event.chat;

import java.util.Optional;

import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record ChatUser(
    String name,
    String decoratedName,
    PlayerListEntry player
) 
{ 
    public boolean isResolved()
    {
        return this.player != null;
    }
}
