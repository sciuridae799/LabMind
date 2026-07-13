package com.labmind.common.frame.util;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

public class SpringUtil implements ApplicationContextAware, DisposableBean {

    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringUtil.applicationContext = applicationContext;
    }

    @Override
    public void destroy() {
        applicationContext = null;
    }

    public static ApplicationContext getApplicationContext() {
        return requireApplicationContext();
    }

    public static <T> T getBean(Class<T> beanType) {
        Assert.notNull(beanType, "beanType must not be null");
        return requireApplicationContext().getBean(beanType);
    }

    public static Object getBean(String beanName) {
        Assert.hasText(beanName, "beanName must not be blank");
        return requireApplicationContext().getBean(beanName);
    }

    public static <T> T getBean(String beanName, Class<T> beanType) {
        Assert.hasText(beanName, "beanName must not be blank");
        Assert.notNull(beanType, "beanType must not be null");
        return requireApplicationContext().getBean(beanName, beanType);
    }

    public static boolean containsBean(String beanName) {
        Assert.hasText(beanName, "beanName must not be blank");
        return requireApplicationContext().containsBean(beanName);
    }

    private static ApplicationContext requireApplicationContext() {
        ApplicationContext context = applicationContext;
        if (context == null) {
            throw new IllegalStateException("Spring application context has not been initialized.");
        }
        return context;
    }
}
