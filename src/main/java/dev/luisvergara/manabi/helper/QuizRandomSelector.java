package dev.luisvergara.manabi.helper;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

@Component 
public class QuizRandomSelector {

    private final Random random = new Random();

     public <T> T select(List<T> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException(
                    "No existen datos suficientes para generar el quiz"
            );
        }

        return items.get(
                random.nextInt(items.size())
        );
    }
    
}