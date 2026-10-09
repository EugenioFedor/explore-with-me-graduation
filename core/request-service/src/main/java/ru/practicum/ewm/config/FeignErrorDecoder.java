package ru.practicum.ewm.config;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

@Configuration
public class FeignErrorDecoder {
    @Bean
    public ErrorDecoder errorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            if (response.status() == 404)
                return new NotFoundException("Required object was not found in dependent service");
            if (response.status() == 409) return new ConflictException("Dependent service rejected the operation");
            return defaultDecoder.decode(methodKey, response);
        };
    }
}
