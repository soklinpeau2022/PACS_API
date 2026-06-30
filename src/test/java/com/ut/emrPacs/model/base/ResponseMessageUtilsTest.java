package com.ut.emrPacs.model.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseMessageUtilsTest {

    @Test
    void clientSafeValidationMessageShouldRemainVisible() {
        String message = "Multiple active machine routes are configured for this modality. Please choose the target machine before sending to PACS.";
        BaseResult result = new BaseResult();
        result.setMessage(message);
        result.setStatus(false);

        ResponseMessage<BaseResult> response = ResponseMessageUtils.makeResponse(false, result);

        assertEquals(message, response.getHeader().getErrorText());
    }
}
