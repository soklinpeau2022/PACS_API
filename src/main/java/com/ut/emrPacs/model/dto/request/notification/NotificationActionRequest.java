package com.ut.emrPacs.model.dto.request.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NotificationActionRequest {

    @Size(max = 200)
    private List<String> notificationIds;

    @JsonIgnore
    private Long userId;

    @JsonIgnore
    private Long hospitalId;

    @JsonIgnore
    private Integer days;
}
