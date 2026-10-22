package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CraftEmailPolicyTest {
    @Test public void acceptsOneAddressAndBoundedDraft() {
        assertNull(CraftEmailPolicy.validationError(" operator@example.test ", "Pump alert", "Running"));
    }

    @Test public void rejectsMissingMultipleOrMalformedRecipients() {
        assertEquals("Укажи один корректный адрес получателя",
                CraftEmailPolicy.validationError("", "Subject", "Body"));
        assertEquals("Укажи один корректный адрес получателя",
                CraftEmailPolicy.validationError("a@example.test,b@example.test", "Subject", "Body"));
        assertEquals("Укажи один корректный адрес получателя",
                CraftEmailPolicy.validationError("ops@invalid", "Subject", "Body"));
    }

    @Test public void rejectsHeaderInjectionAndOversizedContent() {
        assertEquals("Тема письма должна быть до 120 символов в одной строке",
                CraftEmailPolicy.validationError("a@example.test", "subject\nBcc: leak@example.test", "Body"));
        assertEquals("Текст письма не должен превышать 2000 символов",
                CraftEmailPolicy.validationError("a@example.test", "Subject", new String(new char[2001]).replace('\0', 'x')));
    }
}
