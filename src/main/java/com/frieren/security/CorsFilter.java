package com.frieren.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            Response response = Response.ok()
                    .header("Access-Control-Allow-Origin", "http://localhost:4321")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "accept, authorization, content-type, x-requested-with")
                    .header("Access-Control-Allow-Credentials", "true")
                    .build();
            request.abortWith(response);
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().add("Access-Control-Allow-Origin", "http://localhost:4321");
        response.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        response.getHeaders().add("Access-Control-Allow-Headers", "accept, authorization, content-type, x-requested-with");
        response.getHeaders().add("Access-Control-Allow-Credentials", "true");
    }
}
