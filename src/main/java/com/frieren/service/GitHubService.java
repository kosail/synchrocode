package com.frieren.service;

import com.frieren.client.GitHubClient;
import com.frieren.dto.AddCollaboratorRequest;
import com.frieren.dto.CreateRepoRequest;
import com.frieren.dto.CreateRepoResponse;
import com.frieren.dto.GitHubCollaboratorResponse;
import com.frieren.dto.GitHubStatsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class GitHubService {
    private static final Logger LOG = Logger.getLogger(GitHubService.class);

    @Inject
    @RestClient
    GitHubClient gitHubClient;

    @ConfigProperty(name = "github.org")
    String githubOrg;

    @ConfigProperty(name = "github.token")
    Optional<String> githubToken;

    /**
     * Crea un repositorio privado en la organización de GitHub y agrega colaboradores.
     *
     * @param nombreProyecto Nombre del proyecto (se convierte a slug para el repo)
     * @param descripcion Descripción del proyecto
     * @param githubUsernames Lista de usernames de GitHub para agregar como colaboradores
     * @return URL del repositorio creado
     */
    public String crearRepoYAgregarMiembros(String nombreProyecto, String descripcion, List<String> githubUsernames) {
        String repoName = nombreProyecto.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (repoName.isBlank()) {
            repoName = "proyecto-" + System.currentTimeMillis();
        }

        String token = githubToken.map(String::trim).orElse("");
        if (token.isBlank()) {
            throw new RuntimeException("GITHUB_TOKEN no está configurado");
        }

        String authHeader = "Bearer " + token;

        try {
            CreateRepoRequest request = new CreateRepoRequest(repoName, descripcion, true);
            CreateRepoResponse response = gitHubClient.createRepo(githubOrg, authHeader, request);

            LOG.infof("Repositorio creado exitosamente: %s", response.htmlUrl());

            // Agregar colaboradores
            if (githubUsernames != null) {
                for (String username : githubUsernames) {
                    if (username == null || username.isBlank()) continue;
                    try {
                        gitHubClient.addCollaborator(githubOrg, repoName, username.trim(),
                                authHeader, new AddCollaboratorRequest("push"));
                        LOG.infof("Colaborador agregado: %s al repo %s", username, repoName);
                    } catch (Exception e) {
                        LOG.warnf("No se pudo agregar colaborador '%s' al repo '%s': %s",
                                username, repoName, e.getMessage());
                    }
                }
            }

            return response.htmlUrl();
        } catch (Exception e) {
            LOG.errorf("Error al crear repositorio de GitHub '%s': %s", repoName, e.getMessage());
            throw new RuntimeException("No se pudo crear el repositorio de GitHub", e);
        }
    }

    /**
     * Agrega un colaborador a un repositorio existente.
     *
     * @param repoUrl URL del repositorio (se extrae el nombre)
     * @param githubUsername Username de GitHub del colaborador
     */
    public void agregarColaborador(String repoUrl, String githubUsername) {
        if (repoUrl == null || githubUsername == null || githubUsername.isBlank()) return;

        // Extraer el nombre del repo de la URL: https://github.com/org/repo-name -> repo-name
        String repoName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1);
        String token = githubToken.map(String::trim).orElse("");
        if (token.isBlank()) {
            LOG.warn("No se pudo agregar colaborador porque GITHUB_TOKEN no está configurado");
            return;
        }
        String authHeader = "Bearer " + token;

        try {
            gitHubClient.addCollaborator(githubOrg, repoName, githubUsername.trim(),
                    authHeader, new AddCollaboratorRequest("push"));
            LOG.infof("Colaborador agregado: %s al repo %s", githubUsername, repoName);
        } catch (Exception e) {
            LOG.warnf("No se pudo agregar colaborador '%s' al repo '%s': %s",
                    githubUsername, repoName, e.getMessage());
        }
    }

    public GitHubStatsResponse getRepositoryStats(String repoUrl, long linkedCount) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return new GitHubStatsResponse(0, 0, linkedCount);
        }

        String repoName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1);
        String token = githubToken.map(String::trim).orElse("");
        if (token.isBlank()) {
            return new GitHubStatsResponse(0, 0, linkedCount);
        }

        String authHeader = "Bearer " + token;
        long commits = safeCountCommits(repoName, authHeader);
        long pullRequests = safeCountPullRequests(repoName, authHeader);

        return new GitHubStatsResponse(commits, pullRequests, linkedCount);
    }

    public List<GitHubCollaboratorResponse> getRepositoryCollaborators(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return List.of();
        }

        String repoName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1);
        String token = githubToken.map(String::trim).orElse("");
        if (token.isBlank()) {
            return List.of();
        }

        try {
            String authHeader = "Bearer " + token;
            return gitHubClient.listCollaborators(githubOrg, repoName, authHeader, "all", 100)
                    .stream()
                    .map(this::toCollaboratorResponse)
                    .toList();
        } catch (Exception e) {
            LOG.warnf("No se pudieron leer colaboradores del repo '%s': %s", repoName, e.getMessage());
            return List.of();
        }
    }

    private GitHubCollaboratorResponse toCollaboratorResponse(JsonNode collaborator) {
        JsonNode permissions = collaborator.get("permissions");
        String permission = "pull";
        if (permissions != null) {
            if (permissions.path("admin").asBoolean(false)) {
                permission = "admin";
            } else if (permissions.path("push").asBoolean(false)) {
                permission = "push";
            }
        }

        return new GitHubCollaboratorResponse(
                collaborator.path("login").asText(""),
                collaborator.path("avatar_url").asText(null),
                collaborator.path("html_url").asText(null),
                permission
        );
    }

    private long safeCountCommits(String repoName, String authHeader) {
        try {
            return gitHubClient.listCommits(githubOrg, repoName, authHeader, 100).size();
        } catch (Exception e) {
            LOG.warnf("No se pudieron leer commits del repo '%s': %s", repoName, e.getMessage());
            return 0;
        }
    }

    private long safeCountPullRequests(String repoName, String authHeader) {
        try {
            return gitHubClient.listPullRequests(githubOrg, repoName, authHeader, "all", 100).size();
        } catch (Exception e) {
            LOG.warnf("No se pudieron leer pull requests del repo '%s': %s", repoName, e.getMessage());
            return 0;
        }
    }
}
