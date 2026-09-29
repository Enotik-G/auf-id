package com.example.planner.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Учит Hibernate хранить {@link EmailAddress} в колонке-строке.
 * autoApply = true: применяется ко всем полям типа EmailAddress во всех сущностях.
 */
@Converter(autoApply = true)
public class EmailAddressConverter implements AttributeConverter<EmailAddress, String> {

    @Override
    public String convertToDatabaseColumn(EmailAddress email) {
        return email == null ? null : email.value();
    }

    @Override
    public EmailAddress convertToEntityAttribute(String value) {
        return value == null ? null : new EmailAddress(value);
    }
}
