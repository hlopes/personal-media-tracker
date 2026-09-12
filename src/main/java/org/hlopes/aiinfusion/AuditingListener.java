package org.hlopes.aiinfusion;

import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class AuditingListener {
    public void toolExecuted(@Observes ToolExecutedEvent toolExecutedEvent) {
        // Invoked with a tool response from an LLM.
        // It is important to note that this can be invoked multiple times when tools exist.
        Log.info("### ToolExecutedEvent - Request Arguments: "
                + toolExecutedEvent.request().arguments());
        Log.info("### ToolExecutedEvent - Result Text: " + toolExecutedEvent.resultText());
    }
}
