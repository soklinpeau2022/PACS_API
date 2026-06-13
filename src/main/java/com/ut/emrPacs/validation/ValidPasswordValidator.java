package com.ut.emrPacs.validation;

import com.ut.emrPacs.authentication.util.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String error = PasswordPolicy.validate(value);
        if (error == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        return false;
    }
}
