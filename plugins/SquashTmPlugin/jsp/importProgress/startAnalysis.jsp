<%@ page contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.google.gson.JsonObject" %>
<%@ include file="/jcore/doInitPage.jspf" %>
<jsp:useBean id="squashTmAsyncHandler" scope="request" class="co.kozao.jcmsplugin.squashtm.handler.SquashTmImportHandler"><%
%><jsp:setProperty name="squashTmAsyncHandler" property="request"  value="<%= request %>"/><%
%><jsp:setProperty name="squashTmAsyncHandler" property="response" value="<%= response %>"/><%
%><jsp:setProperty name="squashTmAsyncHandler" property="*" /><%
%></jsp:useBean>
<%
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");

    JsonObject json;

    if (loggedMember == null) {
        response.setStatus(403);
        json = new JsonObject();
        json.addProperty("error", "Accès interdit.");
    } else if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.setStatus(405);
        json = new JsonObject();
        json.addProperty("error", "Méthode non autorisée.");
    } else {
        json = squashTmAsyncHandler.startAsyncAnalysis();
        response.setStatus(json.has("jobId") ? 200 : 400);
    }

    out.clear();
    out.print(json.toString());
%>