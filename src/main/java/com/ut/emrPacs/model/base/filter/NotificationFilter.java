package com.ut.emrPacs.model.base.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class NotificationFilter extends Filter {

    private String source;

    private List<String> sources;

    private List<String> notificationIds;

    private String severity;

    private Integer days;
}
