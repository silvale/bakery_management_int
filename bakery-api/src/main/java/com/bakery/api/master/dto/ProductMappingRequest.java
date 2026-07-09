package com.bakery.api.master.dto;

import java.util.UUID;

public record ProductMappingRequest(UUID itemId, String exCode, Integer productionDay, String note) {}
