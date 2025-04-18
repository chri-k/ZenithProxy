package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.zenith.Proxy;
import com.zenith.event.chat.*;
import com.zenith.module.api.Module;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static java.util.Objects.isNull;

// XXX: CHANGES NOT TESTED
public class AutoReply extends Module {
    private Cache<String, String> repliedPlayersCache = CacheBuilder.newBuilder()
            .expireAfterWrite(CONFIG.client.extra.autoReply.cooldownSeconds, TimeUnit.SECONDS)
            .build();
    private Instant lastReply = Instant.now();

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ChatEvent.class, this::handleChatEvent)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.autoReply.enabled;
    }

    public void updateCooldown(final int newCooldown) {
        CONFIG.client.extra.autoReply.cooldownSeconds = newCooldown;
        Cache<String, String> newCache = CacheBuilder.newBuilder()
                .expireAfterWrite(newCooldown, TimeUnit.SECONDS)
                .build();
        newCache.putAll(this.repliedPlayersCache.asMap());
        this.repliedPlayersCache = newCache;
    }

    private void handleChatEvent(ChatEvent event) {
        if (event.type() != MessageType.WHISPER) return;
        if (Proxy.getInstance().hasActivePlayer()) return;

        try {

        var sender = event.source();
        var target = event.target();

        boolean senderResolved = sender.isResolved();

        if (!sender.isResolved()) return;
        if (!target.isResolved()) return;

        String senderName = sender.player().getName();
        String targetName = target.player().getName();

        if (senderName.equalsIgnoreCase(CONFIG.authentication.username)) return;
        if (!targetName.equalsIgnoreCase(CONFIG.authentication.username)) return;

        if (Instant.now().minus(Duration.ofSeconds(1)).isAfter(lastReply)
            && (DISCORD.lastRelayMessage.isEmpty()
            || Instant.now().minus(Duration.ofSeconds(CONFIG.client.extra.autoReply.cooldownSeconds)).isAfter(DISCORD.lastRelayMessage.get()))) {
            if (isNull(repliedPlayersCache.getIfPresent(senderName))) {
                repliedPlayersCache.put(senderName, senderName);
                // 236 char max ( 256 - 4(command) - 16(max name length) )
                sendClientPacketAsync(new ServerboundChatPacket("/w " + senderName + " " + CONFIG.client.extra.autoReply.message.substring(0, Math.min(CONFIG.client.extra.autoReply.message.length(), 236))));
                this.lastReply = Instant.now();
            }
        }

        } catch (final Throwable e) {
            CLIENT_LOG.error("AutoReply Failed", e);
        }
    }
}
