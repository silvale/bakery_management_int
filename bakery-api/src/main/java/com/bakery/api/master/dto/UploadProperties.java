package com.bakery.api.master.dto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bakery.upload")
public class UploadProperties {

    /** Thư mục lưu file upload trên disk. */
    private String dir = "/var/bakery/uploads";

    /** Kích thước tối đa mỗi file (MB). */
    private int maxSizeMb = 5;

    /** Các định dạng được phép upload. */
    private List<String> allowedTypes = List.of("jpg", "jpeg", "png", "webp");
}
