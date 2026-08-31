package org.hlopes.aiinfusion.guardrails;

import org.hlopes.aiinfusion.services.PromptInjectionDetectionService;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PromptInjectionGuard implements InputGuardrail {

    @Inject
    PromptInjectionDetectionService service;

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        double result = service.isInjection(userMessage.singleText());

        if (result > 0.7) {
            return failure("Prompt injection detected");
        }

        return success();
    }
}
