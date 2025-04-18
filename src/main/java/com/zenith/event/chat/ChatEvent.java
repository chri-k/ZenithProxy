package com.zenith.event.chat;

import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record ChatEvent(
    MessageType type,
    ChatUser source,
    ChatUser target,
    Component component,
    String message,
    String messageContent
) { }
