package org.hlopes.util;

import java.util.Map;

import jakarta.ws.rs.WebApplicationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorUtil {

    public static String extractError(WebApplicationException e) {
        try {
            Object entity = e.getResponse().getEntity();

            if (entity instanceof Map) {
                Object err = ((Map<?, ?>) entity).get("error");

                if (err != null) {
                    return err.toString();
                }
            }

            if (entity != null) {
                return entity.toString();
            }

        } catch (Exception ignored) {
        }

        if (e.getMessage() != null) {
            return e.getMessage();
        }

        return "Request failed";
    }
}
