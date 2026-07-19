package com.bakery.framework.dto;

import java.util.Date;

/**
 * Wrapper DTO cho mỗi revision trong lịch sử thay đổi.
 *
 * @param data         snapshot entity tại thời điểm này (đã map sang RES)
 * @param revision     global revision number (Envers)
 * @param revisionDate thời điểm thay đổi
 * @param revisionType ADD | MOD | DEL
 * @param versionNumber thứ tự phiên bản theo entity (1, 2, 3…)
 * @param actor        người thực hiện
 */
public record RevisionResponse<RES>(
        RES data,
        Number revision,
        Date revisionDate,
        String revisionType,
        Integer versionNumber,
        String actor) {}
