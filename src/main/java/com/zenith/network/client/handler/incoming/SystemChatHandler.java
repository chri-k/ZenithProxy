package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.event.chat.*;
import com.zenith.event.queue.QueueSkipEvent;
import com.zenith.event.server.ClientDeathMessageEvent;
import com.zenith.feature.deathmessages.DeathMessageParseResult;
import com.zenith.feature.deathmessages.DeathMessagesParser;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import com.zenith.util.ComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

import static com.zenith.Globals.*;
import static java.util.Objects.nonNull;

public class SystemChatHandler implements ClientEventLoopPacketHandler<ClientboundSystemChatPacket, ClientSession> {
    private static final TextColor DEATH_MSG_COLOR_2b2t = TextColor.color(170, 0, 0);
    private final DeathMessagesParser deathMessagesHelper = new DeathMessagesParser();

    @Override
    public boolean applyAsync(@NonNull ClientboundSystemChatPacket packet, @NonNull ClientSession session) {
        try {

            final boolean essentialsChat = CONFIG.client.extra.chat.essentialsFormatting;

            MessageType ev = MessageType.OTHER;

            if (CONFIG.client.extra.logChatMessages) {
                var component = packet.getContent();
                if (Proxy.getInstance().isInQueue()) {
                    component = component.replaceText(b -> b
                        .matchLiteral("\n\n")
                        .replacement("")
                    );
                }
                CHAT_LOG.info(component);
            }

            final Component component = packet.getContent();
            final String messageString = ComponentSerializer.serializePlain(component);
            Optional<DeathMessageParseResult> deathMessage = Optional.empty();

            String decoratedSenderName = null;
            String decoratedTargetName = null;

            String senderName = null;
            String targetName = null;
            String messageContent = null;

            if (!messageString.startsWith("<") && Proxy.getInstance().isOn2b2t())
                deathMessage = parseDeathMessage2b2t(component, deathMessage, messageString);
            if (ev == MessageType.OTHER && messageString.startsWith("<")) {
                decoratedSenderName = extractSenderNameNormalChat(messageString);
                messageContent = extractMessageContentNormalChat(messageString);
                ev = MessageType.PUBLIC;
            } 

            if (ev == MessageType.OTHER && deathMessage.isPresent()) ev = MessageType.DEATH;

            else if (ev == MessageType.OTHER) {
                if (essentialsChat && messageString.startsWith("["))
                {
                    Integer end = findMatchingBracket(messageString, 0);

                    // [$senderName -> me] $messageText
                    // [me -> $whisperTarget] $messageText
                    final String inner = messageString.substring(1, end);
                    if (inner.endsWith(" -> me")) 
                    {
                        decoratedSenderName = inner.substring(0, inner.length() - 6);
                        targetName = CONFIG.authentication.username;
                        messageContent = messageString.substring(end + 1);
                        ev = MessageType.WHISPER;
                    }
                    else if (inner.startsWith("me -> "))
                    {
                        senderName = CONFIG.authentication.username;
                        decoratedTargetName = inner.substring(6);
                        messageContent = messageString.substring(end + 1);
                        ev = MessageType.WHISPER;
                    }
                }
                else
                { 
                    final String[] split = messageString.split(" ");
                    if (split.length > 2) {
                        if (split[1].startsWith("whispers")) {
                            decoratedSenderName = extractSenderNameReceivedWhisper(split);
                            targetName = CONFIG.authentication.username;
                            ev = MessageType.WHISPER;
                        } else if (messageString.startsWith("to ")) {
                            senderName = CONFIG.authentication.username;
                            decoratedTargetName = extractReceiverNameSentWhisper(split);
                            ev = MessageType.WHISPER;
                        }
                    }
                }
            }

            if (ev == MessageType.OTHER) ev = MessageType.SYSTEM;

            // Try to strip any ranks or other decoration from the names
            if (decoratedSenderName != null && senderName == null)
            {
                final String[] split = decoratedSenderName.split(" ");
                senderName = split[split.length - 1];
            }
            if (decoratedTargetName != null && targetName == null)
            {
                final String[] split = decoratedTargetName.split(" ");
                targetName = split[split.length - 1];
            }

            var sourcePlayer = Optional.ofNullable(senderName).flatMap(t -> CACHE.getTabListCache().getFromName(t));
            var targetPlayer = Optional.ofNullable(targetName).flatMap(t -> CACHE.getTabListCache().getFromName(t));

            if (decoratedTargetName == null) decoratedTargetName = targetName;
            if (decoratedSenderName == null) decoratedSenderName = senderName;

            // The above attempt at getting the player name failed. Try to match the full display name against a display name in tab instead.
            if (sourcePlayer.isEmpty() && decoratedSenderName != null)
            {
                sourcePlayer = Optional.ofNullable(decoratedSenderName).flatMap(t -> CACHE.getTabListCache().getFromDisplayName(t));
            }
            if (targetPlayer.isEmpty() && decoratedTargetName != null)
            {
                targetPlayer = Optional.ofNullable(decoratedTargetName).flatMap(t -> CACHE.getTabListCache().getFromDisplayName(t));
            }

            // Try to match the clipped display name against a display name in tab too.
            if (sourcePlayer.isEmpty() && senderName != null)
            {
                sourcePlayer = Optional.ofNullable(senderName).flatMap(t -> CACHE.getTabListCache().getFromDisplayName(t));
            }
            if (targetPlayer.isEmpty() && targetName != null)
            {
                targetPlayer = Optional.ofNullable(targetName).flatMap(t -> CACHE.getTabListCache().getFromDisplayName(t));
            }

            var sourceUser = new ChatUser(senderName, decoratedSenderName, sourcePlayer.isPresent() ? sourcePlayer.get() : null);
            var targetUser = new ChatUser(targetName, decoratedTargetName, targetPlayer.isPresent() ? targetPlayer.get() : null);
            
            if (Proxy.getInstance().isOn2b2t()
                && "Reconnecting to server 2b2t.".equals(messageString)
                && NamedTextColor.GOLD.equals(component.style().color())) {
                CLIENT_LOG.info("Queue Skip Detected");
                EVENT_BUS.postAsync(QueueSkipEvent.INSTANCE);
            }

            if (ev == MessageType.DEATH)
            {
                EVENT_BUS.postAsync(new DeathMessageChatEvent(deathMessage.get(), component, messageString));
            } else 
            {
                EVENT_BUS.postAsync(new ChatEvent(ev, sourceUser, targetUser, component, messageString, messageContent));
            }
        } catch (final Exception e) {
            CLIENT_LOG.error("Caught exception in ChatHandler. Packet: {}", packet, e);
        }
        return true;
    }

