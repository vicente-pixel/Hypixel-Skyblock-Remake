package net.swofty.type.skyblockgeneric.trading;

import lombok.Getter;
import lombok.Setter;
import net.minestom.server.timer.Task;

import java.util.UUID;

@Getter
public class TradeRequest {
    private final UUID sender;
    private final UUID target;
    @Setter
    private Task expiryTask;

    public TradeRequest(UUID sender, UUID target) {
        this.sender = sender;
        this.target = target;
    }

    public void cancelExpiry() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
    }
}
