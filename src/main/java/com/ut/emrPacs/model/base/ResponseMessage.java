package com.ut.emrPacs.model.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"header", "success", "body"})
@Data
public class ResponseMessage<T> {

    private ResponseHeader header;

    private boolean success;

    private T body;

    public boolean success() {
        return header != null && header.getResult();
    }

    public boolean isSuccess() {
        return header != null && header.getResult();
    }

    public ResponseHeader getHeader() {
        if (header == null) {
            header = new ResponseHeader();
        }
        return header;
    }

}
