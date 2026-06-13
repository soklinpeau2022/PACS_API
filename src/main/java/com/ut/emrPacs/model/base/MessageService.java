package com.ut.emrPacs.model.base;


import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class MessageService {

    public BaseResult message(String text, List<?> array, Boolean status) {
        BaseResult baseResult = new BaseResult();
        baseResult.setMessage(text);
        baseResult.setStatus(status);
        baseResult.setData(array);
        return baseResult;
    }

    public BaseResult message(String text, List<?> array, Pagination pagination, Boolean status) {
        BaseResult baseResult = new BaseResult();
        baseResult.setMessage(text);
        baseResult.setStatus(status);
        baseResult.setData(array);
        baseResult.setPagination(pagination);
        return baseResult;
    }

    public BaseResult message(String text, Boolean status) {
        BaseResult baseResult = new BaseResult();
        baseResult.setMessage(text);
        baseResult.setStatus(status);
        return baseResult;
    }

    public BaseResult message(String text, Boolean status, Long id) {
        BaseResult baseResult = new BaseResult();
        baseResult.setMessage(text);
        baseResult.setStatus(status);
        baseResult.setId(id);
        return baseResult;
    }

}