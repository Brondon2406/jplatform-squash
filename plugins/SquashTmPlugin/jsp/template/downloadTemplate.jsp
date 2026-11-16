<%@ page contentType="text/html; charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.io.File" %><%@ page import="java.io.FileInputStream" %>
<%@ page import="java.io.InputStream" %><%@ page import="java.io.OutputStream" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.service.mapping.SquashTmTemplateMappingManager" %>
<%@ include file="/jcore/doInitPage.jspf" %><%

    if (loggedMember == null) {
        response.sendError(403);
        return;
    }

    File customTemplate = SquashTmTemplateMappingManager.getInstance().getCustomTemplateFile();

    if (customTemplate == null) {
        response.sendRedirect(request.getContextPath() + "/plugins/SquashTmPlugin/docs/template/Modele_Import_SquashTM.xlsx");
        return;
    }

    out.clearBuffer();
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=\"Modele_Import_SquashTM.xlsx\"");
    response.setHeader("Cache-Control", "no-store");
    response.setContentLength((int) customTemplate.length());

    try (InputStream templateInput = new FileInputStream(customTemplate); OutputStream templateOutput = response.getOutputStream()) {
        byte[] buffer = new byte[8192];
        int length;
        while ((length = templateInput.read(buffer)) != -1) {
            templateOutput.write(buffer, 0, length);
        }
        templateOutput.flush();
    }
%>
