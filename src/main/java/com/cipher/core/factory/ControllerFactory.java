package com.cipher.core.factory;

import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ControllerFactory implements Callback<Class<?>, Object> {

    private final ApplicationContext applicationContext;

    @Override
    public Object call(Class<?> type) {
        return applicationContext.getBean(type);
    }
}
