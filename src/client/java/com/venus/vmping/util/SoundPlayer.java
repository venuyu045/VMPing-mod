package com.venus.vmping.util;

import com.venus.vmping.config.ModConfig;
import com.venus.vmping.core.PingKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * 提示音播放。
 *
 * <p>v0.1.0 先用原版音效（{@code NOTE_BLOCK_PLING}）区分"自己标点"和"收到别人的标点"：
 * 自己标点音调高一点、别人的低一点，避免连续收到多个标点时糊成一片。
 * 自定义音效（{@code assets/vmping/sounds/ping.ogg}）在 M6 打磨阶段替换，接口不变。
 *
 * <p>为什么不在渲染线程直接播：声音播放本身是线程安全的（SoundManager 内部队列），
 * 但为了不阻塞渲染，仍然走 {@link Minecraft#execute}。
 */
public final class SoundPlayer {

    private SoundPlayer() {
    }

    /** 自己标点：清脆的高音。 */
    public static void playOwnPing(PingKind kind) {
        play(kind, 1.0f, 1.6f);
    }

    /** 收到别人的标点：略低的提示音，音量随配置。 */
    public static void playRemotePing(PingKind kind) {
        play(kind, 0.8f, 1.0f);
    }

    /** 打开轮盘：轻响一声。 */
    public static void playWheelOpen() {
        play(PingKind.DEFAULT, 0.35f, 2.0f);
    }

    private static void play(PingKind kind, float volumeScale, float pitch) {
        ModConfig config = ModConfig.get();
        if (!config.playSound || config.soundVolume <= 0f) {
            return;
        }
        float volume = Math.max(0f, Math.min(1f, config.soundVolume * volumeScale));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            try {
                // 危险标点用低音提醒，其它类型用 pling
                // NOTE_BLOCK_* 在 26.3 是 Holder.Reference，取 value() 拿到 SoundEvent 才能带 pitch
                var sound = (kind == PingKind.DANGER
                        ? SoundEvents.NOTE_BLOCK_BASS
                        : SoundEvents.NOTE_BLOCK_PLING).value();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
            } catch (Throwable t) {
                // 声音播不出来不影响功能
            }
        });
    }
}
