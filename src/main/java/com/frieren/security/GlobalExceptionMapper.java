package com.frieren.security;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception exception) {
        LOG.warning(exception.getClass().getSimpleName() + ": " + exception.getMessage());

        int status;
        if (exception instanceof IllegalArgumentException) {
            status = 400;
        } else if (exception instanceof SecurityException) {
            status = 403;
        } else if (exception instanceof IllegalStateException) {
            status = 422;
        } else {
            status = 500;
        }

        return Response.status(status)
                .entity(Map.of("error", exception.getMessage() != null ? exception.getMessage() : "Error interno"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
