package com.frieren.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

@ApplicationScoped
public class SupabaseAdminService {
    private static final Logger LOG = Logger.getLogger(SupabaseAdminService.class.getName());

    @ConfigProperty(name = "supabase.url")
    String supabaseUrl;

    @ConfigProperty(name = "supabase.service-role-key")
    String serviceRoleKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Invita a un usuario por email. Supabase le envía un correo con un link
     * para que establezca su contraseña. El user_metadata (role, organizationId) se asigna desde aquí.
     */
    public JsonNode inviteUser(String email, String name, String role, String organizationId, String redirectTo) {
        var data = mapper.createObjectNode();
        data.put("name", name);
        data.put("role", role);
        data.put("organizationId", organizationId);

        var body = mapper.createObjectNode();
        body.put("email", email);
        body.set("data", data);
        body.put("redirect_to", redirectTo);

        return sendRequest("POST", "/auth/v1/invite", body.toString());
    }

    /**
     * Lista todos los usuarios de Supabase Auth.
     * El filtrado por organización se hace en el servicio.
     */
    public JsonNode listUsers(int page, int perPage) {
        return sendRequest("GET", "/auth/v1/admin/users?page=" + page + "&per_page=" + perPage, null);
    }

    /**
     * Obtiene un usuario por su ID.
     */
    public JsonNode getUser(String userId) {
        return sendRequest("GET", "/auth/v1/admin/users/" + userId, null);
    }

    /**
     * Actualiza el user_metadata de un usuario (role, etc).
     */
    public JsonNode updateUser(String userId, ObjectNode userMetadata) {
        var body = mapper.createObjectNode();
        body.set("user_metadata", userMetadata);

        return sendRequest("PUT", "/auth/v1/admin/users/" + userId, body.toString());
    }

    /**
     * Elimina un usuario de Supabase Auth.
     */
    public void deleteUser(String userId) {
        sendRequest("DELETE", "/auth/v1/admin/users/" + userId, null);
    }

    /**
     * Genera una URL firmada para descargar un archivo de un bucket privado.
     * @param bucket Nombre del bucket (ej: task-evidence)
     * @param filePath Ruta del archivo dentro del bucket
     * @param expiresIn Segundos que durará la firma (ej: 3600 para 1 hora)
     * @return JsonNode con la URL firmada
     */
    public String getSignedUrl(String bucket, String filePath, int expiresIn) {
        var body = mapper.createObjectNode();
        body.put("expiresIn", expiresIn);

        // Codificar el path para manejar espacios y caracteres especiales
        String encodedPath = java.net.URLEncoder.encode(filePath, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20"); // URLEncoder usa + para espacios, pero Supabase prefiere %20

        // La API de Supabase Storage para firmas es POST /storage/v1/object/sign/{bucket}/{path}
        JsonNode response = sendRequest("POST", "/storage/v1/object/sign/" + bucket + "/" + encodedPath, body.toString());

        if (response != null && response.has("signedURL")) {
            return response.get("signedURL").asText();
        }
        return null;
    }
    private JsonNode sendRequest(String method, String path, String body) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + path))
                .header("Content-Type", "application/json")
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer " + serviceRoleKey);

        var request = switch (method) {
            case "GET" -> builder.GET().build();
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
            case "DELETE" -> builder.DELETE().build();
            default -> throw new IllegalArgumentException("Método HTTP no soportado: " + method);
        };

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                LOG.severe("Supabase Admin API error: " + response.statusCode() + " - " + response.body());
                throw new RuntimeException("Error en Supabase Admin API: " + response.statusCode() + " - " + response.body());
            }

            if (response.body() == null || response.body().isBlank()) return null;
            return mapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Llamada a Supabase Admin API interrumpida", e);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Error de conexión con Supabase Admin API", e);
        }
    }
}
