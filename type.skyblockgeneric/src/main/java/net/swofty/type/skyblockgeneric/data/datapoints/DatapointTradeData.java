package net.swofty.type.skyblockgeneric.data.datapoints;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.swofty.commons.protocol.Serializer;
import net.swofty.type.skyblockgeneric.data.SkyBlockDatapoint;
import org.json.JSONObject;

public class DatapointTradeData extends SkyBlockDatapoint<DatapointTradeData.TradeData> {
    private static final Serializer<TradeData> serializer = new Serializer<>() {
        @Override
        public String serialize(TradeData value) {
            JSONObject json = new JSONObject();
            json.put("coinsTradedToday", value.coinsTradedToday);
            json.put("lastTradeDay", value.lastTradeDay);
            return json.toString();
        }

        @Override
        public TradeData deserialize(String json) {
            JSONObject jsonObject = new JSONObject(json);
            return new TradeData(
                    jsonObject.optLong("coinsTradedToday", 0L),
                    jsonObject.optLong("lastTradeDay", 0L)
            );
        }

        @Override
        public TradeData clone(TradeData value) {
            return new TradeData(value.coinsTradedToday, value.lastTradeDay);
        }
    };

    public DatapointTradeData(String key) {
        super(key, new TradeData(0L, 0L), serializer);
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class TradeData {
        private long coinsTradedToday;
        private long lastTradeDay;

        public void resetIfNeeded() {
            long currentDay = System.currentTimeMillis() / 86_400_000L;
            if (lastTradeDay != currentDay) {
                coinsTradedToday = 0L;
                lastTradeDay = currentDay;
            }
        }

        public void addCoinsTraded(long amount) {
            resetIfNeeded();
            coinsTradedToday += Math.max(0L, amount);
        }
    }
}
