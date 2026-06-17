package com.ut.emrPacs.config;

import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.model.base.ResponseMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalTime;

@Aspect
@Component
public class ServiceImplErrorActivityLogAspect {

    private final ActivityLogService activityLogService;

    public ServiceImplErrorActivityLogAspect(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @Around(
            "execution(* com.ut.emrPacs.service.serviceImpl..*(..))" +
            " && !within(com.ut.emrPacs.service.serviceImpl.ActivityLogServiceImpl)" +
            " && !within(com.ut.emrPacs.config.ServiceImplErrorActivityLogAspect)"
    )
    public Object logUnhandledServiceErrors(ProceedingJoinPoint joinPoint) throws Throwable {
        LocalTime startDuration = LocalTime.now();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResponseMessage<?> response && !response.isSuccess()) {
                LocalTime endDuration = LocalTime.now();
                if (!ErrorReportingAttributes.isErrorActivityLogged(resolveHttpRequest())) {
                    safeInsertActivityLog(joinPoint, null, "Error", startDuration, endDuration);
                }
            }
            return result;
        } catch (Throwable error) {
            LocalTime endDuration = LocalTime.now();
            if (!ErrorReportingAttributes.isErrorActivityLogged(resolveHttpRequest())) {
                safeInsertActivityLog(joinPoint, error, "Exception", startDuration, endDuration);
            }
            throw error;
        }
    }

    private void safeInsertActivityLog(
            ProceedingJoinPoint joinPoint,
            Throwable error,
            String description,
            LocalTime startDuration,
            LocalTime endDuration
    ) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String className = signature.getDeclaringType().getSimpleName();
            String methodName = signature.getName();

            String moduleName = className.endsWith("ServiceImpl")
                    ? className.substring(0, className.length() - "ServiceImpl".length())
                    : className;
            String moduleId = moduleName + " (" + methodName + ")";
            String endpoint = resolveEndpoint(className, methodName);
            Long errorLine = resolveErrorLine(error);
            String bug = error != null ? error.toString() : "Service returned failed response.";

            activityLogService.insert(
                    endpoint,
                    errorLine,
                    bug,
                    moduleName,
                    moduleId,
                    methodName,
                    2,
                    description,
                    startDuration,
                    endDuration,
                    resolveHttpRequest()
            );
        } catch (Exception ignored) {
            // Never break business flow if audit log insert fails.
        }
    }

    private static Long resolveErrorLine(Throwable error) {
        if (error == null || error.getStackTrace() == null) {
            return null;
        }
        for (StackTraceElement element : error.getStackTrace()) {
            if (element != null && element.getClassName() != null
                    && element.getClassName().startsWith("com.ut.emrPacs.service.serviceImpl")) {
                return (long) element.getLineNumber();
            }
        }
        return error.getStackTrace().length > 0 ? (long) error.getStackTrace()[0].getLineNumber() : null;
    }

    private static String resolveEndpoint(String className, String methodName) {
        HttpServletRequest request = resolveHttpRequest();
        if (request != null && request.getRequestURI() != null && !request.getRequestURI().isBlank()) {
            return request.getRequestURI();
        }
        return "/internal/" + className + "/" + methodName;
    }

    private static HttpServletRequest resolveHttpRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
