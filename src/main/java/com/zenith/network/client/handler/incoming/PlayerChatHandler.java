package com.zenith.network.client.handler.incoming;

import com.zenith.event.chat.*;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandler;
import com.zenith.util.ComponentSerializer;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;

import java.util.Optional;

import static com.zenith.Globals.*;

// XXX: CHANGES NOT TESTED
public class PlayerChatHandler implements PacketHandler<ClientboundPlayerChatPacket, ClientSession> {

    @Override
    public ClientboundPlayerChatPacket apply(ClientboundPlayerChatPacket packet, ClientSession session) {
        var senderPlayerEntry = CACHE.getTabListCache().get(packet.getSender());
        var chatType = CACHE.getChatCache().getChatTypeRegistry().getChatType(packet.getChatType().id());
        if (chatType != null) {
            Component chatComponent = chatType.render(
                packet.getName(),
                Component.text(packet.getContent()),
                packet.getUnsignedContent(),
                packet.getTargetName());
            if (CONFIG.client.extra.logChatMessages) {
                CLIENT_LOG.info(chatComponent);
            }
            boolean isWhisper = false;
            Optional<PlayerListEntry> whisperTarget = Optional.empty();
            if ("commands.message.display.incoming".equals(chatType.translationKey())) {
                isWhisper = true;
                whisperTarget = CACHE.getTabListCache().get(CACHE.getProfileCache().getProfile().getId());
            } else if ("commands.message.display.outgoing".equals(chatType.translationKey())) {
                isWhisper = true;
                whisperTarget = CACHE.getTabListCache().getFromName( // ???
                     ComponentSerializer.serializePlain(packet.getTargetName())
                );
            }

            String messageContent = ComponentSerializer.serializePlain(chatComponent);

            if (isWhisper) {
                if (senderPlayerEntry.isEmpty()) {
                    CLIENT_LOG.warn("No sender found for PlayerChatPacket whisper. chatType: {}, content: {}", chatType.translationKey(), messageContent);
                } else if (whisperTarget.isEmpty()) {
                    CLIENT_LOG.warn("No whisper target found for PlayerChatPacket whisper. chatType: {}, content: {}", chatType.translationKey(), messageContent);
                } else {
                    boolean outgoing = "commands.message.display.outgoing".equals(chatType.translationKey());
                    EVENT_BUS.postAsync(new ChatEvent(
                        MessageType.WHISPER,
                        new ChatUser(senderPlayerEntry.get().getName(), ComponentSerializer.serializePlain(senderPlayerEntry.get().getDisplayName()), senderPlayerEntry.get()),
                        new ChatUser(whisperTarget.get().getName(), ComponentSerializer.serializePlain(whisperTarget.get().getDisplayName()), whisperTarget.get()),
                        chatComponent,
                        messageContent,
                        messageContent
                    ));
                }
            } else {
                if (senderPlayerEntry.isEmpty()) {
                    CLIENT_LOG.warn("No sender found for PlayerChatPacket public chat. chatType: {}, content: {}", chatType.translationKey(), messageContent);
                } else {
                    EVENT_BUS.postAsync(new ChatEvent(
                        MessageType.PUBLIC,
                        new ChatUser(senderPlayerEntry.get().getName(), ComponentSerializer.serializePlain(senderPlayerEntry.get().getDisplayName()), senderPlayerEntry.get()),
                        new ChatUser(null, null, null),
                        chatComponent,
                        messageContent,
                        messageContent
                    ));
                }
            }
        } else {
            CLIENT_LOG.warn("Unknown chat type: {}", packet.getChatType().id());
        }
        return packet;
    }
}
