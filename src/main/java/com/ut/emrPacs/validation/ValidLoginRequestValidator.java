package com.ut.emrPacs.validation;

import com.ut.emrPacs.model.dto.request.authentication.login.LoginRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidLoginRequestValidator implements ConstraintValidator<ValidLoginRequest, LoginRequest> {

    @Override
    public boolean isValid(LoginRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        boolean hasUsername = value.getUsername() != null && !value.getUsername().isBlank();
        if (hasUsername) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Username is required.")
                .addPropertyNode("username")
                .addConstraintViolation();
        return false;
    }
}
