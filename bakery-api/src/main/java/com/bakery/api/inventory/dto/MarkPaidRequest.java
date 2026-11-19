package com.bakery.api.inventory.dto;

import java.util.List;
import java.util.UUID;

public record MarkPaidRequest(List<UUID> ids) {}
