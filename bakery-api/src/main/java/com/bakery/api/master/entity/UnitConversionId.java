package com.bakery.api.master.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite PK cho UnitConversion. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UnitConversionId implements Serializable {
    private String fromUnit;
    private String toUnit;
}
