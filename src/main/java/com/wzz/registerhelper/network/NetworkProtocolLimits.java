package com.wzz.registerhelper.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounds shared by the custom payload codecs.
 */
final class NetworkProtocolLimits {
    static final int MAX_RECIPE_ID_LENGTH = 256;
    static final int MAX_RECIPE_JSON_LENGTH = 262_144;
    static final int MAX_SOURCE_LENGTH = 128;
    static final int MAX_DESCRIPTION_LENGTH = 1_024;
    static final int MAX_BLACKLIST_BATCH_SIZE = 4_096;
    static final int RECIPE_BATCH_SIZE = 100;
    static final int MAX_RECIPE_BATCHES = 1_024;
    static final int MAX_TOTAL_RECIPES = RECIPE_BATCH_SIZE * MAX_RECIPE_BATCHES;

    private NetworkProtocolLimits() {
    }

    static StreamCodec<ByteBuf, String> string(int maxLength) {
        return ByteBufCodecs.stringUtf8(maxLength);
    }

    static <T> StreamCodec<ByteBuf, List<T>> list(StreamCodec<ByteBuf, T> elementCodec, int maxSize) {
        return new StreamCodec<>() {
            @Override
            public List<T> decode(ByteBuf buf) {
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                if (size < 0 || size > maxSize) {
                    throw new DecoderException("List size " + size + " exceeds the protocol limit of " + maxSize);
                }

                List<T> values = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    values.add(elementCodec.decode(buf));
                }
                return values;
            }

            @Override
            public void encode(ByteBuf buf, List<T> values) {
                if (values == null || values.size() > maxSize) {
                    throw new IllegalArgumentException("List size exceeds the protocol limit of " + maxSize);
                }

                ByteBufCodecs.VAR_INT.encode(buf, values.size());
                for (T value : values) {
                    elementCodec.encode(buf, value);
                }
            }
        };
    }

    static int decodeNonNegative(ByteBuf buf, String field, int maxValue) {
        int value = ByteBufCodecs.VAR_INT.decode(buf);
        if (value < 0 || value > maxValue) {
            throw new DecoderException(field + " is outside the allowed range: " + value);
        }
        return value;
    }

    static void encodeNonNegative(ByteBuf buf, String field, int value, int maxValue) {
        if (value < 0 || value > maxValue) {
            throw new IllegalArgumentException(field + " is outside the allowed range: " + value);
        }
        ByteBufCodecs.VAR_INT.encode(buf, value);
    }

    static void validateString(String value, String field, int maxLength) {
        if (value == null || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds the protocol limit of " + maxLength);
        }
    }
}
