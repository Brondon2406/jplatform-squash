
<%@ page contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.google.gson.JsonObject" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.service.SquashTmImportJobManager" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.model.squashimport.ImportSquashTmProgress" %>
<%@ include file="/jcore/doInitPage.jspf" %>

<%
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");

    // Vérifier que l'utilisateur est connecté à JCMS.
    if (loggedMember == null) {
        response.setStatus(403);
        out.print("{\"error\":\"Accès interdit.\"}");
        return;
    }

    // Récupérer l'identifiant du travail d'importation.
    String jobId = request.getParameter("jobId");

    if (jobId == null || jobId.trim().isEmpty()) {
        response.setStatus(400);
        out.print("{\"error\":\"Identifiant d'importation manquant.\"}");
        return;
    }

    // Consulter la progression sans relancer l'importation.
    SquashTmImportJobManager jobManager =
        SquashTmImportJobManager.getInstance();

    ImportSquashTmProgress progress =
        jobManager.getProgress(jobId);

    if (progress == null) {
        response.setStatus(404);
        out.print("{\"error\":\"Importation introuvable.\"}");
        return;
    }

    // Construire la réponse JSON.
    JsonObject json = new JsonObject();
    json.addProperty("percentage", progress.getPercentage());
    json.addProperty("stage", progress.getStage());
    json.addProperty("message", progress.getMessage());
    json.addProperty("completed", progress.isCompleted());
    json.addProperty("success", progress.isSuccess());
    json.addProperty("elapsedTime", progress.getElapsedTime());

    out.print(json.toString());
%>
