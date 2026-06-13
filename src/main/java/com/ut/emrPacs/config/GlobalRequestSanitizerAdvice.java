package com.ut.emrPacs.config;

import com.ut.emrPacs.helper.security.SecurityInputSanitizerHelper;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;

@ControllerAdvice
public class GlobalRequestSanitizerAdvice {

    @InitBinder
    public void sanitizeStringInputs(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(SecurityInputSanitizerHelper.sanitize(text));
            }

            @Override
            public void setValue(Object value) {
                if (value instanceof String stringValue) {
                    super.setValue(SecurityInputSanitizerHelper.sanitize(stringValue));
                    return;
                }
                super.setValue(value);
            }
        });
    }
}