    private Optional<DeathMessageParseResult> parseDeathMessage2b2t(final Component component, Optional<DeathMessageParseResult> deathMessage, final String messageString) {
        if (component.children().stream().anyMatch(child -> nonNull(child.color())
            && Objects.equals(child.color(), DEATH_MSG_COLOR_2b2t))) { // death message color on 2b
            deathMessage = deathMessagesHelper.parse(component, messageString);
            if (deathMessage.isPresent()) {
                if (deathMessage.get().victim().equals(CACHE.getProfileCache().getProfile().getName())) {
                    EVENT_BUS.postAsync(new ClientDeathMessageEvent(messageString));
                }
            } else {
                CLIENT_LOG.warn("Failed to parse death message: {}", messageString);
            }
        }
        return deathMessage;
    }

    private String extractSenderNameNormalChat(final String message) {
        return message.substring(message.indexOf("<") + 1, message.indexOf(">"));
    }

    private String extractMessageContentNormalChat(final String message) {
        return message.substring(message.indexOf(">") + 1).trim();
    }

    private String extractSenderNameReceivedWhisper(final String[] messageSplit) {
        return messageSplit[0].trim();
    }

    private String extractReceiverNameSentWhisper(final String[] messageSplit) {
        return messageSplit[1].replace(":", "");
    }

    private Integer findMatchingBracket(final String _str, Integer index) {
        final char[] str = _str.toCharArray();
        Integer level = 0;
        for (Integer i = index; i < _str.length(); i++)
        {
            if (str[i] == '[') level++;
            if (str[i] == ']' && --level == 0)
            {
                return i;
            }
        }
        return index;
    }
}
