package com.uade.tpo.marketplace.entity.dto.create;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record OrderCreateDto(
    @NotEmpty
    List<@NotNull @Valid OrderItemCreateDto> items,
    String notes
) {}