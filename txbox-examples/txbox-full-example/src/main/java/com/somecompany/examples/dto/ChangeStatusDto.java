package com.somecompany.examples.dto;

import java.util.UUID;

public record ChangeStatusDto(UUID id,
                              OrderStatus status) {
}
