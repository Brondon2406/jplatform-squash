<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.io.IOException" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.SquashTmManager" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.service.SquashTmExportService" %>
<%@ include file="/jcore/doInitPage.jspf" %><%

    // Accès réservé aux membres connectés à JCMS.
    if (loggedMember == null) {
        sendForbidden(request, response);
        return;
    }

    // Il faut être connecté à Squash TM pour interroger l'API.
    SquashTmManager exportManager = SquashTmManager.getInstance();

    if (!exportManager.isPluginActive(loggedMember) || !exportManager.isConnect()) {
        response.setStatus(403);
        response.setContentType("text/plain; charset=UTF-8");
        out.print("Connexion à Squash TM requise.");
        return;
    }

    String exportProjectName = request.getParameter("projectName");

    if (exportProjectName == null || exportProjectName.trim().isEmpty()) {
        response.setStatus(400);
        response.setContentType("text/plain; charset=UTF-8");
        out.print("Le nom du projet est obligatoire.");
        return;
    }

    exportProjectName = exportProjectName.trim();

    byte[] exportContent;
    String exportFileName;

    try {
        exportContent = new SquashTmExportService().exportProject(exportProjectName);
        exportFileName = SquashTmExportService.buildFileName(exportProjectName);
    } catch (IOException | RuntimeException exportError) {
        response.setStatus(500);
        response.setContentType("text/plain; charset=UTF-8");
        out.print(exportError.getMessage() != null ? exportError.getMessage() : "Erreur technique pendant l'export.");
        return;
    }

    String exportAsciiName = exportFileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
    String exportEncodedName = java.net.URLEncoder.encode(exportFileName, "UTF-8").replace("+", "%20");

    // Vider le tampon de la JSP pour ne renvoyer que le fichier.
    out.clear();
    response.reset();
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + exportAsciiName + "\"; filename*=UTF-8''" + exportEncodedName);
    response.setHeader("Cache-Control", "no-store");
    response.setContentLength(exportContent.length);

    java.io.OutputStream exportStream = response.getOutputStream();
    exportStream.write(exportContent);
    exportStream.flush();
%>