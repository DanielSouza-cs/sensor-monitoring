package com.sensormonitoring.warehouse.config;

import com.sensormonitoring.contract.SensorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("warehouse")
public record WarehouseProperties(
        @NotBlank String id,
        @NotEmpty List<@Valid Channel> channels,
        @Positive int bufferCapacity,
        @NotNull @Valid Publish publish) {

    public record Channel(@NotNull SensorType type, @PositiveOrZero @Max(65535) int port) {
    }

    public record Publish(@NotNull Duration confirmTimeout, @PositiveOrZero int maxRetries) {
    }
}
