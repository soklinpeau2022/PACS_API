package com.ut.emrPacs.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@Configuration
@EnableTransactionManagement
public class TransactionManagementConfig {

    @Bean
    public Advisor serviceImplTransactionAdvisor(PlatformTransactionManager transactionManager) {
        RuleBasedTransactionAttribute readOnlyTx = new RuleBasedTransactionAttribute();
        readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
        readOnlyTx.setReadOnly(true);
        readOnlyTx.setRollbackRules(List.of(new RollbackRuleAttribute(Exception.class)));

        RuleBasedTransactionAttribute writeTx = new RuleBasedTransactionAttribute();
        writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        writeTx.setReadOnly(false);
        writeTx.setRollbackRules(List.of(new RollbackRuleAttribute(Exception.class)));

        NameMatchTransactionAttributeSource txAttributeSource = new NameMatchTransactionAttributeSource();
        Map<String, TransactionAttribute> txMap = new HashMap<>();

        txMap.put("list*", readOnlyTx);
        txMap.put("get*", readOnlyTx);
        txMap.put("find*", readOnlyTx);
        txMap.put("view*", readOnlyTx);
        txMap.put("me*", readOnlyTx);
        txMap.put("validate*", readOnlyTx);
        txMap.put("count*", readOnlyTx);

        txMap.put("*", writeTx);
        txAttributeSource.setNameMap(txMap);

        TransactionInterceptor txInterceptor = new TransactionInterceptor();
        txInterceptor.setTransactionManager(transactionManager);
        txInterceptor.setTransactionAttributeSource(txAttributeSource);
        txInterceptor.afterPropertiesSet();

        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(
                "execution(* com.ut.emrPacs.service.serviceImpl..*(..))" +
                " && !within(com.ut.emrPacs.service.serviceImpl.ActivityLogServiceImpl)" +
                " && !within(com.ut.emrPacs.service.serviceImpl.DicomServerClientServiceImpl)" +
                " && !within(com.ut.emrPacs.service.serviceImpl.PacsResultSyncServiceImpl)"
        );

        return new DefaultPointcutAdvisor(pointcut, txInterceptor);
    }
}
